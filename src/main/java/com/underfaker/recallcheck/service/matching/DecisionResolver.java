package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.entity.enums.Decision;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 4단계 판정 — 임계값 적용 (FR-011).
 *
 * 임계값은 MatchingProperties 에서 주입받는다. 코드에 상수로 박지 말 것.
 * 확정된 값이 아직 없으므로 라벨링 데이터셋으로 튜닝한 뒤 properties 만 바꾼다.
 */
@Component
@RequiredArgsConstructor
public class DecisionResolver {

    private final MatchingProperties properties;

    /**
     * @param score 가중치 적용 종합 점수
     * @return MATCH / PARTIAL / NO_MATCH
     *         (정보 부족으로 판별 자체가 불가한 경우는 FinalResult.UNKNOWN 으로
     *          VerificationService 단계에서 처리한다)
     */
    public Decision resolve(double score) {
        // TODO properties.getThreshold() 기준으로 분기
        throw new UnsupportedOperationException("TODO: DecisionResolver.resolve");
    }
}
