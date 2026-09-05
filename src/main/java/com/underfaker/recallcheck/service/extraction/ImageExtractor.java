package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.client.OcrClient;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * FR-006 — OCR 호출·결과 정리.
 * OCR 엔진 자체는 OcrClient 뒤에 숨겨 두어 엔진 교체 시 이 클래스가 바뀌지 않게 한다.
 */
@Component
@RequiredArgsConstructor
public class ImageExtractor {

    private final OcrClient ocrClient;

    public ExtractedProduct extract(String imagePath) {
        // TODO ocrClient.recognize(imagePath) → raw_text 에서 항목 후보 추출
        throw new UnsupportedOperationException("TODO: ImageExtractor.extract");
    }
}
