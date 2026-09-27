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
     * 9/27 — 4단계 표시 기준 변경: 일치는 "100% 확정 근거"가 있을 때만.
     *
     * 팀장 결정(9/27): 결과는 [일치 / 의심 / 항목누락 / 불일치] 로 보여 주고, 일치는 100% 일 때만 쓴다.
     * 텍스트 유사도 0.85 같은 점수로는 "같은 제품"을 확정할 수 없다 — 판매글 상품명은 공표문과
     * 표기가 늘 조금씩 다르기 때문이다. 그래서 MATCH 는 아래 둘 중 하나일 때만 낸다.
     *   (1) 식별자(인증번호·모델명)가 정규화 후 완전히 같다 — 그 항목 점수 1.0
     *   (2) 비교한 텍스트 항목 전부가 100% (이미지판독 행은 빼고 본다 — 사진은 확정 근거가 아니다)
     * 나머지는 점수가 partial 임계값 이상이면 PARTIAL(의심), 아니면 NO_MATCH.
     *
     * matching.threshold.match / matching.unidentified.threshold.match 는 더 이상 쓰지 않는다
     * (프로퍼티 바인딩이 깨지지 않게 필드는 남겨 둔다). partial 임계값은 그대로 쓴다.
     * "항목누락"은 판정이 아니라 표시 상태라 여기서 다루지 않는다(ResultState 참조).
     */
    public Decision resolve(double score, MatchProfile profile, List<FieldComparison> comparisons) {
        if (hasExactEvidence(score, comparisons)) {
            return Decision.MATCH;
        }
        return score >= properties.thresholdFor(profile).getPartial() ? Decision.PARTIAL : Decision.NO_MATCH;
    }

    /** 부동소수 오차를 감안한 100% */
    static final double EXACT = 0.9999;

    /** 완전일치 하나만으로 일치를 확정할 수 있는 항목 */
    static final Set<String> IDENTIFIER_FIELDS = Set.of("certNum", "modelName");

    static boolean hasExactEvidence(double score, List<FieldComparison> comparisons) {
        if (comparisons == null || comparisons.isEmpty()) {
            return false;
        }
        boolean anyText = false;
        boolean allTextExact = true;
        for (FieldComparison c : comparisons) {
            if (c.weight() <= 0 || "imageLabel".equals(c.field())) {
                continue;
            }
            if (IDENTIFIER_FIELDS.contains(c.field()) && c.score() >= EXACT) {
                return true;
            }
            anyText = true;
            allTextExact &= c.score() >= EXACT;
        }
        return anyText && allTextExact;
    }

    /** 프로파일을 안 주면 기존 동작(IDENTIFIED 임계값)을 따른다. 호환용. */
    public Decision resolve(double score) {
        return resolve(score, MatchProfile.IDENTIFIED);
    }
}
