package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** certificationList.json 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CertListApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("resultData") List<Item> resultData
) {

    public boolean isSuccess() {
        return "2000".equals(resultCode);
    }

    public boolean isNoData() {
        return "2004".equals(resultCode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("certUid") Long certUid,
            @JsonProperty("certNum") String certNum,
            /**
             * 인증상태. '안전인증표시 사용금지 2개월' 처럼 개월 수가 문자열에 포함되므로
             * 축약하지 말고 원문 그대로 비교할 것.
             */
            @JsonProperty("certState") String certState,
            /** yyyyMMdd */
            @JsonProperty("certDate") String certDate,
            @JsonProperty("productName") String productName,
            @JsonProperty("brandName") String brandName,
            /** 모델명(로트번호) */
            @JsonProperty("modelName") String modelName,
            @JsonProperty("makerName") String makerName,
            @JsonProperty("makerCntryName") String makerCntryName
    ) {
    }
}
