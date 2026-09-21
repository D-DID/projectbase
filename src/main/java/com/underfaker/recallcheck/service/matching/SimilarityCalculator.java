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
