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
import com.underfaker.recallcheck.service.matching.FieldNormalizer;
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
 *
 * 9/14 수정 — 후보조회 정규화 버그: 원래 여기서 recall_product_name 등 원본 컬럼에
 * 그대로 LIKE 를 걸었다. "가정용물티슈"(공백 없음)로 검증하면 "가정용 물티슈"(공백 있음)
 * 리콜은 SQL 부분일치가 안 돼서 후보 풀에 아예 안 들어갔다. 입력값도 FieldNormalizer 로
 * 정규화하고, recall 쪽 normalized_* 컬럼(엔티티 저장 시 자동 계산)을 기준으로 비교하도록
 * 고쳤다. barcodeNum 은 표기 차이가 실질적으로 없어 정규화 대상에서 제외.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecallQueryService {

    private static final int MAX_CANDIDATES = 50;

    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;
    private final FieldNormalizer fieldNormalizer;

    /**
     * 매칭 후보 조회. 단서가 강한 것부터 좁혀 나간다.
     *   1) barcodeNum 완전 일치 (정규화 안 함)
     *   2) certNum 부분 일치 — 정규화 컬럼 기준 (콤마 구분 목록)
     *   3) recallModelName 부분 일치 — 정규화 컬럼 기준 (콤마 구분 목록)
     *   4) recallProductName 부분 일치 — 정규화 컬럼 기준
     */
    public List<Recall> findCandidates(ExtractedProduct product) {
        Map<Long, Recall> merged = new LinkedHashMap<>();

        if (notBlank(product.barcodeNum())) {
            put(merged, recallRepository.findByBarcodeNum(product.barcodeNum().trim()));
        }
        if (notBlank(product.certNum())) {
            put(merged, recallRepository.findByNormalizedCertNumContaining(
                    fieldNormalizer.normalize(product.certNum())));
        }
        if (notBlank(product.modelName())) {
            put(merged, recallRepository.findByNormalizedModelNameContaining(
                    fieldNormalizer.normalize(product.modelName())));
        }
        if (notBlank(product.productName())) {
            put(merged, recallRepository.findByNormalizedProductNameContaining(
                    fieldNormalizer.normalize(product.productName())));
        }

        // 단서가 약할 때를 대비해 제품명 토큰으로 한 번 더 훑는다 (토큰도 정규화해서 비교)
        if (merged.isEmpty() && notBlank(product.productName())) {
            for (String token : product.productName().trim().split("\\s+")) {
                if (token.length() >= 2) {
                    put(merged, recallRepository.findByNormalizedProductNameContaining(
                            fieldNormalizer.normalize(token)));
                }
            }
        }

        List<Recall> result = new ArrayList<>(merged.values());
        return result.size() > MAX_CANDIDATES ? result.subList(0, MAX_CANDIDATES) : result;
    }

    /**
     * FR-009 캐시 리콜 검색 — GET /api/recalls?productName=...&modelName=...&makerName=...&certNum=...
     * 9/14 구현: 이전엔 request 의 검색조건 필드를 전부 무시하고 전체 목록만 페이징해서 반환했다
     * (버그가 아니라 미구현 상태). productName·modelName·certNum 은 정규화 컬럼 기준으로 비교하고,
     * makerName 은 정규화 컬럼이 없어 원본 텍스트로 비교한다. 값이 없으면(null/blank) 그 조건은
     * 통째로 무시한다.
     */
    public PageResponse<RecallDetailResponse> search(RecallSearchRequest request) {
        Page<Recall> page = recallRepository.search(
                normalizedOrNull(request.productName()),
                normalizedOrNull(request.modelName()),
                blankToNull(request.makerName()),
                normalizedOrNull(request.certNum()),
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

    private String normalizedOrNull(String raw) {
        return notBlank(raw) ? fieldNormalizer.normalize(raw) : null;
    }

    private String blankToNull(String raw) {
        return notBlank(raw) ? raw.trim() : null;
    }
}
