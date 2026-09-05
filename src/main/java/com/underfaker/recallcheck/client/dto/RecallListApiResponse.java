package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * recallList.json 응답 (외부 전용 DTO — 엔티티에 직접 매핑하지 말 것).
 *
 * 공통 응답 규격: resultCode / resultMsg / resultData
 * 결과 코드: 2000 Success · 2004 No Data · 4000 Invalid Auth Key · 4001 Invalid IP
 *           · 4005 Invalid Parameter · 5000 Internal Server Error
 *
 * 주의: resultData 가 배열인지 객체 래퍼인지는 명세서에 명시돼 있지 않다.
 *       첫 호출 응답을 보고 이 타입을 확정할 것.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecallListApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("resultData") List<Item> resultData
) {

    public boolean isSuccess() {
        return "2000".equals(resultCode);
    }

    /** 데이터 없음 — 오류가 아니라 정상 응답이다 */
    public boolean isNoData() {
        return "2004".equals(resultCode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("recallUid") Long recallUid,
            @JsonProperty("recallProductName") String recallProductName,
            @JsonProperty("recallBrandName") String recallBrandName,
            /** 콤마로 구분된 모델명 목록 */
            @JsonProperty("recallModelName") String recallModelName,
            @JsonProperty("recallModelCnt") Integer recallModelCnt,
            @JsonProperty("barcodeNum") String barcodeNum,
            /** 콤마로 구분된 인증번호 목록 */
            @JsonProperty("certNum") String certNum,
            @JsonProperty("categoryName") String categoryName,
            @JsonProperty("recallTypeName") String recallTypeName,
            @JsonProperty("recallMeans") String recallMeans,
            @JsonProperty("recallCmpnyName") String recallCmpnyName,
            @JsonProperty("makerName") String makerName,
            @JsonProperty("makingCntryName") String makingCntryName,
            /** yyyyMMdd */
            @JsonProperty("publishDate") String publishDate,
            /** 제품 결함 */
            @JsonProperty("harmDscr") String harmDscr,
            /** 위해 정보 */
            @JsonProperty("accidentCaseDscr") String accidentCaseDscr,
            /** 소비자 행동요령 (v2.0 추가 항목) */
            @JsonProperty("publishActionDscr") String publishActionDscr
    ) {
    }
}
