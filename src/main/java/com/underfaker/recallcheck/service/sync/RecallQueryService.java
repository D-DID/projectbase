package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.request.RecallSearchRequest;
import com.underfaker.recallcheck.dto.response.RecallDetailResponse;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.RecallFile;
import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import com.underfaker.recallcheck.repository.RecallFileRepository;
import com.underfaker.recallcheck.repository.RecallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FR-009 — 캐시에서 리콜 후보 조회.
 * 매 검증마다 외부 API 를 호출하지 않고 로컬 recall 테이블을 조회한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecallQueryService {

    private static final int MAX_CANDIDATES = 50;

    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;

    /**
     * 매칭 후보 조회. 단서가 강한 것부터 좁혀 나간다.
     *   1) barcodeNum 완전 일치
     *   2) certNum 부분 일치 (콤마 구분 목록)
     *   3) recallModelName 부분 일치 (콤마 구분 목록)
     *   4) recallProductName 부분 일치
     */
    public List<Recall> findCandidates(ExtractedProduct product) {
        Map<Long, Recall> merged = new LinkedHashMap<>();

        if (notBlank(product.barcodeNum())) {
            put(merged, recallRepository.findByBarcodeNum(product.barcodeNum().trim()));
        }
        if (notBlank(product.certNum())) {
            put(merged, recallRepository.findByCertNumContaining(product.certNum().trim()));
        }
        if (notBlank(product.modelName())) {
            put(merged, recallRepository.findByRecallModelNameContaining(product.modelName().trim()));
        }
        if (notBlank(product.productName())) {
            put(merged, recallRepository.findByRecallProductNameContaining(product.productName().trim()));
        }

        // 단서가 약할 때를 대비해 제품명 토큰으로 한 번 더 훑는다
        if (merged.isEmpty() && notBlank(product.productName())) {
            for (String token : product.productName().trim().split("\\s+")) {
                if (token.length() >= 2) {
                    put(merged, recallRepository.findByRecallProductNameContaining(token));
                }
            }
        }

        List<Recall> result = new ArrayList<>(merged.values());
        return result.size() > MAX_CANDIDATES ? result.subList(0, MAX_CANDIDATES) : result;
    }

    public PageResponse<RecallDetailResponse> search(RecallSearchRequest request) {
        Page<Recall> page = recallRepository.findAllByOrderByPublishDateDesc(
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
        return PageResponse.from(page.map(this::toResponse));
    }

    public RecallDetailResponse getDetail(Long recallUid) {
        Recall recall = recallRepository.findById(recallUid)
                .orElseThrow(() -> new BusinessException(ErrorCode.RECALL_NOT_FOUND));
        return toResponse(recall);
    }

    private RecallDetailResponse toResponse(Recall r) {
        List<String> imageUrls = recallFileRepository.findByRecallUid(r.getRecallUid()).stream()
                .map(RecallFile::getImageUrl)
                .toList();

        return new RecallDetailResponse(
                r.getRecallUid(), r.getRecallProductName(), r.getRecallBrandName(),
                r.getRecallModelName(), r.getRecallModelCnt(), r.getBarcodeNum(), r.getCertNum(),
                r.getCategoryName(), r.getRecallTypeName(), r.getRecallMeans(), r.getRecallCmpnyName(),
                r.getMakerName(), r.getMakingCntryName(), r.getPublishDate(),
                r.getHarmDscr(), r.getAccidentCaseDscr(), r.getPublishActionDscr(), imageUrls);
    }

    private void put(Map<Long, Recall> map, List<Recall> found) {
        for (Recall r : found) {
            map.putIfAbsent(r.getRecallUid(), r);
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
