package com.underfaker.recallcheck.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * recallList.json 응답 (외부 전용 DTO).
 *
 * 인터페이스 설계서 v2.0 대조에서 확인된 불일치를 여기서 흡수한다.
 *   - recallCmpnDivName : 명세 표(p.11) ≠ 예시 JSON(p.10)
 *   - recallurl / recallUrl : 대소문자 불일치 (p.17 vs p.16)
 *   - accidentCaseDscr : 표 국문명(p.17) ≠ 예시 값(p.15)
 * 엔티티에 직접 매핑하지 말 것. 매퍼에서 Recall 로 변환한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecallListApiResponse(

        @JsonProperty("resultCode") String resultCode,
        @JsonProperty("resultMsg") String resultMsg,
        @JsonProperty("totalCount") Integer totalCount,
        @JsonProperty("items") List<Item> items
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(

            @JsonProperty("recallUid") Long recallUid,
            @JsonProperty("prductNm") String productName,
            @JsonProperty("mdlNm") String modelName,
            @JsonProperty("mnfcturNm") String makerName,
            @JsonProperty("certNum") String certNum,
            /** 리콜 종류 — 명세 불일치 항목 */
            @JsonProperty("recallCmpnDivName") String recallType,
            @JsonProperty("bssnDate") String announcedAt,
            /** 공표문 링크 — 대소문자 불일치 항목 */
            @JsonProperty("recallUrl") String recallUrl
    ) {
    }
}
