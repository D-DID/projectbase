package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.common.ChildProductSignal;
import com.underfaker.recallcheck.common.OverseasListing;
import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.ExtractionNotes;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.ImageInsight;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.dto.internal.ResultView;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.enums.Decision;
import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.service.sync.RecallQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 통합테스트 엣지케이스 20개 (10/30) — 노션 '통합테스트 케이스 작성(엣지케이스 20개)' 항목.
 * 케이스 설명·기대 결과 표는 docs/edge-cases.md. 번호(E01~E20)가 서로 맞는다.
 *
 * 리콜 공표문은 전부 db/recall-dump.sql 의 실제 행(recall_uid 표기)을 필요한 칸만 옮겼다.
 * 후보조회(RecallQueryService)는 DB 없이 돌리려고 고정 목록을 돌려주는 대역으로 바꾸고,
 * 판정·정렬은 실제 MatchingService.match 경로를 그대로 탄다(저장소는 Mockito 대역).
 */
class EdgeCaseScenariosTest {

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

    // ── 실제 공표문(db/recall-dump.sql) ──
    static Recall recall(long uid, String product, String brand, String model, String cert, String company, String category) {
        return Recall.builder().recallUid(uid).recallProductName(product).recallBrandName(brand)
                .recallModelName(model).certNum(cert).recallCmpnyName(company).categoryName(category)
                .publishDate("20260723").build();
    }

    static final Recall CHESS = recall(10022634L, "게임완구(완구)", "-", "원목체스", "CB067R227-2006",
            "주식회사 바니랜드", "어린이>완구");
    static final Recall TRAIN = recall(10022642L, "모형완구(완구)", "제우스", "기차놀이", "CB067R317-5002",
            "(주) 제우스", "어린이>완구");
    static final Recall BUBBLE = recall(10022637L, "기타완구(완구)", "HAPPY FAIENDS", "비눗방울", "CB065R2807-5003",
            "해피프랜즈아이앤디", "어린이>완구");
    static final Recall JACKET = recall(10022559L, "중의류(아섬)(아동용 섬유제품)", "리틀뎁", "(품번) VDECX01", "-",
            "부성상사", "어린이>아동용 섬유제품");
    static final Recall SLIME = recall(10023005L, "기타완구(완구)", null, "(제품명) 허니 슬라임", "CB065R2649-4001",
            "리틀아이", "어린이>완구");
    static final Recall SWIMSUIT = recall(10022653L, "중의류(유섬)(유아용 섬유제품)", "-", "(제품명) 베베코지 아기 수영복",
            "CB014R1928-3004", "제이모션 주식회사", "어린이>유아용 섬유제품");
    static final Recall RAINCOAT = recall(10022664L, "외의류(유섬)(유아용 섬유제품)", "로토토베베", "(상품명) 트로피컬레인코트",
            "CB017R1113-5002", "주식회사 성실", "어린이>유아용 섬유제품");
    static final Recall FRIENDS_BUBBLE = recall(10022640L, "기타완구(완구)", "-", "거꾸로 프랜즈버블", "CB064R918-5001",
            "(주)세주", "어린이>완구");
    static final Recall WATER_GUN = recall(10022636L, "기타완구(완구)", "-", "MJ21 워터건 / (제품명) MJ21 압축 워터건",
            "CB064R4597-3001", "오늘", "어린이>완구");

    static ExtractedProduct seller(String productName, String modelName, String makerName, String certNum) {
        return new ExtractedProduct(productName, null, modelName, makerName, null, certNum, null, null, 1.0);
    }

    List<MatchCandidate> match(ExtractedProduct product, Recall... candidates) {
        pool = List.of(candidates);
        return matching.match(1L, product);
    }

    Decision top(ExtractedProduct product, Recall... candidates) {
        List<MatchCandidate> result = match(product, candidates);
        assertFalse(result.isEmpty(), "후보가 있어야 한다");
        return result.get(0).decision();
    }

    static FieldComparison row(MatchCandidate c, String field) {
        return c.comparisons().stream().filter(x -> x.field().equals(field)).findFirst().orElse(null);
    }

    // ── 1차 판단: KC 인증번호 ──

