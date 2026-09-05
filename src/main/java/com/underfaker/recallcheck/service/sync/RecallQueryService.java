package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.request.RecallSearchRequest;
import com.underfaker.recallcheck.dto.response.RecallDetailResponse;
import com.underfaker.recallcheck.entity.Recall;
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

    /** 매칭 후보 조회 — 모델명·인증번호·제품명 기준 1차 필터링 */
    public List<Recall> findCandidates(ExtractedProduct product) {
        // TODO 인증번호 완전일치 → 모델명 부분일치 → 제품명 토큰 검색 순으로 후보 축소
        throw new UnsupportedOperationException("TODO: findCandidates");
    }

    public PageResponse<RecallDetailResponse> search(RecallSearchRequest request) {
        throw new UnsupportedOperationException("TODO: search");
    }

    public RecallDetailResponse getDetail(Long recallUid) {
        throw new UnsupportedOperationException("TODO: getDetail");
    }
}
