package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.common.ListingTextCleaner;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.entity.MatchResult;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.enums.Decision;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.service.sync.RecallQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * FR-010 후보 도출 → FR-011 4단계 판정 → FR-012 항목별 대조.
 *
 * ── 9/20 필드 매핑 교정 ──
 * 실데이터 55건을 적재해 보니 비교 대상 컬럼 세 개가 어긋나 있었다.
 *
 * (1) productName ↔ recall_product_name
 *     recall_product_name 은 제품명이 아니라 품목 분류명이다("기타완구(완구)").
 *     실제 상품명은 recall_model_name 쪽("(제품명) 허니 슬라임")에 있으므로 두 칸 중
 *     잘 맞는 쪽으로 비교한다.
 *
 * (2) makerName ↔ maker_name
 *     55건 전부 maker_name 이 비어 있다. 제조·수입사는 recall_cmpny_name 에 들어온다.
 *     구버전은 official 이 비었다는 이유로 비교를 건너뛰어, 가중치 0.10 짜리 항목이
 *     한 번도 동작한 적이 없다. Recall.resolveMakerName() 이 폴백을 담당한다.
 *
 * (3) brandName — 비교 자체가 없었다
 *     ExtractedProduct.brandName 도 recall_brand_name 도 값이 있는데 대조하지 않았다.
 *
 * ── 9/20 프로파일 분기 ──
 * 리콜 데이터가 품번 있는 공산품과 품번 없는 유아·잡화로 갈려서, 비교 대상 리콜마다
 * 프로파일을 판정하고 그에 맞는 가중치·임계값을 쓴다. 근거는 MatchProfile 주석 참조.
 *
 * 판매글 상품명은 그대로 쓰지 않는다. "[무료배송] 아트박스 허니 슬라임 100g, 3개" 같은 값이
 * 공표문의 "(제품명) 허니 슬라임" 과 비교되면 길이 차이만으로 점수가 깎인다.
 * ListingTextCleaner 로 정제본을 하나 더 만들어 두 후보 중 잘 맞는 쪽을 쓴다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MatchingService {

    private final RecallQueryService recallQueryService;
    private final FieldNormalizer fieldNormalizer;
    private final SimilarityCalculator similarityCalculator;
    private final DecisionResolver decisionResolver;
    private final MatchResultRepository matchResultRepository;

    /**
     * @param verificationId 검증 요청 식별자
     * @param product        통합된 식별 정보
     * @return 판정된 후보 목록 (점수 내림차순)
     */
    public List<MatchCandidate> match(Long verificationId, ExtractedProduct product) {
        List<Recall> candidates = recallQueryService.findCandidates(product);

        List<MatchCandidate> results = new ArrayList<>();
        for (Recall recall : candidates) {
            MatchProfile profile = MatchProfile.of(recall);
            List<FieldComparison> comparisons = compare(product, recall, profile);
            if (comparisons.isEmpty()) {
                continue;
            }
            double score = similarityCalculator.weightedScore(comparisons);
            Decision decision = decisionResolver.resolve(score, profile);
            String matchedField = comparisons.stream()
                    .filter(c -> c.score() >= 0.9)
                    .map(FieldComparison::field)
                    .collect(Collectors.joining(","));
            String reason = buildReason(comparisons, score, profile);

            results.add(new MatchCandidate(
                    recall.getRecallUid(), score, decision, reason, comparisons));

            matchResultRepository.save(MatchResult.builder()
                    .verificationId(verificationId)
                    .recallUid(recall.getRecallUid())
                    .similarityScore(score)
                    .matchedField(matchedField.isEmpty() ? null : matchedField)
                    .decision(decision)
                    .reason(reason)
                    .build());
        }

        results.sort(Comparator.comparingDouble(MatchCandidate::similarityScore).reversed());
        return results;
    }

    /**
     * 항목별 대조 (FR-012) — 프로파일은 리콜 데이터에서 자동 판정한다.
     *
     * 9/17 public 으로 변경 — FR-014 판정근거 조회(VerificationService.getEvidence)가 이 계산을
     * 그대로 다시 쓴다. match_result 에는 항목별 점수가 저장되지 않아서(종합 점수·matched_field·
     * reason 만) 조회 시점에 재계산해야 하는데, 판정 때와 다른 코드로 계산하면 화면 근거와
     * 실제 판정이 어긋난다. 계산 자체는 DB 를 안 건드리는 순수 함수다.
     */
    @Transactional(readOnly = true)
    public List<FieldComparison> compare(ExtractedProduct product, Recall recall) {
        return compare(product, recall, MatchProfile.of(recall));
    }

    /** 프로파일을 명시하는 버전 — match() 가 이미 판정한 값을 재사용할 때 쓴다. */
    @Transactional(readOnly = true)
    public List<FieldComparison> compare(ExtractedProduct product, Recall recall, MatchProfile profile) {
        List<FieldComparison> comparisons = new ArrayList<>();

        addListComparison(comparisons, "modelName",
                product.modelName(), recall.getRecallModelName(), true, profile);

        addListComparison(comparisons, "certNum",
                product.certNum(), recall.getCertNum(), false, profile);

        // 상품명 — 입력은 원본과 정제본 둘 다, 공식 값은 분류명 칸과 상품명이 실제로 들어 있는
        // 모델명 칸 둘 다. 네 조합 중 가장 잘 맞는 하나를 대표로 쓴다.
        addProductNameComparison(comparisons, product.productName(),
                recall.getRecallModelName(), recall.getRecallProductName(), profile);

        addComparison(comparisons, "makerName",
                product.makerName(), recall.resolveMakerName(), profile);

        addComparison(comparisons, "brandName",
                product.brandName(), recall.getRecallBrandName(), profile);

        return comparisons;
    }

    /** 단일 값 대조 */
    private void addComparison(List<FieldComparison> out, String field,
                               String input, String official, MatchProfile profile) {
        String normalizedInput = fieldNormalizer.normalize(input);
        String normalizedOfficial = fieldNormalizer.normalize(official);
        if (normalizedInput == null || normalizedOfficial == null) {
            return;
        }
        double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
        out.add(new FieldComparison(field, input, official, score,
                similarityCalculator.weightOf(field, profile)));
    }

    /**
     * 상품명 대조 — 입력 후보(원본·정제본) × 공식 후보(모델명 칸·분류명 칸)를 모두 재고
     * 가장 높은 점수를 쓴다.
     *
     * 입력 쪽을 둘로 두는 이유: 판매글 문구를 걷어낸 쪽이 대개 잘 맞지만, 공표문이 오히려
     * 긴 상품명인 경우(10022552 의 "(온라인)투데이리빙 16개 세트 …")에는 원본이 더 맞는다.
     * 공식 쪽을 둘로 두는 이유: 상품명이 어느 칸에 들어 있는지가 카테고리마다 다르다.
     */
    private void addProductNameComparison(List<FieldComparison> out, String input,
                                          String officialModelName, String officialProductName,
                                          MatchProfile profile) {
        String[] inputCandidates = ListingTextCleaner.candidates(input);
        if (inputCandidates.length == 0) {
            return;
        }

        double best = -1.0;
        String bestInput = null;
        String bestOfficial = null;

        for (String inputCandidate : inputCandidates) {
            String normalizedInput = fieldNormalizer.normalize(inputCandidate);
            if (normalizedInput == null) {
                continue;
            }
            for (String official : new String[]{officialModelName, officialProductName}) {
                String normalizedOfficial = fieldNormalizer.normalize(official);
                if (normalizedOfficial == null) {
                    continue;
                }
                double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
                if (score > best) {
                    best = score;
                    bestInput = inputCandidate;
                    bestOfficial = official;
                }
            }
        }
        if (bestOfficial == null) {
            return;
        }
        out.add(new FieldComparison("productName", bestInput, bestOfficial, best,
                similarityCalculator.weightOf("productName", profile)));
    }

    /**
     * 콤마 구분 목록 대응 — 가장 잘 맞는 항목 하나를 대표로 쓴다.
     *
     * 9/20 — 모델명은 저장 시점(Recall.normalizeFields)과 같은 normalizeModelName 규칙을 쓴다.
     * 구버전은 저장은 normalize(), 비교는 normalizeModelName() 이라 규칙이 갈라져 있었다.
     */
    private void addListComparison(List<FieldComparison> out, String field,
                                   String input, String officialList,
                                   boolean modelStyle, MatchProfile profile) {
        String normalizedInput = modelStyle
                ? fieldNormalizer.normalizeModelName(input)
                : fieldNormalizer.normalize(input);
        if (normalizedInput == null) {
            return;
        }

        List<String> officials = fieldNormalizer.splitList(officialList);
        if (officials.isEmpty()) {
            return;
        }

        double best = -1.0;
        String bestOfficial = null;
        for (String official : officials) {
            String normalizedOfficial = modelStyle
                    ? fieldNormalizer.normalizeModelName(official)
                    : fieldNormalizer.normalize(official);
            if (normalizedOfficial == null) {
                continue;
            }
            double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
            if (score > best) {
                best = score;
                bestOfficial = official;
            }
        }
        // 공식 값이 전부 플레이스홀더("-" 등)였다면 비교 대상이 아니다.
        if (bestOfficial == null) {
            return;
        }
        out.add(new FieldComparison(field, input, bestOfficial, best,
                similarityCalculator.weightOf(field, profile)));
    }

    /**
     * 판정 근거 문자열.
     * 어떤 프로파일로 쟀는지 같이 남긴다 — 같은 점수라도 임계값이 달라서
     * 결과가 갈리므로, 나중에 로그만 보고 판정을 재현하려면 이 정보가 필요하다.
     */
    private String buildReason(List<FieldComparison> comparisons, double score, MatchProfile profile) {
        String detail = comparisons.stream()
                .map(c -> String.format("%s %.0f%%", korean(c.field()), c.score() * 100))
                .collect(Collectors.joining(" · "));
        return String.format("[%s] 종합 %.0f%% (%s)", korean(profile), score * 100, detail);
    }

    private String korean(MatchProfile profile) {
        return profile == MatchProfile.IDENTIFIED ? "품번기준" : "상품명기준";
    }

    private String korean(String field) {
        return switch (field) {
            case "modelName" -> "모델명";
            case "certNum" -> "인증번호";
            case "productName" -> "제품명";
            case "makerName" -> "제조사";
            case "brandName" -> "브랜드";
            default -> field;
        };
    }
}
