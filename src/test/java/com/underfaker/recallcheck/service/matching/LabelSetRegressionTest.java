package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.common.ListingTextCleaner;
import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.enums.Decision;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.service.sync.RecallQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 11/3 라벨링셋(쿠팡 정상 판매 제품) 실측에서 나온 오탐 회귀 시험.
 *
 * 입력은 실제 쿠팡 상세페이지 '필수 표기 정보'에서 확장 0.7.1 파서로 읽은 값, 공표문은 서버 DB 의 실제 행이다.
 * 결과 원본: 8주차 회의내용/라벨링셋_50.csv (1차, 검증 ID 1042~1091) · 라벨링셋_130.csv (2차, 검증 ID 1092~1221).
 */
class LabelSetRegressionTest {

    final MatchingProperties properties = new MatchingProperties();
    List<Recall> pool = List.of();
    final RecallQueryService query = new RecallQueryService(null, null, new FieldNormalizer()) {
        @Override
        public List<Recall> findCandidates(ExtractedProduct product) {
            return pool;
        }
    };
    final MatchingService matching = new MatchingService(query, new FieldNormalizer(),
            new SimilarityCalculator(properties), new DecisionResolver(properties),
            Mockito.mock(MatchResultRepository.class));

    static Recall recall(long uid, String product, String brand, String model, String cert, String company,
                         String category, String date) {
        return Recall.builder().recallUid(uid).recallProductName(product).recallBrandName(brand)
                .recallModelName(model).certNum(cert).recallCmpnyName(company).categoryName(category)
                .publishDate(date).build();
    }

    /** 2015 우양산 공표문 — 모델명 칸이 일반명 "우산" */
    static final Recall UMBRELLA_2015 = recall(10008707L, "우양산", "(주)협진티앤디", "우산", "안전품질표시",
            "(주)협진티앤디", "생활용품>기타 생활용품", "20150723");

    /** 2026-10-29 공표문 — 모델명 칸에 쿠팡 상품명이 옵션(", 1개, 혼합색상")째 들어 있다 */
    static final Recall CAMPING_CHAIR = recall(10023131L, "기타어린이제품(기타어린이제품)", "-",
            "(제품명) 어린이 빈티지 캠핑 의자 경량의자 접이식의자, 1개, 혼합색상", "-", "아이나라",
            "기타어린이제품>", "20261029");

    /** 품번이 있는 아동복 공표문(EdgeCaseScenariosTest 와 같은 실제 행) */
    static final Recall JACKET = recall(10022559L, "중의류(아섬)(아동용 섬유제품)", "리틀뎁", "(품번) VDECX01", "-",
            "부성상사", "어린이>아동용 섬유제품", "20260723");

    static ExtractedProduct seller(String productName, String modelName, String certNum) {
        return new ExtractedProduct(productName, null, modelName, null, null, certNum, null, null, 1.0);
    }

    Decision top(ExtractedProduct product, Recall... candidates) {
        pool = List.of(candidates);
        List<MatchCandidate> result = matching.match(1L, product);
        assertFalse(result.isEmpty(), "후보가 있어야 한다");
        return result.get(0).decision();
    }

    // ── 1. 일반명 모델명은 품번이 아니다 ──

    @Test
    void L01_모델명이_일반명_우산이면_공표문_모델명이_우산이어도_일치가_아니다() {
        // 라벨링셋 35번: 수정 전 '일치'(종합 17%)
        Decision d = top(seller("핑크퐁 아기상어 40 페이스 장우산 6K IUPFU10008", "우산", null), UMBRELLA_2015);
        assertNotEquals(Decision.MATCH, d);
        assertEquals(Decision.NO_MATCH, d);
    }

    @Test
    void L02_품번꼴_모델명이_같으면_여전히_일치() {
        assertEquals(Decision.MATCH, top(seller("리틀뎁 아동 바람막이", "VDECX01", null), JACKET));
    }

    @Test
    void L03_라벨이_붙은_품번도_품번으로_본다() {
        assertEquals(Decision.MATCH, top(seller("리틀뎁 아동 바람막이", "(품번) VDECX01", null), JACKET));
    }

    // ── 2. 공표문 모델명 칸의 옵션 조각("1개", "혼합색상")은 상품명 후보가 아니다 ──

