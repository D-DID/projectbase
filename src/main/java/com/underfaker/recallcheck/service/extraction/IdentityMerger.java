package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 항목 통합·보강 (FR-007).
 * 한 검증 요청에서 URL·이미지·수동 입력 등 여러 소스의 추출 결과가 나올 수 있으므로
 * 항목별로 신뢰도가 높은 값을 골라 하나로 합친다.
 *
 * 우선순위: confidence 가 높은 소스의 값이 이긴다. 값이 비어 있으면 다음 소스가 채운다.
 */
@Component
public class IdentityMerger {

    public ExtractedProduct merge(List<ExtractedProduct> sources) {
        if (sources == null || sources.isEmpty()) {
            return new ExtractedProduct(null, null, null, null, null, null, null, null, 0.0);
        }
        List<ExtractedProduct> ordered = sources.stream()
                .sorted((a, b) -> Double.compare(conf(b), conf(a)))
                .toList();

        String productName = null, brandName = null, modelName = null, makerName = null;
        String barcodeNum = null, certNum = null, thumbnailUrl = null;
        StringBuilder rawText = new StringBuilder();
        double confidence = 0.0;

        for (ExtractedProduct s : ordered) {
            productName = pick(productName, s.productName());
            brandName = pick(brandName, s.brandName());
            modelName = pick(modelName, s.modelName());
            makerName = pick(makerName, s.makerName());
            barcodeNum = pick(barcodeNum, s.barcodeNum());
            certNum = pick(certNum, s.certNum());
            // 9/17 추가 — 썸네일도 같은 규칙(신뢰도 높은 소스 우선, 비면 다음 소스가 채움)으로 합친다
            thumbnailUrl = pick(thumbnailUrl, s.thumbnailUrl());
            if (s.rawText() != null && !s.rawText().isBlank()) {
                rawText.append(s.rawText()).append('\n');
            }
            confidence = Math.max(confidence, conf(s));
        }

        return new ExtractedProduct(productName, brandName, modelName, makerName,
                barcodeNum, certNum, thumbnailUrl, rawText.toString().trim(), confidence);
    }

    private String pick(String current, String candidate) {
        if (current != null && !current.isBlank()) {
            return current;
        }
        return (candidate == null || candidate.isBlank()) ? current : candidate;
    }

    private double conf(ExtractedProduct p) {
        return p.confidence() == null ? 0.0 : p.confidence();
    }
}
