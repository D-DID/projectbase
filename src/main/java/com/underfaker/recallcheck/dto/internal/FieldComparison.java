package com.underfaker.recallcheck.dto.internal;

/**
 * 항목 단위 일치 여부.
 * 슬라이드 23 의 "항목별 대조" 표에 그대로 대응한다.
 *
 * @param field    항목명 (productName / modelName / makerName / certNum ...)
 * @param inputValue    사용자 입력에서 추출된 값
 * @param officialValue 공식 리콜 데이터의 값
 * @param score    항목 유사도 0.0 ~ 1.0
 * @param weight   해당 항목의 가중치
 */
public record FieldComparison(

        String field,
        String inputValue,
        String officialValue,
        double score,
        double weight
) {
    public boolean matched() {
        return score >= 1.0;
    }
}
