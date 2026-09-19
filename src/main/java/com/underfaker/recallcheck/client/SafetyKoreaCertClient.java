package com.underfaker.recallcheck.client;

import com.underfaker.recallcheck.client.dto.CertDetailApiResponse;
import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    @Value("${openapi.safety-korea.cert-list-path}")
    private String certListPath;

    @Value("${openapi.safety-korea.cert-detail-path}")
    private String certDetailPath;

    public CertListApiResponse fetchList(String conditionKey, String conditionValue) {
        CertListApiResponse response = safetyKoreaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(certListPath)
                        .queryParam("conditionKey", conditionKey)
                        .queryParam("conditionValue", conditionValue)
                        .build())
                .retrieve()
                .body(CertListApiResponse.class);

        if (response == null) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED, "KC인증 목록 응답이 비어 있습니다.");
        }
        if (!response.isSuccess() && !response.isNoData()) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED,
                    "KC인증 목록 조회 실패: " + response.resultCode() + " " + response.resultMsg());
        }
        return response;
    }

    public CertDetailApiResponse fetchDetail(String certNum) {
        CertDetailApiResponse response = safetyKoreaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(certDetailPath)
                        .queryParam("certNum", certNum)
                        .build())
                .retrieve()
                .body(CertDetailApiResponse.class);

        if (response == null || !response.isSuccess()) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED,
                    "KC인증 상세 조회 실패 (certNum=" + certNum + ")");
        }
        return response;
    }
}