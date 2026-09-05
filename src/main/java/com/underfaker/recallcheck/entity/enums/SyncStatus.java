package com.underfaker.recallcheck.entity.enums;

/**
 * 동기화 실행 결과 (api_sync_log.status)
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 * ORDINAL 을 쓰면 상수 순서를 바꾸는 순간 기존 데이터의 의미가 전부 어긋난다.
 */
public enum SyncStatus {

    /** 전건 성공 */
    SUCCESS,

    /** 부분 일치 — 추가 확인 필요 */
    PARTIAL,

    /** 처리 실패 */
    FAILED
}
