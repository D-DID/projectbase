package com.underfaker.recallcheck.dto.request;

import jakarta.validation.constraints.Size;

/**
 * FR-008 사용자 직접 입력 요청.
 * 자동 추출이 실패했을 때 사용자가 식별 정보를 직접 채워 검증을 계속한다.
 * 전 항목 선택 입력이지만, 최소 한 항목은 채워져야 한다(서비스에서 검증).
 */
public record ManualInputRequest(

        @Size(max = 255) String productName,
        @Size(max = 255) String brandName,
        @Size(max = 255) String modelName,
        @Size(max = 255) String makerName,
        @Size(max = 64) String barcodeNum,
        @Size(max = 64) String certNum
) {
}
