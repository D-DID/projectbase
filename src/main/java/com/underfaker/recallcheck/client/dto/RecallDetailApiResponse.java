package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** recallDetail.json 응답 — 상세 정보 및 리콜 사진 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecallDetailApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("recallUid") Long recallUid,
        @JsonProperty("prductNm") String productName,
        @JsonProperty("mdlNm") String modelName,
        @JsonProperty("mnfcturNm") String makerName,
        /** 제품 결함 / 사고 내용 — 명세 불일치 항목 */
        @JsonProperty("accidentCaseDscr") String defectContent,
        @JsonProperty("cnsmrActnMatter") String consumerAction,
        @JsonProperty("recallUrl") String recallUrl,
        @JsonProperty("imageList") List<String> imageUrls
) {
}
