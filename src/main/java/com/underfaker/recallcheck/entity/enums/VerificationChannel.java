package com.underfaker.recallcheck.entity.enums;


/**
 * 검증 요청이 어느 화면에서 들어왔는지 (verification.channel)
 *
 * 9/13 추가 — "제품 정보로 리콜 검증"(단건, 웹 직접입력)과 "쿠팡 구매 이력 검증"(배치, 크롬 확장이
 * 주문목록에서 긁어 보낸 것)을 프론트에서 서로 다른 화면으로 분리해서 보여줘야 하는데,
 * 기존엔 두 경로 모두 verifyByManualInput() 을 그대로 타면서 InputType.MANUAL 로만 저장돼
 * DB 레벨에서 구별할 방법이 없었다. entity.enums.SourceType 은 이거랑 다른 용도
 * (extraction.source — 상품 정보를 어떤 방식으로 추출했는지)라 그대로 재사용하면 의미가 겹쳐서
 * 새로 분리했다.
 *
 * 반드시 @Enumerated(EnumType.STRING) 으로 매핑할 것.
 */
public enum VerificationChannel {

    /** localhost:8080 웹페이지의 "제품 정보로 리콜 검증" 폼에서 단건으로 직접 입력 */
    WEB,

    /** 크롬 확장이 쿠팡 주문목록에서 긁어서 배치로 보낸 것 */
    EXTENSION
}
