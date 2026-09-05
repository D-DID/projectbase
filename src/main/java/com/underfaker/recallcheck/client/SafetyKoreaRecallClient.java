package com.underfaker.recallcheck.client;

import com.underfaker.recallcheck.client.dto.RecallDetailApiResponse;
import com.underfaker.recallcheck.client.dto.RecallListApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 국가기술표준원 Open API — 국내리콜.
 *   목록 : /openapi/api/recall/recallList.json
 *   상세 : /openapi/api/recall/recallDetail.json
 */
@Component
@RequiredArgsConstructor
public class SafetyKoreaRecallClient {

    @Qualifier("safetyKoreaRestClient")
    private final RestClient safetyKoreaRestClient;

    /** 제품명·모델명·바코드·인증번호·공표일자 기준 리콜 목록 조회 */
    public RecallListApiResponse fetchList(int page, int size) {
        // TODO ServiceKey 쿼리 파라미터 부착, 결과 코드 검사
        throw new UnsupportedOperationException("TODO: fetchList");
    }

    /** recallUid 기준 상세 정보 및 리콜 사진 조회 */
    public RecallDetailApiResponse fetchDetail(Long recallUid) {
        throw new UnsupportedOperationException("TODO: fetchDetail");
    }
}
