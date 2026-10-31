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
        LocalDateTime createdAt,

        /* 9/27 — 화면 4단계 상태와 사진 확인 버튼 (dto.internal.ResultView 참조) */
        String resultState,
        String imageCheck,
        Boolean imageCheckAvailable,
        String missingReason,

        /* 9/30 — 확장이 쿠팡 상세페이지에서 읽은 KC 인증정보 상태(DISCLOSED/REFERENCED/NONE/UNREADABLE).
           웹 직접입력·9/27 이전 건은 null. 쿠팡 결과 화면이 "KC 인증 대상 제품이 몇 개였는지"를 보여 주는 데 쓴다.
           kcText 는 판매자가 KC 칸에 적은 원문(예: "해당없음(14세 이상)") — 근거 표시용 */
        String kcStatus,
        String kcText,

        /* 10/3 — KC 인증 DB 조회 결과(FOUND/NOT_FOUND/UNAVAILABLE)와 인증상태 원문("적합"·"기간만료" 등).
           인증번호가 없었거나 10/3 이전 검증이면 null */
        String kcLookup,
        String kcCertState,

        /* 10/31 — 마지막으로 확인한 시각(updated_at). 같은 입력을 다시 확인하면 새 검증을 쌓지 않고 이 값만 바뀐다.
           이력 목록은 이 순서로 정렬된다 */
        LocalDateTime checkedAt
) {

    /** 10/31 이전 형태(checkedAt 없음) 호환용 */
    public VerificationHistoryResponse(Long verificationId, InputType inputType, VerificationChannel channel,
                                       String inputSummary, String makerName, VerificationStatus status,
                                       FinalResult finalResult, LocalDateTime createdAt, String resultState,
                                       String imageCheck, Boolean imageCheckAvailable, String missingReason,
                                       String kcStatus, String kcText, String kcLookup, String kcCertState) {
        this(verificationId, inputType, channel, inputSummary, makerName, status, finalResult, createdAt,
                resultState, imageCheck, imageCheckAvailable, missingReason, kcStatus, kcText, kcLookup,
                kcCertState, null);
    }
}
