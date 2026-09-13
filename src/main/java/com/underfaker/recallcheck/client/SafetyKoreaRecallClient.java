package com.underfaker.recallcheck.client;

import com.underfaker.recallcheck.client.dto.RecallDetailApiResponse;
import com.underfaker.recallcheck.client.dto.RecallListApiResponse;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국가기술표준원 SafetyKorea Open API — 국내리콜.
 *
 *   목록 : /openapi/api/recall/recallList.json?conditionKey={key}&conditionValue={value}
 *   상세 : /openapi/api/recall/recallDetail.json?recallUid={recallUid}
 *
 * 인증은 쿼리 파라미터가 아니라 HTTP 헤더 AuthKey 로 보낸다(대소문자 구분).
 * 페이징 파라미터는 명세에 없고 목록은 최대 1,000건까지만 내려온다.
 */
@Component
@RequiredArgsConstructor
public class SafetyKoreaRecallClient {

    /** conditionKey 로 쓸 수 있는 값 */
    public static final String KEY_ALL = "all";
    public static final String KEY_BARCODE = "barcodeNum";
    public static final String KEY_PRODUCT_NAME = "recallProductName";
    public static final String KEY_BRAND_NAME = "recallBrandName";
    public static final String KEY_MODEL_NAME = "recallModelName";
    public static final String KEY_CERT_NUM = "certNum";
    public static final String KEY_PUBLISH_DATE = "publishDate";

    @Qualifier("safetyKoreaRestClient")
    private final RestClient safetyKoreaRestClient;

    @Value("${openapi.safety-korea.recall-list-path}")
    private String recallListPath;

    @Value("${openapi.safety-korea.recall-detail-path}")
    private String recallDetailPath;

    /**
     * 국내리콜 목록 조회.
     *
     * @param conditionKey   KEY_* 상수 중 하나
     * @param conditionValue 검색어
     */
    public RecallListApiResponse fetchList(String conditionKey, String conditionValue) {
        RecallListApiResponse response = safetyKoreaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(recallListPath)
                        .queryParam("conditionKey", conditionKey)
                        .queryParam("conditionValue", conditionValue)
                        .build())
                .retrieve()
                .body(RecallListApiResponse.class);

        if (response == null) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED, "리콜 목록 응답이 비어 있습니다.");
        }
        // 2000 Success, 2004 No Data 는 둘 다 정상 응답이다. 그 외(4000/4001/4005/5000)만 실패로 취급.
        if (!response.isSuccess() && !response.isNoData()) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED,
                    "리콜 목록 조회 실패: " + response.resultCode() + " " + response.resultMsg());
        }
        return response;
    }

    /** recallUid 기준 상세 정보 및 리콜 사진 조회 */
    public RecallDetailApiResponse fetchDetail(Long recallUid) {
        RecallDetailApiResponse response = safetyKoreaRestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(recallDetailPath)
                        .queryParam("recallUid", recallUid)
                        .build())
                .retrieve()
                .body(RecallDetailApiResponse.class);

        if (response == null || !response.isSuccess()) {
            throw new BusinessException(ErrorCode.OPENAPI_CALL_FAILED,
                    "리콜 상세 조회 실패 (recallUid=" + recallUid + ")");
        }
        return response;
    }
}