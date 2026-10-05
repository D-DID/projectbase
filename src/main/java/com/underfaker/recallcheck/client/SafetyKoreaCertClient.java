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

import java.util.List;

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

    /** 10/3 — 검증 중 1건 조회용(짧은 타임아웃). RestClientConfig 참조. */
    @Qualifier("safetyKoreaLookupRestClient")
    private final RestClient safetyKoreaLookupRestClient;

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

    /**
     * 10/3 추가 — 인증번호로 목록을 조회해 <b>번호가 정확히 같은 것만</b> 돌려준다(KC인증 연동).
     *
     * 10/3 실측으로 정한 규칙:
     *   - conditionKey=certNum 은 앞부분 일치다. "CB067R2225" 로 "CB067R2225-4001" 이 나왔다 → 정확히 같은 것만 남긴다.
     *   - 없는 번호는 오류가 아니라 resultCode 2000 + 빈 목록으로 온다 → 빈 목록 = 미등록.
     *   - 응답이 비었거나 2000/2004 가 아니면 예외 — 호출 쪽(KcLookupService)이 "조회 불가"로 처리한다.
     *
     * @param certNum 정리된 인증번호(KcCertNumbers.extract 결과)
     * @return 같은 번호의 인증 행(대개 1건). 없으면 빈 목록.
     */
    public List<CertListApiResponse.Item> findByCertNumExact(String certNum) {
        CertListApiResponse response = safetyKoreaLookupRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(certListPath)
                        .queryParam("conditionKey", KEY_CERT_NUM)
                        .queryParam("conditionValue", certNum)
                        .build())
                .retrieve()
                .body(CertListApiResponse.class);

        if (response == null) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED, "KC인증 목록 응답이 비어 있습니다.");
        }
        if (!response.isSuccess() && !response.isNoData()) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED,
                    "KC인증 번호 조회 실패: " + response.resultCode() + " " + response.resultMsg());
        }
        return exactMatches(response.resultData(), certNum);
    }

    /** 앞부분 일치로 섞여 온 결과에서 번호가 정확히 같은 행만 남긴다(대소문자·앞뒤 공백 무시). */
    static List<CertListApiResponse.Item> exactMatches(List<CertListApiResponse.Item> items, String certNum) {
        if (items == null || certNum == null) {
            return List.of();
        }
        String want = certNum.trim();
        return items.stream()
                .filter(item -> item.certNum() != null && item.certNum().trim().equalsIgnoreCase(want))
                .toList();
    }
}
