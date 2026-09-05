package com.underfaker.recallcheck.entity.enums;

/**
 * 검증 입력 방식 (verification.input_type)
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 * ORDINAL 을 쓰면 상수 순서를 바꾸는 순간 기존 데이터의 의미가 전부 어긋난다.
 */
public enum InputType {

    /** 상품 페이지 URL */
    URL,

    /** 제품 이미지 업로드 */
    IMAGE,

    /** 사용자 직접 입력 */
    MANUAL
}
