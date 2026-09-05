package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * certificationDetail.json 응답.
 * 목록 항목에 파생모델·연관인증번호·제조공장·인증이미지 배열이 추가된다.
 * 현재 DDL 에는 해당 컬럼이 없어 엔티티로는 적재하지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CertDetailApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("resultData") Item resultData
) {

    public boolean isSuccess() {
        return "2000".equals(resultCode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("certUid") Long certUid,
            @JsonProperty("certNum") String certNum,
            @JsonProperty("certState") String certState,
            @JsonProperty("certDate") String certDate,
            @JsonProperty("productName") String productName,
            @JsonProperty("brandName") String brandName,
            @JsonProperty("modelName") String modelName,
            @JsonProperty("makerName") String makerName,
            @JsonProperty("makerCntryName") String makerCntryName,
            /** 상세 조회 전용 */
            @JsonProperty("derivationModels") List<String> derivationModels,
            @JsonProperty("similarCertifications") List<String> similarCertifications,
            @JsonProperty("certificationImageUrls") List<String> certificationImageUrls
    ) {
    }
}
