package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 항목 통합·보강 (FR-007).
 * 한 검증 요청에서 URL·이미지·수동 입력 등 여러 소스의 추출 결과가 나올 수 있으므로
 * 항목별로 신뢰도가 높은 값을 선택해 하나로 합친다.
 */
@Component
public class IdentityMerger {

    /**
     * @param sources 소스별 추출 결과 (confidence 포함)
     * @return 항목별로 병합된 단일 식별 정보
     */
    public ExtractedProduct merge(List<ExtractedProduct> sources) {
        // TODO 항목별 우선순위 규칙: 수동 입력 > 높은 confidence > 먼저 추출된 값
        throw new UnsupportedOperationException("TODO: IdentityMerger.merge");
    }
}
