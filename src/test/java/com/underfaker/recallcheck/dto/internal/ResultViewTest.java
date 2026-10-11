package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/10 최종 흐름 — 1차(KC 번호 + 제품명)로 판단할 수 없을 때만 사진 찾기, 해외직구·구매대행은 판단 제외. */
class ResultViewTest {

    static final String THUMB = "https://thumbnail.coupangcdn.com/a.jpg";

    static String detail(String kc, String kcText) {
        return ExtractionNotes.detailBlock(kc, kcText);
    }

    @Test
    void 웹_직접입력에서_KC_번호를_안_넣으면_KC_번호_미표기와_사진_찾기() {
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.WEB, null, THUMB, null);
        assertEquals("MISSING", v.resultState());
        assertTrue(v.imageCheckAvailable());
        assertTrue(v.missingReason().startsWith("KC 인증번호(예: CB063R423-5001 형식)가 입력되지 않아"));

        // 번호가 아닌 글자만 넣어도 번호 없음
        assertEquals("MISSING", ResultView.of(FinalResult.NO_MATCH, VerificationChannel.WEB, null, THUMB, "없음").resultState());
    }

    @Test
    void 웹_직접입력에서_KC_번호를_넣었으면_1차로_판단한_것이라_사진_찾기를_안_연다() {
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.WEB, null, THUMB, "CB063R423-5001");
        assertEquals("NO_MATCH", v.resultState());
        assertFalse(v.imageCheckAvailable());
        assertNull(v.missingReason());
    }

    @Test
    void 쿠팡_KC_칸에_번호가_없으면_KC_번호_미표기() {
        String raw = detail("REFERENCED", "상품 상세페이지 참조");
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.EXTENSION, raw, THUMB, null);
        assertEquals("MISSING", v.resultState());
        assertTrue(v.imageCheckAvailable());
        assertTrue(v.missingReason().contains("상품 상세페이지 참조"));

        // 의심이어도 KC 번호가 없으면 사진 찾기는 연다(상태는 의심 그대로)
        ResultView partial = ResultView.of(FinalResult.PARTIAL, VerificationChannel.EXTENSION, raw, THUMB, null);
        assertEquals("PARTIAL", partial.resultState());
        assertTrue(partial.imageCheckAvailable());
    }

    @Test
    void 확장이_놓쳐도_서버가_KC_칸에서_번호를_찾았으면_미표기가_아니다() {
        String raw = detail("REFERENCED", "XL090008-16192 외");
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.EXTENSION, raw, THUMB, "XL090008-16192");
        assertEquals("NO_MATCH", v.resultState());
        assertFalse(v.imageCheckAvailable());
    }

    @Test
    void 적힌_번호가_KC_인증_DB에_없으면_판정은_두고_사진_찾기만_연다() {
        String raw = detail("DISCLOSED", "CB000X000-0000") + "\n" + KcLookup.notFound("CB063R423-5001").toBlock();
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.EXTENSION, raw, THUMB, "CB063R423-5001");
        assertEquals("NO_MATCH", v.resultState());
        assertTrue(v.imageCheckAvailable());
    }

    @Test
    void 해외직구_구매대행은_판단_제외() {
        String raw = detail("REFERENCED", "해외구매대행 상품") + "\n" + ExtractionNotes.overseasBlock("해외구매대행");
        ResultView v = ResultView.of(FinalResult.UNKNOWN, VerificationChannel.EXTENSION, raw, THUMB, null);
        assertEquals("EXCLUDED", v.resultState());
        assertFalse(v.imageCheckAvailable());
        assertTrue(v.missingReason().contains("해외구매대행"));
    }

    @Test
    void 사진_찾기로_얻은_상품명_KC_번호_모델명을_내려준다() {
        ImageInsight insight = new ImageInsight(null, List.of(), 0.0, 5, List.of("빠방 밀대"), "빠방 밀대",
                "CB063R423-5001", "TC-2024");
        String raw = detail("REFERENCED", "상세페이지 참조") + "\n" + insight.toRawTextBlock() + "\n"
                + ExtractionNotes.imageCheckBlock(ExtractionNotes.ImageCheck.FOUND);
        ResultView v = ResultView.of(FinalResult.PARTIAL, VerificationChannel.EXTENSION, raw, THUMB, null);
        assertEquals("PARTIAL", v.resultState());
        assertFalse(v.imageCheckAvailable());   // 이미 눌렀다
        assertEquals("빠방 밀대", v.imageProductName());
        assertEquals("CB063R423-5001", v.imageCertNum());
        assertEquals("TC-2024", v.imageModelName());
    }

    @Test
    void 해외직구_구매대행이어도_어린이제품이면_판단하고_제30조를_안내한다() {
        // 10/10 — 어린이제품은 구매대행도 KC 표시가 없으면 금지(어린이제품법 제30조). 판단 제외가 아니다
        String raw = detail("REFERENCED", "해외구매대행 상품") + "\n" + ExtractionNotes.overseasChildBlock("해외구매대행");
        ResultView v = ResultView.of(FinalResult.NO_MATCH, VerificationChannel.EXTENSION, raw, THUMB, null);
        assertEquals("MISSING", v.resultState());
        assertTrue(v.imageCheckAvailable());
        assertTrue(v.missingReason().contains("어린이제품 안전 특별법 제30조"));
    }
}
