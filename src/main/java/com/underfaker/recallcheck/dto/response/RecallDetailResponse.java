package com.underfaker.recallcheck.dto.response;

import java.time.LocalDate;
import java.util.List;

/** 공표문 상세 */
public record RecallDetailResponse(

        Long recallUid,
        String productName,
        String modelName,
        String makerName,
        String certNum,
        String recallType,
        String defectContent,
        String consumerAction,
        LocalDate announcedAt,
        String recallUrl,
        List<String> imageUrls
) {
}