    @Test
    void L04_옵션_혼합색상만_겹치는_쿠팡_상품은_의심이_아니다() {
        // 라벨링셋 3·29·49번: 수정 전 의심(62% · 63% · 57%)
        assertEquals(Decision.NO_MATCH,
                top(seller("뽀로로 큐티붕붕 플러스 자동차 장난감 4종 세트, 혼합색상, 1개", null, "CB064R3190-1001"), CAMPING_CHAIR));
        assertEquals(Decision.NO_MATCH,
                top(seller("옥스포드 종합 블록 OBP2042, 혼합색상, 1개", null, null), CAMPING_CHAIR));
        assertEquals(Decision.NO_MATCH,
                top(seller("핑크퐁 유아용 아기상어 멜로디 버블 토이, 혼합색상, 1개", null, "CB064R5010-4001"), CAMPING_CHAIR));
    }

    @Test
    void L05_실제로_같은_상품은_옵션_조각을_빼도_찾는다() {
        // 대조군: 그 공표문의 실제 상품(쿠팡식 이름)은 여전히 의심으로 잡혀야 한다
        Decision d = top(seller("어린이 빈티지 캠핑 의자 경량의자 접이식의자, 1개, 혼합색상", null, null), CAMPING_CHAIR);
        assertEquals(Decision.PARTIAL, d);
    }

    // ── 3. 품번 옆의 짧은 조각(품목 이름)이 긴 판매글에 들어 있다는 것만으로는 의심이 아니다 (11/3 2차, 130건) ──

    /** 품번 + 품목 이름("아동운동화")이 한 칸에 든 공표문(2026-03-16, 서버 DB 실제 행) */
    static final Recall FILA_SHOES = recall(10022340L, "휠라 꾸미 라이트 포켓몬 메가리자몽 (FILA GGUMI LIGHT POKEMON)",
            "휠라 키즈(FILA KIDS)", "FK253CD01X001 / 아동운동화", "CB014R2014-5004", "휠라코리아 주식회사",
            "어린이용품>기타 어린이제품", "20260316");

    /** 모델명 칸 전체가 "목욕놀이"인 공표문(2018-05-03) */
    static final Recall BATH_TOY = recall(10011682L, "완구", null, "목욕놀이", "CB063R095-7012", "황용토이",
            "어린이용품>완구", "20180503");

    @Test
    void L07_다른_회사_아동운동화는_품목_이름만_같아서는_의심이_아니다() {
        // 라벨링셋 60번(서버 검증 1151): 수정 전 의심 61% — 근거는 제품명 한 줄("아동운동화" 포함)
        assertEquals(Decision.NO_MATCH, top(seller(
                "바이시유 발편한 아동운동화 남아 여아 신발 키즈 유아 운동화 아동화 스니커즈 베이직", null, null), FILA_SHOES));
    }

    @Test
    void L08_알려진_한계_품번없는_칸_전체가_짧은_이름이면_그대로_의심() {
        // 라벨링셋 47번: 서버 후보 50건에는 안 들었지만 공표문 전체 재생(LabelReplay)에서 의심 58%.
        // 칸 전체가 "목욕놀이"라 "(제품명) 허니 슬라임"(EdgeCaseScenariosTest E07, 진짜 리콜 제품명)과 글자 수로
        // 가를 수 없다. 묶으면 E07 을 놓치므로 이번에는 묶지 않는다(MatchingService.isShortSidePiece 주석).
        // 이 시험은 현재 동작을 적어 두는 것 — 규칙을 바꾸면 여기서 먼저 드러난다.
        assertEquals(Decision.PARTIAL, top(seller(
                "[목욕을 좋아하게 하라] 물놀이 장난감 물속에서 움직이는 물고기 장난감 led 아기 목욕 장난감 로보피쉬 목욕놀이 물고기 LUPEIYA, 2개, 랜덤색상",
                null, null), BATH_TOY));
    }

    @Test
    void L09_짧은_조각이라도_완전히_같거나_품번이면_그대로() {
        // 상품명이 그 조각과 통째로 같으면 1.0 그대로 → 의심(식별자 없이 이름만 같으면 의심까지)
        assertEquals(Decision.PARTIAL, top(seller("아동운동화", null, null), FILA_SHOES));
        // 품번은 길이와 상관없이 기존 규칙(품번 일치 → 일치)
        assertEquals(Decision.MATCH, top(seller("휠라 아동 운동화", "FK253CD01X001", null), FILA_SHOES));
    }

