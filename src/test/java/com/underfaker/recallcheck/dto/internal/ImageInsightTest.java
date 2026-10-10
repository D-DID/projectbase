package com.underfaker.recallcheck.dto.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/10 — 판정에는 여러 사이트에서 반복된 값(상품명·KC 번호·모델명)만 쓴다. */
class ImageInsightTest {

    @Test
    void 추정_이름과_개체명만_있으면_쓰지_않는다() {
        // 9/27 슬라임 실측 — bestGuess 'honeybee'. 반복 근거가 없는 이름이라 판정에 넣지 않는다
        ImageInsight guessOnly = new ImageInsight("honeybee", List.of("Western honey bee"), 0.8, 10,
                List.of(), null, null, null);
        assertFalse(guessOnly.isUsable());
        assertTrue(guessOnly.textCandidates().isEmpty());
        assertEquals(null, guessOnly.toRawTextBlock());
    }

    @Test
    void 반복된_상품명_KC_번호_모델명은_저장했다가_그대로_되살린다() {
        ImageInsight insight = new ImageInsight("toy", List.of(), 0.5, 15, List.of("토이천국 빠방 밀대"),
                "토이천국 빠방 밀대", "CB063R423-5001", "TC-2024");
        assertEquals(List.of("토이천국 빠방 밀대"), insight.textCandidates());

        ImageInsight back = ImageInsight.fromRawText("[detail]\nkc: REFERENCED\n[/detail]\n" + insight.toRawTextBlock());
        assertEquals("토이천국 빠방 밀대", back.productName());
        assertEquals("CB063R423-5001", back.certNum());
        assertEquals("TC-2024", back.modelName());
        assertTrue(back.isUsable());
    }

    @Test
    void KC_번호만_반복돼도_쓸_수_있고_10월7일_블록도_읽힌다() {
        ImageInsight certOnly = new ImageInsight(null, List.of(), 0.0, 4, List.of(), null, "CB063R423-5001", null);
        assertTrue(certOnly.isUsable());
        assertTrue(certOnly.textCandidates().isEmpty());

        ImageInsight legacy = ImageInsight.fromRawText(
                "[vision]\nbest: toy\ntop: 0.5\npages: 3\ntitle: 퍼기 큐피드 손목 치발기 세트\nproduct: 퍼기 큐피드 손목 치발기 세트\n[/vision]");
        assertEquals("퍼기 큐피드 손목 치발기 세트", legacy.productName());
        assertEquals(null, legacy.certNum());

        // 10/9 이전에 추정 이름만으로 저장된 블록은 이제 '못 찾음'과 같다
        assertFalse(ImageInsight.fromRawText("[vision]\nbest: honeybee\ntop: 0.8\npages: 10\n[/vision]").isUsable());
    }
}
