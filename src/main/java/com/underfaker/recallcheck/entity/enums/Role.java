package com.underfaker.recallcheck.entity.enums;

/**
 * 사용자 권한
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 * ORDINAL 을 쓰면 상수 순서를 바꾸는 순간 기존 데이터의 의미가 전부 어긋난다.
 */
public enum Role {

    /** 일반 사용자 */
    USER,

    /** 시스템 관리자 */
    ADMIN
}
