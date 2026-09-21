package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.entity.enums.Decision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    /** 프로파일을 안 주면 기존 동작(IDENTIFIED 임계값)을 따른다. 호환용. */
    public Decision resolve(double score) {
        return resolve(score, MatchProfile.IDENTIFIED);
    }
}
