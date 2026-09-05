package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.entity.enums.Decision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 4단계 판정 — 임계값 적용 (FR-011).
 *
 * 임계값은 MatchingProperties 에서 주입받는다. 코드에 상수로 박지 않는다.
 * 아직 라벨링 데이터로 튜닝하지 않은 잠정값이다.
 */
@Component
@RequiredArgsConstructor
public class DecisionResolver {

    private final MatchingProperties properties;

    /**
     * @param score 가중치 적용 종합 점수
     * @return MATCH / PARTIAL / NO_MATCH
     *         (정보 부족으로 판별 자체가 불가한 경우는 VerificationService 에서 UNKNOWN 으로 처리)
     */
    public Decision resolve(double score) {
        MatchingProperties.Threshold t = properties.getThreshold();
        if (score >= t.getMatch()) {
            return Decision.MATCH;
        }
        if (score >= t.getPartial()) {
            return Decision.PARTIAL;
        }
        return Decision.NO_MATCH;
    }
}
