package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.request.RecallSearchRequest;
import com.underfaker.recallcheck.dto.response.RecallDetailResponse;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.repository.RecallFileRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FR-009 — 캐시에서 리콜 후보 조회.
 * 매 검증마다 외부 API 를 호출하지 않고 로컬에 적재된 recall 테이블을 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecallQueryService {

    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;

    /**
     * 매칭 후보 조회.
     *
     * 후보 축소 순서 — 단서가 강한 것부터:
     *   1) barcodeNum 완전 일치
     *   2) certNum 부분 일치 (recall.cert_num 은 콤마 구분 목록)
     *   3) recallModelName 부분 일치 (recall_model_name 도 콤마 구분 목록)
     *   4) recallProductName 부분 일치
     */
    public List<Recall> findCandidates(ExtractedProduct product) {
        // TODO 위 순서대로 조회하고 중복 제거해서 반환
        throw new UnsupportedOperationException("TODO: findCandidates");
    }

    public PageResponse<RecallDetailResponse> search(RecallSearchRequest request) {
        throw new UnsupportedOperationException("TODO: search");
    }

    public RecallDetailResponse getDetail(Long recallUid) {
        // TODO recall + recall_file 조회해서 imageUrls 채우기
        throw new UnsupportedOperationException("TODO: getDetail");
    }
}
