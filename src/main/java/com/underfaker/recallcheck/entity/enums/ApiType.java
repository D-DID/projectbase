package com.underfaker.recallcheck.entity.enums;

/**
 * 동기화 대상 API 구분 (api_sync_log.api_type).
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 */
public enum ApiType {

    /** 국내리콜정보 */
    RECALL,

    /** KC인증정보 */
    CERT
}
