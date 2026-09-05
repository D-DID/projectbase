package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 항목별 점수 산정 (FR-010) */
@Component
@RequiredArgsConstructor
public class SimilarityCalculator {

    private final MatchingProperties properties;

    /**
     * 두 문자열의 유사도 (0.0 ~ 1.0).
     * 정규화된 편집거리(Levenshtein)를 쓴다.
     */
    public double similarity(String a, String b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        // 한쪽이 다른 쪽을 포함하면 부분 일치로 높게 본다 (모델명이 제품명에 섞여 있는 경우)
        if (a.contains(b) || b.contains(a)) {
            int shorter = Math.min(a.length(), b.length());
            int longer = Math.max(a.length(), b.length());
            return 0.75 + 0.2 * ((double) shorter / longer);
        }
        int distance = levenshtein(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return Math.max(0.0, 1.0 - ((double) distance / maxLen));
    }

    /**
     * 항목별 비교 결과에 가중치를 적용한 종합 점수.
     * 양쪽 값이 모두 있는 항목만 분모에 넣는다 — 없는 항목 때문에 점수가 깎이면 안 된다.
     */
    public double weightedScore(List<FieldComparison> comparisons) {
        double weightedSum = 0.0;
        double weightTotal = 0.0;
        for (FieldComparison c : comparisons) {
            weightedSum += c.score() * c.weight();
            weightTotal += c.weight();
        }
        return weightTotal == 0.0 ? 0.0 : weightedSum / weightTotal;
    }

    public double weightOf(String field) {
        MatchingProperties.Weight w = properties.getWeight();
        return switch (field) {
            case "modelName" -> w.getModelName();
            case "certNum" -> w.getCertNum();
            case "productName" -> w.getProductName();
            case "makerName" -> w.getMakerName();
            default -> 0.05;
        };
    }

    private int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
    }
}
