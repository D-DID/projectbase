package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.common.ProductPageParser;
import com.underfaker.recallcheck.dto.internal.UrlExtraction;

/**
 * 10/31 — URL 미리 읽기(POST /api/verifications/url/preview). 검증을 저장하지 않고, 서버가 상품 페이지에서 뽑은 값만
 * 돌려준다. 웹 화면 ② 방식이 칸을 채우는 데 쓴다(사용자가 고친 뒤 [리콜 여부 확인하기] → /manual).
 *
 * @param kcStatus 고시 표의 KC 칸 상태(DISCLOSED · REFERENCED · NONE). 칸을 못 찾았으면 null
 * @param kcText   KC 칸 원문
 */
public record UrlPreviewResponse(
        String url,
        String site,
        String productName,
        String brandName,
        String modelName,
        String makerName,
        String barcodeNum,
        String certNum,
        String thumbnailUrl,
        String kcStatus,
        String kcText
) {

    public static UrlPreviewResponse of(String url, UrlExtraction ex) {
        ProductPageParser.Parsed p = ex.parsed() == null ? ProductPageParser.Parsed.EMPTY : ex.parsed();
        return new UrlPreviewResponse(url, ex.site(), p.productName(), p.brandName(), p.modelName(), p.makerName(),
                p.barcodeNum(), p.certNum(), p.thumbnailUrl(), p.kcStatus(), p.kcText());
    }
}