    @Test
    void E01_KC_번호가_공표문과_같으면_일치() {
        assertEquals(Decision.MATCH, top(seller("바니랜드 원목 체스 세트 접이식", null, null, "CB067R227-2006"), CHESS));
    }

    @Test
    void E02_KC_칸에_설명과_소문자가_섞여도_번호만_뽑아_일치() {
        // 10/30 수정 전: 번호가 하나면 원문째 비교해서 의심(인증번호 74%)으로 떨어졌다
        MatchCandidate c = match(seller("원목 체스", null, null, "kc 인증번호 : cb067r227-2006 (어린이제품 안전확인)"), CHESS).get(0);
        assertEquals(Decision.MATCH, c.decision());
        assertEquals(1.0, row(c, "certNum").score(), 1e-9);
    }

    @Test
    void E03_KC_번호가_여러_개면_그중_하나라도_같으면_일치() {
        assertEquals(Decision.MATCH, top(seller("원목 체스", null, null, "CB000R000-0000, CB067R227-2006"), CHESS));
    }

    @Test
    void E04_KC_번호가_한_글자라도_다르면_일치로_올리지_않는다() {
        // 제품명은 100% 같아도 번호가 다르면 '같은 제품' 확정 근거가 없다 → 의심까지
        MatchCandidate c = match(seller("원목체스", null, null, "CB067R227-2007"), CHESS).get(0);
        assertEquals(Decision.PARTIAL, c.decision());
        assertEquals(0.0, row(c, "certNum").score(), 1e-9);
    }

    // ── 표기 다름 ──

    @Test
    void E05_품번은_소문자_하이픈이_달라도_같은_품번이면_일치() {
        assertEquals(Decision.MATCH, top(seller("리틀뎁 아동 바람막이", "vdecx-01", null, null), JACKET));
    }

    @Test
    void E06_공표문_인증번호_칸이_비어_있으면_그_칸은_비교하지_않는다() {
        // 공표문 cert_num "-" — 판매자가 적은 다른 번호 때문에 깎이지 않고 품번으로 판단
        MatchCandidate c = match(seller("리틀뎁 VDECX01 점퍼", "VDECX01", null, "CB111R111-1111"), JACKET).get(0);
        assertNull(row(c, "certNum"));
        assertEquals(Decision.MATCH, c.decision());
    }

    @Test
    void E07_판매글_수식어가_붙은_상품명은_의심까지() {
        MatchCandidate c = match(seller("[무료배송] 리틀아이 허니 슬라임 100g 3개", null, null, null), SLIME).get(0);
        assertEquals(Decision.PARTIAL, c.decision());
    }

    @Test
    void E08_띄어쓰기가_없어도_정규화하면_같은_이름() {
        // 정규화하면 제품명은 100% 같다. 10/31 팀장 결정 — 식별자(KC 번호·품번) 없이 제품명만 같으면
        // '의심'까지(DecisionResolver.hasExactEvidence). 예전에는 일치였다(docs/edge-cases.md '정책 확인').
        MatchCandidate c = match(seller("베베코지아기수영복", null, null, null), SWIMSUIT).get(0);
        assertEquals(1.0, row(c, "productName").score(), 1e-9);
        assertEquals(Decision.PARTIAL, c.decision());
    }

    // ── 리콜 아닌 제품 ──

    @Test
    void E09_무관한_제품은_불일치() {
        assertEquals(Decision.NO_MATCH, top(seller("삼성 갤럭시 버즈 무선 이어폰", null, null, null), CHESS));
    }

    @Test
    void E10_같은_브랜드의_다른_제품은_브랜드만으로_올리지_않는다() {
        MatchCandidate c = match(seller("로토토베베 아기 겨울 우주복 점프슈트", null, null, null), RAINCOAT).get(0);
        assertEquals(1.0, row(c, "brandName").score(), 1e-9);   // 상품명에서 브랜드는 찾았다
        assertEquals(Decision.NO_MATCH, c.decision());
    }

    // ── 후보 여러 개 / 없음 ──

    @Test
    void E11_후보가_여러_개면_번호가_맞는_후보가_맨_위() {
        List<MatchCandidate> result = match(seller("제우스 기차놀이 세트", null, null, "CB067R317-5002"), CHESS, TRAIN, BUBBLE);
        assertEquals(3, result.size());
        assertEquals(10022642L, result.get(0).recallUid());
        assertEquals(Decision.MATCH, result.get(0).decision());
        assertEquals(Decision.NO_MATCH, result.get(1).decision());
        assertEquals(Decision.NO_MATCH, result.get(2).decision());
    }

