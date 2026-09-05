package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * recallDetail.json 응답 — 목록 항목과 동일한 필드에 recallFiles 배열이 추가된다.
 *
 * 명세서 대조에서 확인된 원문 불일치(첫 호출 때 실제 응답으로 확정할 것):
 *   - recallCmpnDivName(명세 표) vs recallCmpnyDivName(예시 JSON) — 리콜 사업자 구분.
 *     현재 DDL 에 해당 컬럼이 없어 매핑하지 않는다.
 *   - recallurl(명세 표) vs recallUrl(예시 JSON) — 국외리콜 응답 항목.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecallDetailApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("resultData") Item resultData
) {

    public boolean isSuccess() {
        return "2000".equals(resultCode);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("recallUid") Long recallUid,
            @JsonProperty("recallProductName") String recallProductName,
            @JsonProperty("recallBrandName") String recallBrandName,
            @JsonProperty("recallModelName") String recallModelName,
            @JsonProperty("recallModelCnt") Integer recallModelCnt,
            @JsonProperty("barcodeNum") String barcodeNum,
            @JsonProperty("certNum") String certNum,
            @JsonProperty("categoryName") String categoryName,
            @JsonProperty("recallTypeName") String recallTypeName,
            @JsonProperty("recallMeans") String recallMeans,
            @JsonProperty("recallCmpnyName") String recallCmpnyName,
            @JsonProperty("makerName") String makerName,
            @JsonProperty("makingCntryName") String makingCntryName,
            @JsonProperty("publishDate") String publishDate,
            @JsonProperty("harmDscr") String harmDscr,
            @JsonProperty("accidentCaseDscr") String accidentCaseDscr,
            @JsonProperty("publishActionDscr") String publishActionDscr,
            /** 상세 조회 전용 — recall_file 테이블로 적재된다 */
            @JsonProperty("recallFiles") List<RecallFileItem> recallFiles
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecallFileItem(

            /** '전체사진' 또는 '부분사진' — FileDiv.from() 으로 변환 */
            @JsonProperty("fileDiv") String fileDiv,
            @JsonProperty("imageUrl") String imageUrl
    ) {
    }
}
