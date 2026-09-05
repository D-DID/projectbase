package com.underfaker.recallcheck.client;

import com.underfaker.recallcheck.client.dto.CertDetailApiResponse;
import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국가기술표준원 SafetyKorea Open API — KC인증.
 *
 *   목록 : /openapi/api/cert/certificationList.json?conditionKey={key}&conditionValue={value}
 *   상세 : /openapi/api/cert/certificationDetail.json?certNum={certNum}
 *
 * 인증은 HTTP 헤더 AuthKey 로 보낸다(대소문자 구분).
 */
@Component
@RequiredArgsConstructor
public class SafetyKoreaCertClient {

    public static final String KEY_ALL = "all";
    public static final String KEY_CERT_NUM = "certNum";
    public static final String KEY_PRODUCT_NAME = "productName";
    public static final String KEY_MODEL_NAME = "modelName";
    public static final String KEY_CERT_DATE = "certDate";
    public static final String KEY_SIGN_DATE = "signDate";

    @Qualifier("safetyKoreaRestClient")
    private final RestClient safetyKoreaRestClient;

    public CertListApiResponse fetchList(String conditionKey, String conditionValue) {
        throw new UnsupportedOperationException("TODO: fetchList");
    }

    public CertDetailApiResponse fetchDetail(String certNum) {
        throw new UnsupportedOperationException("TODO: fetchDetail");
    }
}
