package com.underfaker.recallcheck.dto.request;

import jakarta.validation.constraints.Size;

/**
 * FR-008 사용자 직접 입력 요청.
 * 자동 추출이 실패했을 때 사용자가 식별 정보를 직접 채워 검증을 계속한다.
 * 전 항목 선택 입력이지만, 최소 한 항목은 채워져야 한다(서비스에서 검증).
 *
 * 9/17 추가 — thumbnailUrl.
 * 9/12 결정(쿠팡 주문내역 기반 자동판정)에 따라 크롬 확장이 주문목록에서 항목별로
 * "썸네일 이미지 URL + 상품명"을 뽑아 보낸다. 신규 엔드포인트를 따로 파지 않고 기존 6필드에
 * thumbnailUrl 하나만 더해서 받는다 — 확장은 이미 POST /api/verifications/manual/batch 로
 * 이 DTO 배열을 보내고 있어서, 필드만 늘리면 기존 경로가 그대로 쓰인다(확장 쪽은
 * background.js 의 toManualInputRequest() 에 한 줄 추가하면 끝).
 *
 * 1단계에서는 받아서 extraction 테이블에 보관만 한다. 이 값을 실제 판정에 쓰는 건
 * 2단계 Google Vision/Lens 이미지 유사도 단계다.
 */
public record ManualInputRequest(

        @Size(max = 255) String productName,
        @Size(max = 255) String brandName,
        @Size(max = 255) String modelName,
        @Size(max = 255) String makerName,
        @Size(max = 64) String barcodeNum,
        @Size(max = 64) String certNum,
        @Size(max = 500) String thumbnailUrl
) {
}
