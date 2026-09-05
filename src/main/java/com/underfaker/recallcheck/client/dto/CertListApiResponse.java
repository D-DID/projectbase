package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** certificationList.json 응답 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CertListApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("totalCount") Integer totalCount,
        @JsonProperty("items") List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("certNum") String certNum,
            @JsonProperty("prductNm") String productName,
            @JsonProperty("mdlNm") String modelName,
            @JsonProperty("mnfcturNm") String makerName,
            @JsonProperty("certDate") String certifiedAt
    ) {
    }
}
