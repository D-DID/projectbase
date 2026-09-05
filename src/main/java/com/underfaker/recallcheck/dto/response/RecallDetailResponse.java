package com.underfaker.recallcheck.dto.response;

import java.util.List;

/** 공표문 상세 */
public record RecallDetailResponse(

        Long recallUid,
        String recallProductName,
        String recallBrandName,
        String recallModelName,
        Integer recallModelCnt,
        String barcodeNum,
        String certNum,
        String categoryName,
        String recallTypeName,
        String recallMeans,
        String recallCmpnyName,
        String makerName,
        String makingCntryName,
        /** yyyyMMdd */
        String publishDate,
        /** 제품 결함 */
        String harmDscr,
        /** 위해 정보 */
        String accidentCaseDscr,
        /** 소비자 행동요령 */
        String publishActionDscr,
        List<String> imageUrls
) {
}
