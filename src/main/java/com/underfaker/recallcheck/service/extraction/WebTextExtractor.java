package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import org.springframework.stereotype.Component;

/**
 * FR-005 — URL 페이지 텍스트 파싱.
 * 판매글 본문에서 제품명·모델명·제조사·바코드·인증번호 후보를 뽑는다.
 */
@Component
public class WebTextExtractor {

    /**
     * @param url 사용자가 입력한 상품 페이지 URL
     * @return 추출 결과. 추출 실패 시 빈 값이 채워진 결과를 반환하고 예외를 던지지 않는다
     *         (흐름도 02 분기 — 실패해도 종료하지 않고 사용자 직접 입력으로 이어짐).
     */
    public ExtractedProduct extract(String url) {
        // TODO Jsoup 등으로 페이지 로드 → 본문 텍스트 정제 → 항목 후보 추출
        throw new UnsupportedOperationException("TODO: WebTextExtractor.extract");
    }
}
