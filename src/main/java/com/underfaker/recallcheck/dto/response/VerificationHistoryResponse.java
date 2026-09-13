package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.InputType;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import com.underfaker.recallcheck.entity.enums.VerificationStatus;

import java.time.LocalDateTime;

/**
 * FR-015 검증 이력 목록 항목.
 *
 * 9/13 추가 — channel 필드로 "제품 정보로 리콜 검증"(WEB)과 "쿠팡 구매 이력 검증"(EXTENSION)을
 * 프론트에서 구분해서 보여줄 수 있게 함. GET /api/verifications/me?channel=EXTENSION 으로
 * 필터링도 가능.
 *
 * 9/13 수정 — inputSummary 버그 수정: 예전엔 직접입력 건이면 무조건 "직접 입력" 고정 문자열만
 * 내려가서 목록에 실제 상품명이 하나도 안 보였음(품목 검색/쿠팡 이력 화면 둘 다 재현됨).
 * 이제 추출된 상품명이 있으면 그걸 담는다. makerName 은 카드 부제("쿠팡 상품명 · OO베이비")용
 * 보조 필드 — 추출 정보가 없으면 null.
 */
public record VerificationHistoryResponse(

        Long verificationId,
        InputType inputType,
        VerificationChannel channel,
        String inputSummary,
        String makerName,
        VerificationStatus status,
        FinalResult finalResult,
        LocalDateTime createdAt
) {
}
