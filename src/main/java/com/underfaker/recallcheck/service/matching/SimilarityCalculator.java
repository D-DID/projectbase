package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 항목별 점수 산정 (FR-010).
 *
 * ── 9/20 수정 두 가지 ──
 *
 * (1) 부분 포함 보너스에 길이 가드
 *     구버전은 a.contains(b) 이기만 하면 무조건 0.75 이상을 줬다. 실데이터의
 *     recall_model_name 에 상품명 문장이 통째로 들어오는 탓에(예: 10022552 의
 *     "(온라인)투데이리빙 16개 세트 동물 캐릭터 신발 파츠 …") 짧은 입력 토큰 하나가 우연히
 *     포함되기만 해도 0.75 가 찍혔다. "슬라임" 세 글자가 무관한 리콜의 긴 상품명에 들어 있으면
 *     그 행이 PARTIAL(0.60)을 넘겨 버린다. 4자 미만은 보너스를 빼고 편집거리로 넘긴다.
 *
 * (2) 가중치를 프로파일별로 고른다
 *     품번이 있는 공산품과 품번이 없는 유아·잡화는 믿을 수 있는 항목이 다르다.
 *     근거는 MatchProfile 주석 참조.
 */
@Component
@RequiredArgsConstructor
public class SimilarityCalculator {

    /**
     * 부분 포함 보너스를 줄 최소 길이.
     * 이보다 짧은 조각이 긴 문자열에 들어 있는 것은 우연일 가능성이 높다.
     */
    private static final int MIN_CONTAINS_LENGTH = 4;

    /** 부분 포함일 때의 기본 점수. 나머지는 길이 비율로 채운다. */
    private static final double CONTAINS_BASE = 0.55;
    private static final double CONTAINS_RATIO_WEIGHT = 0.40;

    /**
     * 식별자(인증번호·품번)에서 부분 포함을 인정할 최소 길이.
     * 일반 문자열용 4자보다 하나 길게 둔다 — "1250" 같은 숫자 네 자리는 품번 여러 개에 우연히
     * 들어 있을 수 있다. 잠정치이며 실측 근거는 없다.
     */
    private static final int MIN_IDENTIFIER_CONTAINS_LENGTH = 5;

    private final MatchingProperties properties;

    /**
     * 두 문자열의 유사도 (0.0 ~ 1.0). 정규화된 편집거리(Levenshtein)를 쓴다.
     *
     * 입력은 이미 정규화된 문자열이어야 한다. TextNormalizer 가 null 을 돌려줄 수 있으므로
     * null 을 0.0 으로 받는다 — 다만 값이 없는 항목은 애초에 MatchingService 가 비교 목록에
     * 넣지 않으므로 여기까지 null 이 오는 건 방어적 처리다.
     */
    public double similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }

        int shorter = Math.min(a.length(), b.length());
        int longer = Math.max(a.length(), b.length());

        if ((a.contains(b) || b.contains(a)) && shorter >= MIN_CONTAINS_LENGTH) {
            return CONTAINS_BASE + CONTAINS_RATIO_WEIGHT * ((double) shorter / longer);
        }

        int distance = levenshtein(a, b);
        return Math.max(0.0, 1.0 - ((double) distance / longer));
    }

    /**
     * 식별자(인증번호·품번) 전용 유사도 — 완전일치 또는 포함관계일 때만 점수를 준다.
     *
     * ── 9/27 추가 ──
     * 인증번호·품번은 한 글자만 달라도 다른 제품이다. 그런데 similarity() 는 편집거리로
     * 부분점수를 주기 때문에, 형식이 같은 번호끼리는 무관해도 절반 넘게 나온다.
     * 실측(검증 577): 입력 인증번호와 공표문 인증번호가 서로 다른데 "인증번호 57%" 가 찍혔고,
     * 제품명 100% 와 합쳐져 상품명기준 종합 0.806 → MATCH(임계 0.80) 로 올라갔다.
     * 같은 입력이 리콜 13건 이상에 MATCH 로 붙었다.
     *
     * 규칙:
     *   같으면 1.0
     *   한쪽이 다른 쪽을 포함하고 짧은 쪽이 5자 이상이면 similarity() 와 같은 포함 점수
     *     (옵션 접미사 "SG1250M" ↔ "SG1250MW", 앞부분만 적은 "YU101889" ↔ "YU101889-23001" 대응.
     *      하이픈·공백 차이는 이 메서드에 오기 전에 정규화에서 이미 지워진다)
     *   그 밖은 0.0 — 편집거리 부분점수를 주지 않는다.
     *
     * 0 점이어도 항목은 비교 목록에 남는다(분모에 들어간다). "번호가 다르다"는 것 자체가
     * 반대 증거이기 때문이다.
     */
    public double identifierSimilarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        int shorter = Math.min(a.length(), b.length());
        int longer = Math.max(a.length(), b.length());
        if ((a.contains(b) || b.contains(a)) && shorter >= MIN_IDENTIFIER_CONTAINS_LENGTH) {
            return CONTAINS_BASE + CONTAINS_RATIO_WEIGHT * ((double) shorter / longer);
        }
        return 0.0;
    }

    /**
     * 항목별 비교 결과에 가중치를 적용한 종합 점수.
     * 양쪽 값이 모두 있는 항목만 분모에 넣는다 — 없는 항목 때문에 점수가 깎이면 안 된다.
     */
    public double weightedScore(List<FieldComparison> comparisons) {
        double weightedSum = 0.0;
        double weightTotal = 0.0;
        for (FieldComparison c : comparisons) {
            weightedSum += c.score() * c.weight();
            weightTotal += c.weight();
        }
        return weightTotal == 0.0 ? 0.0 : weightedSum / weightTotal;
    }

    /** 프로파일에 맞는 항목 가중치 */
    public double weightOf(String field, MatchProfile profile) {
        return properties.weightFor(profile).of(field);
    }

    /** 프로파일을 안 주면 기존 동작(IDENTIFIED)을 따른다. 호환용. */
    public double weightOf(String field) {
        return weightOf(field, MatchProfile.IDENTIFIED);
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
