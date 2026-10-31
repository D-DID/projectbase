package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.entity.enums.Decision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * FR-011 — 종합 점수를 MATCH / PARTIAL / NO_MATCH 로 떨어뜨린다.
 *
 * 9/20 — 임계값을 프로파일별로 나눴다.
 * 품번이 있는 공산품은 모델명이 일치하면 점수가 확실하게 높게 나오므로 기존 0.85 를 유지한다.
 * 품번이 없는 유아·잡화는 판매글 상품명과 공표문 상품명이 같은 표기일 수 없어서 같은 잣대를
 * 들이대면 전부 PARTIAL 아래로 떨어진다. 그래서 조금 낮춘 값을 따로 둔다.
 *
 * 낮춘 만큼 오탐이 늘어날 수 있다. 이 값이 라벨링 데이터셋으로 맞춰야 할 1순위 대상이고,
 * application.properties 의 matching.unidentified.threshold.* 로 재컴파일 없이 조정한다.
 */
@Component
@RequiredArgsConstructor
public class DecisionResolver {

    private final MatchingProperties properties;

    /** 프로파일에 맞는 임계값으로 판정한다. */
    public Decision resolve(double score, MatchProfile profile) {
        MatchingProperties.Threshold threshold = properties.thresholdFor(profile);
        if (score >= threshold.getMatch()) {
            return Decision.MATCH;
        }
        if (score >= threshold.getPartial()) {
            return Decision.PARTIAL;
        }
        return Decision.NO_MATCH;
    }

    /**
     * 9/27 → 10/31 — 4단계 표시 기준. 일치는 "확정 근거"가 있을 때만 낸다.
     *
     * ── 10/31 팀장 결정: 2단계 판정 (KC 인증번호 + 제품명) ──
     *   일치 = KC 인증번호(또는 인증 DB 모델명 = 공표문 모델명) 완전일치 <b>그리고</b> 제품명 유사도 rule.nameMatch(50%) 이상
     *        · 판매자가 적은 품번(모델명) 완전일치 — 품번은 그 자체가 제품 단위 식별자라 이름을 따로 요구하지 않는다
     *          (인증 모델명은 판매자 품번이 아니라 KC 번호로 인증 DB 를 찾아 나온 값이라 KC 번호와 같은 급으로 본다)
     *   의심 = KC 인증번호는 같은데 제품명이 50% 미만이거나 비어 있음
     *        · 품번은 같은데 KC 인증번호가 다름
     *        · 제품명이 거의 같은데(rule.nameSuspectOtherCert, 90% 이상) KC 인증번호가 다름 — 인증 갱신·판매자 오기 가능
     *        · 그 밖에 종합 점수가 partial 임계값 이상(예전과 같음)
     *   불일치 = 그 외
     *
     * 근거(리콜 4,092건 자기 대조, 10/31):
     *   - KC 번호만으로 일치를 내지 않는 이유: KC 번호 156개가 서로 다른 공표문 2건 이상에 나온다. 같은 번호로 다른 제품이
     *     따로 공표된다(CB062A035-8001 — 에메랄드슬라임·딸기요거트슬라임·초코칩슬라임…, 이름 50% 미만인 쌍 734개).
     *     판매자가 다른 제품 번호를 옮겨 적는 경우도 있다. 그래서 이름이 다르면 '의심' — 사용자가 원문 링크로 확인한다.
     *   - 품번은 그대로 일치로 두는 이유: 코드형 품번 2,564개 중 공표문 2건 이상에 나오는 85개는 대부분 같은 제품의 재공표다.
     *     공표문 제품명이 '중의류(아섬)' 같은 분류명이라 이름으로 막으면 진짜 리콜(리틀뎁 바람막이 VDECX01)도 의심으로 떨어진다.
     *   - '이름 50% + KC 다름'을 의심으로 올리지 않는 이유: KC 번호가 다르면 다른 인증 모델일 가능성이 크고, 50% 로 두면
     *     정상 제품 1개당 이름이 비슷한 다른 공표문 6.2건이 의심이 된다. 90% 이상(0.06건 — 지금 규칙 0.07건과 비슷)만 올린다.
     *
     * 사진 찾기(2차)로 얻은 값(image*)은 확정 근거가 아니다 — 최대 '의심'(10/10).
     * matching.threshold.match / matching.unidentified.threshold.match 는 더 이상 쓰지 않는다(바인딩 호환용으로 남김).
     */
    public Decision resolve(double score, MatchProfile profile, List<FieldComparison> comparisons) {
        Evidence e = Evidence.of(comparisons);
        MatchingProperties.Rule rule = properties.getRule();
        if (e.exactModel()) {
            return e.certDiffers() ? Decision.PARTIAL : Decision.MATCH;
        }
        if (e.exactCert()) {
            return e.nameScore() >= rule.getNameMatch() ? Decision.MATCH : Decision.PARTIAL;
        }
        if (e.certDiffers() && e.nameScore() >= rule.getNameSuspectOtherCert()) {
            return Decision.PARTIAL;
        }
        return score >= properties.thresholdFor(profile).getPartial() ? Decision.PARTIAL : Decision.NO_MATCH;
    }