    @Test
    void E12_리콜_DB에_후보가_없으면_결과도_없다() {
        assertTrue(match(seller("아기 물티슈 100매", null, null, null)).isEmpty());
    }

    // ── 제조사 ──

    @Test
    void E13_제조사는_회사_형태_표기가_달라도_같은_회사() {
        // 10/30 수정 전: "주식회사 세주" vs "(주)세주" = 50%
        MatchCandidate c = match(seller("거꾸로 프랜즈 버블 비눗방울", null, "주식회사 세주", null), FRIENDS_BUBBLE).get(0);
        assertEquals(1.0, row(c, "makerName").score(), 1e-9);
        assertEquals("주식회사 세주", row(c, "makerName").inputValue());   // 근거표에는 원문
    }

    @Test
    void E14_제조사만_같으면_판단하지_않는다() {
        assertEquals(Decision.NO_MATCH, top(seller("비눗방울 장난감", null, "주식회사 세주", null), FRIENDS_BUBBLE));
    }

    @Test
    void E15_공표문_모델명_칸이_슬래시로_이어져_있어도_조각으로_잰다() {
        MatchCandidate c = match(seller("MJ21 압축 워터건 물총", null, null, null), WATER_GUN).get(0);
        assertEquals(Decision.PARTIAL, c.decision());
        assertTrue(row(c, "productName").score() >= 0.85);
    }

    // ── 최종 흐름(10/10) 표시 규칙 ──

    @Test
    void E16_KC_번호를_안_넣으면_KC_번호_미표기와_사진으로_찾기() {
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.WEB, null,
                "https://thumbnail.coupangcdn.com/a.jpg", null);
        assertEquals(ResultView.MISSING, v.resultState());
        assertTrue(v.imageCheckAvailable());
    }

    @Test
    void E17_어린이제품이_아닌_해외직구는_판단_제외() {
        String title = "[해외직구] 블루투스 무선 스피커 방수";
        assertNotNull(OverseasListing.signal(title));
        assertFalse(ChildProductSignal.likely(title));
        String raw = ExtractionNotes.detailBlock("REFERENCED", "해외직구 상품") + "\n" + ExtractionNotes.overseasBlock("해외직구");
        ResultView v = ResultView.of(FinalResult.UNKNOWN, VerificationChannel.EXTENSION, raw, null, null);
        assertEquals(ResultView.EXCLUDED, v.resultState());
        assertFalse(v.imageCheckAvailable());
    }

    @Test
    void E18_해외_구매대행이어도_어린이제품이면_판단하고_제30조를_안내() {
        String title = "해외구매대행 아기 치발기 실리콘";
        assertNotNull(OverseasListing.signal(title));
        assertTrue(ChildProductSignal.likely(title));
        String raw = ExtractionNotes.detailBlock("REFERENCED", "해외구매대행") + "\n" + ExtractionNotes.overseasChildBlock("해외구매대행");
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.EXTENSION, raw,
                "https://thumbnail.coupangcdn.com/a.jpg", null);
        assertNotEquals(ResultView.EXCLUDED, v.resultState());
        assertTrue(v.missingReason().contains("제30조"));
    }

    @Test
    void E19_해외직구_아님처럼_부정하면_국외_판매로_보지_않는다() {
        assertNull(OverseasListing.signal("국내 정품 해외직구 아님 당일발송"));
        assertNull(OverseasListing.signal("[구매대행 NO] 국내 재고"));
    }

    @Test
    void E20_사진_찾기로_KC_번호가_같아도_최대_의심() {
        pool = List.of(CHESS);
        ImageInsight insight = new ImageInsight(null, List.of(), 0.0, 4, List.of(), null, "CB067R227-2006", null);
        MatchCandidate c = matching.matchByImage(1L, seller("체스 보드게임", null, null, null), insight).candidates().get(0);
        assertEquals(Decision.PARTIAL, c.decision());
        assertNotEquals(Decision.MATCH, c.decision());
    }
}
