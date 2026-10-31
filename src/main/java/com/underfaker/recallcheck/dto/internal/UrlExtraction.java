package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.common.ProductPageParser;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;

/**
 * 10/31 — FR-005 URL 자동 추출 결과(WebTextExtractor#read).
 *
 * @param status  OK · EMPTY(페이지는 받았는데 식별 정보가 없음) · BLOCKED_SITE(서버 접속을 막는 쇼핑몰)
 *                · FETCH_FAILED(못 받음 — 오류 응답·시간 초과·HTML 아님) · NOT_ALLOWED(내부망 등 열면 안 되는 주소)
 * @param parsed  뽑은 값(OK 가 아니면 비어 있을 수 있다)
 * @param site    호스트 이름(안내 문구용)
 */
public record UrlExtraction(Status status, ProductPageParser.Parsed parsed, String site) {

    public enum Status { OK, EMPTY, BLOCKED_SITE, FETCH_FAILED, NOT_ALLOWED }

    public boolean ok() {
        return status == Status.OK;
    }

    /** 기존 검증 흐름(직접입력과 같은 규칙)에 태울 요청 */
    public ManualInputRequest toRequest() {
        ProductPageParser.Parsed p = parsed == null ? ProductPageParser.Parsed.EMPTY : parsed;
        return new ManualInputRequest(p.productName(), p.brandName(), p.modelName(), p.makerName(),
                p.barcodeNum(), p.certNum(), p.thumbnailUrl(), p.kcStatus(), p.kcText());
    }
}