    /**
     * 10/31 — 판정 이유 한 줄(판정 근거 reason 뒤에 붙인다). 2단계 규칙 때문에 '의심'이 된 경우만. 아니면 null.
     */
    public String note(List<FieldComparison> comparisons) {
        Evidence e = Evidence.of(comparisons);
        MatchingProperties.Rule rule = properties.getRule();
        if (e.exactModel()) {
            return e.certDiffers() ? "품번은 같지만 KC 인증번호가 달라 확정하지 않음 — 원문에서 인증번호를 확인하세요" : null;
        }
        if (e.exactCert() && e.nameScore() < rule.getNameMatch()) {
            return e.nameScore() < 0
                    ? "KC 인증번호는 같지만 제품명이 없어 확정하지 않음 — 원문에서 제품을 확인하세요"
                    : String.format("KC 인증번호는 같지만 제품명이 달라(%.0f%%) 확정하지 않음 — 같은 번호로 여러 제품이 공표된 경우가 있습니다",
                    e.nameScore() * 100);
        }
        if (!e.exactCert() && e.certDiffers() && e.nameScore() >= rule.getNameSuspectOtherCert()) {
            return String.format("제품명은 거의 같지만(%.0f%%) KC 인증번호가 다름 — 인증 갱신·판매자 오기일 수 있어 의심",
                    e.nameScore() * 100);
        }
        return null;
    }

    /** 부동소수 오차를 감안한 100% */
    static final double EXACT = 0.9999;

    /**
     * 완전일치하면 확정 근거가 되는 항목(식별자). 10/31 — KC 인증번호는 제품명이 함께 맞아야 확정(resolve 참조).
     * 10/3 — certModelName(인증 DB 모델명 = 공표문 모델명) 추가. MatchingService 가 완전일치일 때만 가중치를 준다.
     */
    static final Set<String> IDENTIFIER_FIELDS = Set.of("certNum", "modelName", "certModelName");

    /**
     * 판정에 쓰는 요약(사진 찾기 값·가중치 0 행은 뺀다).
     * @param exactModel  판매자 품번(모델명) 완전일치
     * @param exactCert   KC 인증번호 완전일치 또는 인증 DB 모델명 = 공표문 모델명
     * @param nameScore   제품명 유사도(여러 행이면 최댓값). 제품명 비교가 없으면 -1
     * @param certDiffers KC 인증번호를 비교했는데 다르다
     */
    record Evidence(boolean exactModel, boolean exactCert, double nameScore, boolean certDiffers) {

        static Evidence of(List<FieldComparison> comparisons) {
            boolean model = false;
            boolean cert = false;
            double name = -1;
            boolean certDiffers = false;
            if (comparisons != null) {
                for (FieldComparison c : comparisons) {
                    // 10/10 — 사진 찾기(2차)로 얻은 값(imageLabel·imageCertNum·imageModelName)은 확정 근거가 아니다.
                    if (c.weight() <= 0 || isImageField(c.field())) {
                        continue;
                    }
                    boolean exact = c.score() >= EXACT;
                    switch (c.field()) {
                        case "modelName" -> model |= exact;
                        // 인증 모델명 = KC 번호로 인증 DB 에서 찾은 모델명. 번호에서 나온 값이라 KC 번호와 같이 다룬다
                        case "certModelName" -> cert |= exact;
                        case "certNum" -> {
                            cert |= exact;
                            certDiffers |= !exact;
                        }
                        case "productName" -> name = Math.max(name, c.score());
                        default -> { }
                    }
                }
            }
            // 인증번호가 여러 개면 하나라도 같으면 같은 것으로 본다(다른 번호 행은 무시)
            return new Evidence(model, cert, name, certDiffers && !cert);
        }
    }

    /** 식별자 완전일치가 하나라도 있는가(사진 찾기 값 제외) — 10/31 이후 KC 번호만으로는 일치가 아니다(resolve 참조) */
    static boolean hasExactEvidence(double score, List<FieldComparison> comparisons) {
        Evidence e = Evidence.of(comparisons);
        return e.exactModel() || e.exactCert();
    }

    /** 사진 찾기(2차)에서 온 항목인가 — 이름이 "image" 로 시작한다 */
    static boolean isImageField(String field) {
        return field != null && field.startsWith("image");
    }

    /** 프로파일을 안 주면 기존 동작(IDENTIFIED 임계값)을 따른다. 호환용. */
    public Decision resolve(double score) {
        return resolve(score, MatchProfile.IDENTIFIED);
    }
}
