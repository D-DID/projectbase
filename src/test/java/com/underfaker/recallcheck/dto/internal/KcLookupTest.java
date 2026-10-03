package com.underfaker.recallcheck.dto.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KC 조회 결과의 빈 항목 채우기와 raw_text 블록 저장·복원. 값은 10/3 KC API 실측 응답. */
class KcLookupTest {

    /** 10/3 실측: certificationList.json?conditionKey=certNum&conditionValue=CB067R2225-4001 */
    static final KcLookup PINK_FOOT = new KcLookup(KcLookup.Status.FOUND, "CB067R2225-4001", "적합", "20240419",
            "완구", "", "핑크풋 슬라임", "-", "중국", "API");

    static ExtractedProduct product(String productName, String modelName, String makerName, String certNum) {
        return new ExtractedProduct(productName, null, modelName, makerName, null, certNum, null, null, 1.0);
    }

    @Test
    void 비어있는_모델명을_채운다_제조사_하이픈은_채우지_않는다() {
        ExtractedProduct in = product("핑크풋 슬라임 대용량 3종", null, null, "CB067R2225-4001");
        ExtractedProduct out = PINK_FOOT.fillBlanks(in);
        assertEquals("핑크풋 슬라임", out.modelName());
        assertNull(out.makerName());                       // "-" 는 값이 아니다
        assertNull(out.brandName());                       // "" 도 값이 아니다
        assertEquals("핑크풋 슬라임 대용량 3종", out.productName()); // 품목명 "완구"로 덮지 않는다
        assertEquals("CB067R2225-4001", out.certNum());
        assertEquals("모델명", PINK_FOOT.filledFields(in));
    }

    @Test
    void 이미_입력한_값은_덮지_않는다() {
        ExtractedProduct in = product("슬라임", "PF-100", "핑크풋", "CB067R2225-4001");
        ExtractedProduct out = PINK_FOOT.fillBlanks(in);
        assertEquals("PF-100", out.modelName());
        assertEquals("핑크풋", out.makerName());
        assertEquals("", PINK_FOOT.filledFields(in));
    }

    @Test
    void FOUND_가_아니면_입력_그대로() {
        ExtractedProduct in = product("슬라임", null, null, "KC 인증번호: XX00000-00000");
        assertSame(in, KcLookup.notFound("XX00000-00000").fillBlanks(in));
        assertSame(in, KcLookup.unavailable("XX00000-00000").fillBlanks(in));
    }

    @Test
    void 번호가_하나면_정리된_번호로_바꾼다() {
        ExtractedProduct messy = product("핑크풋 슬라임", null, null, "KC 인증번호: CB067R2225-4001 (어린이제품)");
        assertEquals("CB067R2225-4001", PINK_FOOT.fillBlanks(messy).certNum());
    }

    @Test
    void 번호가_여럿이면_원문을_둔다() {
        String raw = "CB067R2225-4001, CB067R2225-4002";
        assertEquals(raw, PINK_FOOT.fillBlanks(product("슬라임", null, null, raw)).certNum());
    }

    @Test
    void 블록으로_저장했다가_그대로_복원한다() {
        String raw = "[detail]\nkc: DISCLOSED\n[/detail]\n" + PINK_FOOT.toBlock();
        KcLookup back = KcLookup.fromRawText(raw);
        assertEquals(KcLookup.Status.FOUND, back.status());
        assertEquals("CB067R2225-4001", back.certNum());
        assertEquals("적합", back.certState());
        assertEquals("20240419", back.certDate());
        assertEquals("핑크풋 슬라임", back.modelName());
        assertEquals("-", back.makerName());
        assertEquals("중국", back.makerCntryName());
        assertEquals("API", back.source());
        assertNull(back.brandName());   // 빈 값은 블록에 안 쓴다
    }

    @Test
    void 블록이_여럿이면_마지막_것을_쓴다_없으면_null() {
        String raw = KcLookup.notFound("A11-1001").toBlock() + "\n" + PINK_FOOT.toBlock();
        assertEquals(KcLookup.Status.FOUND, KcLookup.fromRawText(raw).status());
        assertNull(KcLookup.fromRawText("[detail]\nkc: NONE\n[/detail]"));
        assertNull(KcLookup.fromRawText(null));
        assertNull(KcLookup.fromRawText("[kc-lookup]\nresult: 이상한값\n[/kc-lookup]"));
    }

    @Test
    void 판정근거_재계산용_applyFromRawText() {
        ExtractedProduct merged = new ExtractedProduct("슬라임", null, null, null, null, "CB067R2225-4001",
                null, "[detail]\nkc: DISCLOSED\n[/detail]\n" + PINK_FOOT.toBlock(), 1.0);
        assertEquals("핑크풋 슬라임", KcLookup.applyFromRawText(merged).modelName());
        ExtractedProduct noBlock = product("슬라임", null, null, null);
        assertSame(noBlock, KcLookup.applyFromRawText(noBlock));
    }

    @Test
    void 인증상태_주의_표시() {
        assertFalse(PINK_FOOT.stateNeedsAttention());
        KcLookup expired = new KcLookup(KcLookup.Status.FOUND, "B361R3583-1003", "기간만료", "20110826",
                "완구", null, "테디웨딩베어", "-", "중국", "API");
        assertTrue(expired.stateNeedsAttention());
        assertFalse(KcLookup.notFound("A11-1001").stateNeedsAttention());
    }

    @Test
    void 판정_때_값과_블록에서_되살린_값이_같다_255자까지() {
        // 판정근거 재계산이 판정과 어긋나지 않으려면 certification 칸 최대(255)까지 그대로 돌아와야 한다(10/3 리뷰)
        String longModel = "모델".repeat(127);   // 254자
        KcLookup kc = new KcLookup(KcLookup.Status.FOUND, "A11-1001", "적합", null, null, null,
                longModel, null, null, "API");
        assertEquals(longModel, KcLookup.fromRawText(kc.toBlock()).modelName());
    }

    @Test
    void 값_한줄_255자_제한_줄바꿈_제거() {
        KcLookup messy = new KcLookup(KcLookup.Status.FOUND, "A11-1001", "적합", null, null, null,
                "모델\n두줄" + "x".repeat(300), null, null, "API");
        KcLookup back = KcLookup.fromRawText(messy.toBlock());
        assertEquals(255, back.modelName().length());
        assertTrue(back.modelName().startsWith("모델 두줄"));
    }
}
