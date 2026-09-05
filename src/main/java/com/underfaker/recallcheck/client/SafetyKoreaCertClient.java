package com.underfaker.recallcheck.client;

import com.underfaker.recallcheck.client.dto.CertDetailApiResponse;
import com.underfaker.recallcheck.client.dto.CertListApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국가기술표준원 Open API — KC인증.
 *   목록 : /openapi/api/cert/certificationList.json
 *   상세 : /openapi/api/cert/certificationDetail.json
 */
@Component
@RequiredArgsConstructor
public class SafetyKoreaCertClient {

    @Qualifier("safetyKoreaRestClient")
    private final RestClient safetyKoreaRestClient;

    public CertListApiResponse fetchList(int page, int size) {
        throw new UnsupportedOperationException("TODO: fetchList");
    }

    public CertDetailApiResponse fetchDetail(String certNum) {
        throw new UnsupportedOperationException("TODO: fetchDetail");
    }
}
