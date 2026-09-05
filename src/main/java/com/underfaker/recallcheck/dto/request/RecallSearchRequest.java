package com.underfaker.recallcheck.dto.request;

/** 리콜 캐시 검색 조건 */
public record RecallSearchRequest(

        String productName,
        String modelName,
        String makerName,
        String certNum,
        Integer page,
        Integer size
) {
    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
