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
        Decision after = decide(PINK_FOOT_CERT.applyTo(seller), recall);

        assertNotEquals(Decision.MATCH, before);   // 원문 그대로는 인증번호·모델명 확정 근거가 없다
        assertEquals(Decision.MATCH, after);
    }

    @Test
    void 리콜_공표문에_인증번호가_없어도_인증DB_모델명이_같으면_일치() {
        // 리콜 4,092건 중 948건(23%)은 인증번호 칸이 "-"·"공급자적합성" 등이라 번호로는 못 잡는다(10/3 실측).
        ExtractedProduct seller = new ExtractedProduct("핑크풋 슬라임 대용량 3종 세트", null, null, null, null,
                "CB067R2225-4001", null, null, 1.0);
        Recall recallWithoutCert = pinkFootRecall("-");

        Decision before = decide(seller, recallWithoutCert);
        Decision after = decide(PINK_FOOT_CERT.applyTo(seller), recallWithoutCert);

        assertNotEquals(Decision.MATCH, before);
        assertEquals(Decision.MATCH, after);   // 인증 DB 모델명 "핑크풋 슬라임" = 공표문 모델명
    }

    @Test
    void 인증DB_대표_모델명이_리콜_모델명과_다르면_판정을_깎지_않는다() {
        // 10/3 실측: B361R3583-1003 — 인증 DB 대표 모델 "테디웨딩베어", 같은 번호의 리콜(uid 2855) 모델 "허스키미니".
        // KC 번호 하나에 모델이 여러 개다. 대표 모델명이 다르다는 건 "다른 제품"의 증거가 아니다.
        KcLookup teddy = new KcLookup(KcLookup.Status.FOUND, "B361R3583-1003", "기간만료", "20110826",
                "완구", null, "테디웨딩베어", "-", "중국", "API");
        Recall husky = Recall.builder()
                .recallUid(2855L).recallProductName("완구(비작동완구)").recallModelName("허스키미니")
                .certNum("-").publishDate("20130103").build();   // 번호 칸을 비워 인증 모델명 효과만 본다
        ExtractedProduct seller = new ExtractedProduct("허스키미니 강아지 인형", null, null, null, null,
                "B361R3583-1003", null, null, 1.0);

        MatchProfile profile = MatchProfile.of(husky);
        List<FieldComparison> withKc = matching.compare(teddy.applyTo(seller), husky, profile);
        double before = calculator.weightedScore(matching.compare(seller, husky, profile));
        double after = calculator.weightedScore(withKc);

        assertEquals(before, after, 1e-9);                       // 점수 그대로
        assertEquals(decide(seller, husky), decide(teddy.applyTo(seller), husky));
        FieldComparison certRow = withKc.stream()
                .filter(c -> c.field().equals(MatchingService.CERT_MODEL_FIELD)).findFirst().orElseThrow();
        assertEquals(0.0, certRow.weight());                     // 근거표엔 남되 반영 안 함
    }

    @Test
    void 인증DB에_없는_번호면_판정을_올리지_않는다() {
        ExtractedProduct seller = new ExtractedProduct("핑크풋 슬라임 대용량 3종 세트", null, null, null, null,
                "XX00000-00000", null, null, 1.0);
        Recall recall = pinkFootRecall("-");

        Decision before = decide(seller, recall);
        Decision after = decide(KcLookup.notFound("XX00000-00000").applyTo(seller), recall);

        assertEquals(before, after);
        assertNotEquals(Decision.MATCH, after);
    }

    @Test
    void 공표문에_다른_인증번호가_있으면_인증_모델명이_같아도_일치로_올리지_않는다() {
        // 10/3 리뷰: 우리 번호 CB067R2225-4001 의 대표 모델명과 같은 이름이지만, 다른 번호로 인증받은 리콜
        ExtractedProduct seller = new ExtractedProduct("핑크풋 슬라임 대용량 3종 세트", null, null, null, null,
                "CB067R2225-4001", null, null, 1.0);
        Recall otherCert = pinkFootRecall("CB099R0001-1001");

        Decision before = decide(seller, otherCert);
        Decision after = decide(PINK_FOOT_CERT.applyTo(seller), otherCert);

        assertEquals(before, after);
        assertNotEquals(Decision.MATCH, after);
    }

    @Test
    void 상품명_문장_칸의_조각과만_같으면_인정하지_않는다() {
        // 10/3 리뷰: UNIDENTIFIED 공표문 "곰인형 / 토끼인형" 조각과 짧은 일반명 "곰인형" 이 우연히 같아지는 경우
        KcLookup bear = new KcLookup(KcLookup.Status.FOUND, "B111R111-1111", "적합", null, "완구", null,
                "곰인형", "-", null, "API");
        Recall sentence = Recall.builder().recallUid(9L).recallProductName("완구")
                .recallModelName("곰인형 / 토끼인형").certNum("-").publishDate("20200101").build();
        ExtractedProduct seller = new ExtractedProduct("포근한 곰인형", null, null, null, null,
                "B111R111-1111", null, null, 1.0);

        assertEquals(decide(seller, sentence), decide(bear.applyTo(seller), sentence));
        assertNotEquals(Decision.MATCH, decide(bear.applyTo(seller), sentence));
    }

    @Test
    void 대조할_항목이_없던_리콜에_반영_안되는_행만_생기지_않는다() {
        // 10/3 리뷰: 다른 항목이 하나도 없는데 가중치 0 인 인증 모델명 행만 있으면 0점 후보가 저장된다
        ExtractedProduct seller = new ExtractedProduct(null, null, null, null, null, "B361R3583-1003", null, null, 1.0);
        KcLookup teddy = new KcLookup(KcLookup.Status.FOUND, "B361R3583-1003", "기간만료", null, "완구", null,
                "테디웨딩베어", "-", null, "API");
        Recall husky = Recall.builder().recallUid(2855L).recallModelName("허스키미니").publishDate("20130103").build();

        assertEquals(List.of(), matching.compare(teddy.applyTo(seller), husky, MatchProfile.of(husky)));
    }

    @Test
    void 일반명_인증_모델명은_같아도_일치로_올리지_않는다() {
        // 10/3 실조회 20건 점검: B364R116-9002 인증 모델명 "물총" = 리콜 모델명 "물총".
        // 번호 없는 다른 회사 "물총" 리콜과도 같아지므로 확정 근거로 쓰지 않는다.
        KcLookup waterGun = new KcLookup(KcLookup.Status.FOUND, "B364R116-9002", "기간만료", "20090313", "완구", null,
                "물총", "-", null, "API");
        Recall otherWaterGun = Recall.builder().recallUid(7L).recallProductName("완구").recallModelName("물총")
                .certNum("-").publishDate("20200101").build();
        ExtractedProduct seller = new ExtractedProduct(null, null, null, null, null, "B364R116-9002", null, null, 1.0);

        assertNotEquals(Decision.MATCH, decide(waterGun.applyTo(seller), otherWaterGun));
    }

    @Test
    void 실조회_품번_모델명은_괄호_공백이_달라도_일치() {
        // 10/3 실조회: 인증 DB "OSC-930S)" / 리콜 "OSC-930S", 인증 DB "COUGAR SL500" / 리콜 "COUGARSL500"
        ExtractedProduct seller = new ExtractedProduct(null, null, null, null, null, "A043H002-7002", null, null, 1.0);
        KcLookup osc = new KcLookup(KcLookup.Status.FOUND, "A043H002-7002", "적합", "20070809", null, null,
                "OSC-930S)", "-", null, "API");
        Recall recall = Recall.builder().recallUid(8L).recallProductName("전기용품").recallModelName("OSC-930S")
                .certNum("-").publishDate("20200101").build();
        assertEquals(Decision.MATCH, decide(osc.applyTo(seller), recall));

        KcLookup cougar = new KcLookup(KcLookup.Status.FOUND, "YU10147-14002A", "반납", "20141020", null, null,
                "COUGAR SL500", "-", null, "API");
        Recall cougarRecall = Recall.builder().recallUid(9L).recallProductName("생활용품").recallModelName("COUGARSL500")
                .certNum("-").publishDate("20200101").build();
        ExtractedProduct seller2 = new ExtractedProduct(null, null, null, null, null, "YU10147-14002A", null, null, 1.0);
        assertEquals(Decision.MATCH, decide(cougar.applyTo(seller2), cougarRecall));
    }
}
