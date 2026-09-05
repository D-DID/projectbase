package com.underfaker.recallcheck.dto.internal;

/**
 * 추출 통합 결과 — 제품명·모델명·제조사 등.
 * 서비스 계층 간 전달용이며 컨트롤러로 나가지 않는다.
 *
 * @param confidence 0.0 ~ 1.0. 수동 입력은 1.0 으로 둔다.
 */
public record ExtractedProduct(

        String productName,
        String brandName,
        String modelName,
        String makerName,
        String barcodeNum,
        String certNum,
        String rawText,
        Double confidence
) {
    /** 매칭을 시도할 만한 식별 정보가 하나도 없는 상태인가 (→ FinalResult.UNKNOWN) */
    public boolean isEmpty() {
        return isBlank(productName) && isBlank(modelName)
                && isBlank(makerName) && isBlank(barcodeNum) && isBlank(certNum);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
