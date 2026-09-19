package com.underfaker.recallcheck.service.matching;

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

/** FR-010 후보 도출 → FR-011 4단계 판정 → FR-012 항목별 대조 */
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
            List<FieldComparison> comparisons = compare(product, recall);
            if (comparisons.isEmpty()) {
                continue;
            }
            double score = similarityCalculator.weightedScore(comparisons);
            Decision decision = decisionResolver.resolve(score);
            String matchedField = comparisons.stream()
                    .filter(c -> c.score() >= 0.9)
                    .map(FieldComparison::field)
                    .collect(Collectors.joining(","));

            results.add(new MatchCandidate(
                    recall.getRecallUid(), score, decision, buildReason(comparisons, score), comparisons));

            matchResultRepository.save(MatchResult.builder()
                    .verificationId(verificationId)
                    .recallUid(recall.getRecallUid())
                    .similarityScore(score)
                    .matchedField(matchedField.isEmpty() ? null : matchedField)
                    .decision(decision)
                    .reason(buildReason(comparisons, score))
                    .build());
        }

        results.sort(Comparator.comparingDouble(MatchCandidate::similarityScore).reversed());
        return results;
    }

    /**
     * 항목별 대조 (FR-012).
     * 양쪽에 값이 있는 항목만 비교 대상에 넣는다.
     * recall 의 모델명·인증번호는 콤마 구분 목록이라 쪼개서 최고 점수를 취한다.
     *
     * 9/17 public 으로 변경 — FR-014 판정근거 조회(VerificationService.getEvidence)가
     * 이 계산을 그대로 다시 쓴다. match_result 에는 항목별 점수가 저장되지 않아서
     * (종합 점수·matched_field 문자열·reason 만 있음) 조회 시점에 재계산해야 하는데,
     * 그때 판정 때와 다른 코드로 계산하면 화면에 보이는 근거와 실제 판정이 어긋난다.
     * 계산 자체는 DB 를 건드리지 않는 순수 함수라 읽기 트랜잭션에서 불러도 안전하다.
     */
    @Transactional(readOnly = true)
    public List<FieldComparison> compare(ExtractedProduct product, Recall recall) {
        List<FieldComparison> comparisons = new ArrayList<>();

        addListComparison(comparisons, "modelName",
                product.modelName(), recall.getRecallModelName(), true);
        addListComparison(comparisons, "certNum",
                product.certNum(), recall.getCertNum(), false);
        addComparison(comparisons, "productName",
                product.productName(), recall.getRecallProductName());
        addComparison(comparisons, "makerName",
                product.makerName(), recall.getMakerName());

        return comparisons;
    }

    private void addComparison(List<FieldComparison> out, String field, String input, String official) {
        if (isBlank(input) || isBlank(official)) {
            return;
        }
        double score = similarityCalculator.similarity(
                fieldNormalizer.normalize(input), fieldNormalizer.normalize(official));
        out.add(new FieldComparison(field, input, official, score, similarityCalculator.weightOf(field)));
    }

    /** 콤마 구분 목록 대응 — 가장 잘 맞는 항목 하나를 대표로 쓴다 */
    private void addListComparison(List<FieldComparison> out, String field,
                                   String input, String officialList, boolean modelStyle) {
        if (isBlank(input) || isBlank(officialList)) {
            return;
        }
        String normalizedInput = modelStyle
                ? fieldNormalizer.normalizeModelName(input)
                : fieldNormalizer.normalize(input);

        double best = 0.0;
        String bestOfficial = officialList;
        for (String official : fieldNormalizer.splitList(officialList)) {
            String normalizedOfficial = modelStyle
                    ? fieldNormalizer.normalizeModelName(official)
                    : fieldNormalizer.normalize(official);
            double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
            if (score > best) {
                best = score;
                bestOfficial = official;
            }
        }
        out.add(new FieldComparison(field, input, bestOfficial, best, similarityCalculator.weightOf(field)));
    }

    private String buildReason(List<FieldComparison> comparisons, double score) {
        String detail = comparisons.stream()
                .map(c -> String.format("%s %.0f%%", korean(c.field()), c.score() * 100))
                .collect(Collectors.joining(" · "));
        return String.format("종합 %.0f%% (%s)", score * 100, detail);
    }

    private String korean(String field) {
        return switch (field) {
            case "modelName" -> "모델명";
            case "certNum" -> "인증번호";
            case "productName" -> "제품명";
            case "makerName" -> "제조사";
            default -> field;
        };
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
