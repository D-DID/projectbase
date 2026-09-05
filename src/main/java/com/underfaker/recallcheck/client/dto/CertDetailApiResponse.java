package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** certificationDetail.json 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CertDetailApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("certNum") String certNum,
        @JsonProperty("prductNm") String productName,
        @JsonProperty("mdlNm") String modelName,
        @JsonProperty("mnfcturNm") String makerName,
        @JsonProperty("certDate") String certifiedAt
) {
}