    @Test
    void L10_짧은_조각에_제조사가_함께_맞으면_의심까지는_간다() {
        // 제품명 0.50(묶음) + 제조사 1.00 → 품번기준 (0.10×0.50 + 0.10×1.00) / 0.20 = 0.75 ≥ 0.60
        ExtractedProduct withMaker = new ExtractedProduct("꾸미 라이트 아동운동화 벨크로 키즈 신발", null, null,
                "휠라코리아(주)", null, null, null, null, 1.0);
        assertEquals(Decision.PARTIAL, top(withMaker, FILA_SHOES));
    }

    // ── 4. 품번이 아닌 모델명이 공표문 모델명과 통째로 같으면 의심 (11/3 2차, 130건에서 찾은 미탐) ──

    /** 2021-02-25 공표문 — 오즈키즈(㈜오픈한) 레이니 아동우비, 모자 끝 술 장식 코드 길이 초과 */
    static final Recall RAINY_COAT = recall(10016200L, "아동용섬유제품", "-", "레이니 아동우비", "-", "㈜오픈한",
            "어린이>아동용 섬유제품", "20210225");

    @Test
    void L11_고시_모델명이_공표문_모델명과_같으면_제목이_달라도_의심() {
        // 라벨링셋 65번(서버 검증 1156): 수정 전 '불일치'(KC 번호 미표기로 표시) 종합 40% — 모델명 100%(가중치 0.05)·제목 33%
        MatchCandidate c = matching.match(1L, withPool(seller("오즈키즈 아동용 레이니 우비", "레이니 아동우비", null), RAINY_COAT)).get(0);
        assertEquals(Decision.PARTIAL, c.decision());
        assertTrue(new DecisionResolver(properties).note(c.comparisons()).contains("품번이 아닌 제품 이름"));
    }

    @Test
    void L12_같은_이름에_KC_번호까지_같으면_일치() {
        Recall withCert = recall(10099002L, "아동용섬유제품", "-", "레이니 아동우비", "CB014R999-1001", "㈜오픈한",
                "어린이>아동용 섬유제품", "20210225");
        assertEquals(Decision.MATCH, top(seller("오즈키즈 아동용 레이니 우비", "레이니 아동우비", "CB014R999-1001"), withCert));
    }

    @Test
    void L13_짧은_일반명이나_다른_KC_번호면_이_규칙을_쓰지_않는다() {
        // "우산"(2자)은 그대로 불일치 — L01 과 같은 경우
        assertEquals(Decision.NO_MATCH, top(seller("핑크퐁 아기상어 40 페이스 장우산 6K IUPFU10008", "우산", null), UMBRELLA_2015));
        // 같은 이름이라도 KC 번호가 다르면 의심(이름 100% + KC 다름 규칙), 일치는 아니다
        Recall withCert = recall(10099003L, "아동용섬유제품", "-", "레이니 아동우비", "CB014R999-1001", "㈜오픈한",
                "어린이>아동용 섬유제품", "20210225");
        assertEquals(Decision.PARTIAL, top(seller("오즈키즈 아동용 레이니 우비", "레이니 아동우비", "CB014R111-2002"), withCert));
    }

    ExtractedProduct withPool(ExtractedProduct p, Recall... candidates) {
        pool = List.of(candidates);
        return p;
    }

    @Test
    void L06_옵션_조각_판별() {
        assertTrue(ListingTextCleaner.isOptionOnly("혼합색상"));
        assertTrue(ListingTextCleaner.isOptionOnly(" 1개 "));
        assertTrue(ListingTextCleaner.isOptionOnly("랜덤발송 2개"));
        assertTrue(ListingTextCleaner.isOptionOnly("Free"));
        assertTrue(ListingTextCleaner.isOptionOnly("100g"));
        assertFalse(ListingTextCleaner.isOptionOnly("(제품명) 어린이 빈티지 캠핑 의자"));
        assertFalse(ListingTextCleaner.isOptionOnly("VDECX01"));
        assertFalse(ListingTextCleaner.isOptionOnly("허니 슬라임"));
    }
}
