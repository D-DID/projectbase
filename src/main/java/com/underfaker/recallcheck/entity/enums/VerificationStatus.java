package com.underfaker.recallcheck.entity.enums;

/**
 * 검증 처리 상태 (verification.status)
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 * ORDINAL 을 쓰면 상수 순서를 바꾸는 순간 기존 데이터의 의미가 전부 어긋난다.
 */
public enum VerificationStatus {

    /** 처리 대기 */
    PENDING,

    /** 처리 완료 */
    DONE,

    /** 처리 실패 */
    FAILED
}
