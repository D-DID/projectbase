package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.enums.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * KC인증 연동이 실제 판정을 바꾸는지 — 실제 매칭 코드(MatchingService.compare + DecisionResolver)로 확인한다.
 *
 * 리콜: db/recall-dump.sql 의 recall_uid 10023006 (2026-09-16 공표, 핑크풋 슬라임) 그대로.
 * KC 인증 DB: 10/3 실측 certificationList.json?conditionKey=certNum&conditionValue=CB067R2225-4001 응답 그대로.
 */
class KcCertMatchingTest {

    final MatchingProperties properties = new MatchingProperties();
    final MatchingService matching = new MatchingService(null, new FieldNormalizer(),
            new SimilarityCalculator(properties), new DecisionResolver(properties), null);
    final DecisionResolver resolver = new DecisionResolver(properties);
    final SimilarityCalculator calculator = new SimilarityCalculator(properties);

    static final KcLookup PINK_FOOT_CERT = new KcLookup(KcLookup.Status.FOUND, "CB067R2225-4001", "적합", "20240419",
            "완구", "", "핑크풋 슬라임", "-", "중국", "API");

    static Recall pinkFootRecall(String certNum) {
        return Recall.builder()
                .recallUid(10023006L)
                .recallProductName("기타완구(완구)")
                .recallBrandName("-")
                .recallModelName("핑크풋 슬라임")
                .recallModelCnt(1)
                .barcodeNum("-")
                .certNum(certNum)
                .categoryName("어린이>완구")
                .recallTypeName("명령에따른리콜")
                .recallCmpnyName("핑크풋")
                .publishDate("20260916")
                .build();
    }

    Decision decide(ExtractedProduct product, Recall recall) {
        MatchProfile profile = MatchProfile.of(recall);
        List<FieldComparison> comparisons = matching.compare(product, recall, profile);
        return resolver.resolve(calculator.weightedScore(comparisons), profile, comparisons);
    }

    @Test
    void 판매자가_번호를_설명과_섞어_적어도_정리된_번호로_일치() {
        // 쿠팡 '필수 표기 정보' KC 칸에 흔한 형태. 모델명 칸은 비어 있다.
        ExtractedProduct seller = new ExtractedProduct("핑크풋 슬라임 대용량 3종 세트", null, null, null, null,
                "KC 인증번호: CB067R2225-4001 (어린이제품)", null, null, 1.0);
        Recall recall = pinkFootRecall("CB067R2225-4001");

        Decision before = decide(seller, recall);
        Decision after = decide(PINK_FOOT_CERT.fillBlanks(seller), recall);

        assertNotEquals(Decision.MATCH, before);   // 원문 그대로는 인증번호·모델명 확정 근거가 없다
        assertEquals(Decision.MATCH, after);
    }

    @Test
    void 리콜_공표문에_인증번호가_없어도_인증DB_모델명으로_일치() {
        // 리콜 4,092건 중 948건(23%)은 인증번호 칸이 "-"·"공급자적합성" 등이라 번호로는 못 잡는다(10/3 실측).
        ExtractedProduct seller = new ExtractedProduct("핑크풋 슬라임 대용량 3종 세트", null, null, null, null,
                "CB067R2225-4001", null, null, 1.0);
        Recall recallWithoutCert = pinkFootRecall("-");

        Decision before = decide(seller, recallWithoutCert);
        Decision after = decide(PINK_FOOT_CERT.fillBlanks(seller), recallWithoutCert);

        assertNotEquals(Decision.MATCH, before);
        assertEquals(Decision.MATCH, after);   // 인증 DB 모델명 "핑크풋 슬라임" = 공표문 모델명
    }

    @Test
    void 인증DB에_없는_번호면_판정을_올리지_않는다() {
        ExtractedProduct seller = new ExtractedProduct("핑크풋 슬라임 대용량 3종 세트", null, null, null, null,
                "XX00000-00000", null, null, 1.0);
        Recall recall = pinkFootRecall("-");

        Decision before = decide(seller, recall);
        Decision after = decide(KcLookup.notFound("XX00000-00000").fillBlanks(seller), recall);

        assertEquals(before, after);
        assertNotEquals(Decision.MATCH, after);
    }
}
