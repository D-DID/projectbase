package com.underfaker.recallcheck.dto.internal;

/**
 * 추출 통합 결과 — 제품명·모델명·제조사 등.
 * 서비스 계층 간 전달용이며 컨트롤러로 나가지 않는다.
 *
 * 9/17 추가 — thumbnailUrl. 크롬 확장이 쿠팡 주문내역에서 가져온 항목 썸네일 URL 이
 * 검증 파이프라인 끝까지 실려 가도록 필드를 추가했다. 1단계에서는 운반·보관만 하고
 * 판정에는 쓰지 않는다(2단계 Vision/Lens 이미지 유사도에서 사용).
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
        String thumbnailUrl,
        String rawText,
        Double confidence
) {
    /**
     * 매칭을 시도할 만한 식별 정보가 하나도 없는 상태인가 (→ FinalResult.UNKNOWN).
     *
     * thumbnailUrl 은 일부러 판단 대상에서 뺐다 — 1단계 매칭은 전부 텍스트 기반이라
     * 썸네일만 있고 상품명이 없는 항목은 어차피 후보조회가 안 된다. 썸네일이 있다고
     * isEmpty()=false 로 만들면 판정 불가건이 NO_MATCH(정상)로 잘못 내려간다.
     * 2단계에서 이미지 판정이 붙으면 그때 여기도 같이 고쳐야 한다.
     */
    public boolean isEmpty() {
        return isBlank(productName) && isBlank(modelName)
                && isBlank(makerName) && isBlank(barcodeNum) && isBlank(certNum);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
