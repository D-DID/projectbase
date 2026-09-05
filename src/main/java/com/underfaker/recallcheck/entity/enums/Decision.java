package com.underfaker.recallcheck.entity.enums;

/**
 * 후보별 판정 (match_result.decision)
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 * ORDINAL 을 쓰면 상수 순서를 바꾸는 순간 기존 데이터의 의미가 전부 어긋난다.
 */
public enum Decision {

    /** 일치 — 리콜 대상 상품과 일치 */
    MATCH,

    /** 부분 일치 — 추가 확인 필요 */
    PARTIAL,

    /** 불일치 — 일치하는 리콜 정보 없음 */
    NO_MATCH
}
