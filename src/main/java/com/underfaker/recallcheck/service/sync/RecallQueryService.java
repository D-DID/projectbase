package com.underfaker.recallcheck.service.sync;

import com.underfaker.recallcheck.common.PageResponse;
import com.underfaker.recallcheck.common.TextNormalizer;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.KcLookup;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FR-009 — 캐시에서 리콜 후보 조회.
 * 매 검증마다 외부 API 를 호출하지 않고 로컬 recall 테이블을 조회한다.
 *
 * 9/14 수정 — 원본 컬럼에 그대로 LIKE 를 걸어서 공백 표기 차이로 후보 풀에 못 들어가던 버그.
 * 입력도 정규화하고 recall 쪽 normalized_* 컬럼 기준으로 비교하도록 고쳤다.
 *
 * ── 9/20 재설계 ──
 * 실데이터 55건을 적재해 보고 두 가지가 드러났다.
 *
 * (1) 빈 문자열 조회 = 전건 오탐
 *     정규화가 "-" 를 "" 로 만들고, 그 "" 로 LIKE 를 걸면 '%%' 가 되어 recall 전건이
 *     후보로 끌려왔다. 이제 TextNormalizer 가 null 을 돌려주고, null 이거나 너무 짧은
 *     (MIN_SEARCH_LENGTH 미만) 값은 그 조건을 통째로 건너뛴다. 조건 하나가 빠지는 것은
 *     후보가 조금 줄어드는 일이지만, 빈 값으로 거는 것은 후보조회 자체를 무의미하게 만든다.
 *
 * (2) 상품명은 recall_product_name 이 아니라 recall_model_name 에 있다
 *     recall_product_name 은 "기타완구(완구)" 같은 품목 분류명이다. 확장이 쿠팡에서 긁어온
 *     "허니 슬라임" 으로 그 컬럼에 LIKE 를 걸면 영원히 안 맞는다. 실제 상품명은
 *     recall_model_name 의 "(제품명) 허니 슬라임" 쪽에 들어 있다.
 *     그래서 입력 상품명은 model_name 컬럼을 먼저 훑고, 분류명이 그대로 들어오는 경우를
 *     위해 product_name 컬럼도 함께 훑는다.
 *
 * 바코드는 정규화하지 않는다(표기 차이가 실질적으로 없다). 다만 값 없음을 뜻하는 "-" 가
 * 그대로 오므로 플레이스홀더 여부만 검사한다 — "-" 로 조회하면 바코드 없는 리콜이 전부 걸린다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecallQueryService {

    private static final int MAX_CANDIDATES = 50;

    /** 10/3 — KC 인증 DB 모델명으로 더 찾는 후보 상한 */
    private static final int CERT_MODEL_MAX_CANDIDATES = 10;

    /** 토큰 폴백에서 쓸 최소 토큰 길이. 2자는 "완구", "우산" 같은 분류어가 걸려 후보가 폭증한다. */
    private static final int MIN_TOKEN_LENGTH = 3;

    /**
     * 토큰 폴백에서 제외할 판매글 상용어.
     * 쿠팡 상품명에 흔히 섞이는 말들로, 이걸로 LIKE 를 걸면 관련 없는 리콜이 대량으로 걸린다.
     */
    private static final Set<String> TOKEN_STOPWORDS = Set.of(
            "정품", "세트", "무료배송", "당일발송", "무료", "배송", "사은품", "증정", "할인",
            "특가", "최저가", "行사", "행사", "신상", "신상품", "인기", "추천", "베스트",
            "대용량", "소용량", "리필", "본품", "구성", "개입", "매입", "묶음", "박스",
            "국내산", "수입", "정식", "공식", "당일", "택배", "km", "KG", "ML", "CM");

    private final RecallRepository recallRepository;
    private final RecallFileRepository recallFileRepository;
    private final FieldNormalizer fieldNormalizer;

    /**
     * 매칭 후보 조회. 단서가 강한 것부터 좁혀 나간다.
     *   1) barcodeNum 완전 일치 (정규화 안 함, 플레이스홀더만 거름)
     *   2) certNum 부분 일치 — 정규화 컬럼 기준
     *   3) modelName 부분 일치 — 정규화 컬럼 기준
     *   4) productName 부분 일치 — model_name 컬럼 먼저, product_name 컬럼도 함께
     *   5) (1~4 가 전부 공쳤을 때만) productName 토큰 폴백
     *
     * LinkedHashMap 이라 삽입 순서가 곧 단서 강도 순서다. MAX_CANDIDATES 로 자를 때
     * 약한 단서로 걸린 것부터 잘려 나간다.
     */
    public List<Recall> findCandidates(ExtractedProduct product) {
        Map<Long, Recall> merged = new LinkedHashMap<>();

        String barcode = barcodeKey(product.barcodeNum());
        if (barcode != null) {
            put(merged, recallRepository.findByBarcodeNum(barcode));
        }

        String certKey = fieldNormalizer.searchKey(product.certNum());
        if (certKey != null) {
            put(merged, recallRepository.findByNormalizedCertNumContaining(certKey));
        }

        // 10/3 — KC인증 연동. 인증 DB 모델명으로도 찾는다. 공표문 인증번호 칸이 "-"·"공급자적합성" 등이라
        // 번호로는 못 찾는 리콜(4,092건 중 23%)을 모델명으로 잡기 위해서. 판정은 MatchingService 가 완전일치만 반영한다.
        KcLookup kc = KcLookup.fromRawText(product.rawText());
        String certModelKey = kc == null ? null : fieldNormalizer.modelSearchKey(kc.modelNameForMatching());
        if (certModelKey != null) {
            // 흔한 이름("슬라임")이면 수백 건이 걸려 다른 후보·사진 확인 후보 자리를 차지한다(10/3 리뷰) — 앞 10건만.
            List<Recall> byCertModel = recallRepository.findByNormalizedModelNameContaining(certModelKey);
            put(merged, byCertModel.size() > CERT_MODEL_MAX_CANDIDATES
                    ? byCertModel.subList(0, CERT_MODEL_MAX_CANDIDATES) : byCertModel);
        }

        String modelKey = fieldNormalizer.modelSearchKey(product.modelName());
        if (modelKey != null) {
            put(merged, recallRepository.findByNormalizedModelNameContaining(modelKey));
        }

        // 상품명 — 실데이터에서 상품명은 model_name 칸에 들어 있다. 그쪽을 먼저 본다.
        String productAsModelKey = fieldNormalizer.modelSearchKey(product.productName());
        if (productAsModelKey != null) {
            put(merged, recallRepository.findByNormalizedModelNameContaining(productAsModelKey));
        }
        String productKey = fieldNormalizer.searchKey(product.productName());
        if (productKey != null) {
            put(merged, recallRepository.findByNormalizedProductNameContaining(productKey));
        }

        // 단서가 약할 때를 대비한 토큰 폴백. 전체 문자열로는 안 걸렸지만 일부 단어가 걸릴 수 있다.
        // 9/20 — 대상 컬럼을 model_name 으로 바꾸고(상품명이 거기 있으므로), 최소 길이를 3자로
        // 올리고, 판매글 상용어를 걸러낸다. 2자 토큰을 그대로 쓰면 "완구"·"우산" 같은 분류어가
        // 걸려서 후보 50개가 전부 무관한 행으로 채워진다.
        if (merged.isEmpty()) {
            putTokenFallback(merged, product.productName());
        }

        List<Recall> result = new ArrayList<>(merged.values());
        if (result.size() > MAX_CANDIDATES) {
            log.debug("[RecallQuery] 후보 {}건 중 상위 {}건만 사용", result.size(), MAX_CANDIDATES);
            result = result.subList(0, MAX_CANDIDATES);
        }
        if (result.isEmpty()) {
            log.debug("[RecallQuery] 후보 0건 — barcode={} cert={} model={} product={}",
                    barcode, certKey, modelKey, productKey);
        }
        return result;
    }

    /** 상품명을 단어로 쪼개 model_name 컬럼을 훑는다. 노이즈 토큰은 버린다. */
    private void putTokenFallback(Map<Long, Recall> merged, String productName) {
        if (productName == null || productName.isBlank()) {
            return;
        }
        for (String token : productName.trim().split("\\s+")) {
            String key = fieldNormalizer.modelSearchKey(token);
            if (key == null || key.length() < MIN_TOKEN_LENGTH) {
                continue;
            }
            if (TOKEN_STOPWORDS.contains(key) || isQuantityToken(key)) {
                continue;
            }
            put(merged, recallRepository.findByNormalizedModelNameContaining(key));
        }
    }

    /** "100G", "3개", "2025" 처럼 숫자로 시작하는 수량·규격 토큰인가. 제품 식별에 쓸모가 없다. */
    private boolean isQuantityToken(String normalized) {
        return !normalized.isEmpty() && Character.isDigit(normalized.charAt(0));
    }

    /**
     * 바코드 조회 키.
     * 정규화는 하지 않되(표기 차이가 없는 값이다) "-" 같은 플레이스홀더는 걸러낸다.
     * 실데이터에서 바코드 없는 행의 barcode_num 은 NULL 이 아니라 "-" 라서,
     * 그대로 조회하면 바코드 없는 리콜이 통째로 후보가 된다.
     */
    private String barcodeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        // 정규화 결과가 null 이면 값이 없다는 뜻("-", "N/A", 기호뿐 …)
        return TextNormalizer.normalize(trimmed) == null ? null : trimmed;
    }

    /**
     * FR-009 캐시 리콜 검색 — GET /api/recalls?productName=...&modelName=...&makerName=...&certNum=...
     * 파라미터가 비었거나 정규화 후 남는 글자가 없으면 그 조건은 통째로 무시한다(null 로 넘긴다).
     */
    public PageResponse<RecallDetailResponse> search(RecallSearchRequest request) {
        Page<Recall> page = recallRepository.search(
                fieldNormalizer.searchKey(request.productName()),
                fieldNormalizer.modelSearchKey(request.modelName()),
                blankToNull(request.makerName()),
                fieldNormalizer.searchKey(request.certNum()),
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

    private String blankToNull(String raw) {
        return (raw == null || raw.isBlank()) ? null : raw.trim();
    }
}
