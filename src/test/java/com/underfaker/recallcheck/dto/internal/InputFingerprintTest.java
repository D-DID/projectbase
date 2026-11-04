package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.entity.Extraction;
import com.underfaker.recallcheck.entity.enums.SourceType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/31 — 검증 이력 중복 정리의 '같은 입력' 판별 */
class InputFingerprintTest {

    private static ManualInputRequest req(String name, String cert, String thumb, String kcStatus, String kcText) {
        return new ManualInputRequest(name, null, null, "애플비북스", null, cert, thumb, kcStatus, kcText);
    }

    /** 저장된 MANUAL 행 — ExtractionService.extractFromManualInput 과 같은 방식으로 만든다 */
    private static Extraction saved(ManualInputRequest r) {
        return Extraction.builder()
                .verificationId(1L)
                .source(SourceType.MANUAL)
                .productName(r.productName()).brandName(r.brandName()).modelName(r.modelName())
                .makerName(r.makerName()).barcodeNum(r.barcodeNum()).certNum(r.certNum())
                .thumbnailUrl(r.thumbnailUrl())
                .rawText(ExtractionNotes.detailBlock(r.kcStatus(), r.kcText()))
                .confidence(1.0)
                .build();
    }

    @Test
    void 저장한_입력과_같은_요청은_같다() {
        ManualInputRequest r = req("바스락바스락 아기 헝겊책 2종 세트", "CB064R1376-8002A", "https://a/1.jpg",
                "DISCLOSED", "CB064R1376-8002A");
        assertTrue(InputFingerprint.same(InputFingerprint.of(r), InputFingerprint.of(saved(r))));
    }

    @Test
    void 사진_주소만_다르면_같은_입력() {
        ManualInputRequest a = req("헝겊책", "CB064R1376-8002A", "https://a/230x230ex/1.jpg", "DISCLOSED", null);
        ManualInputRequest b = req("헝겊책", "CB064R1376-8002A", "https://a/492x492ex/1.jpg", "DISCLOSED", null);
        assertTrue(InputFingerprint.same(InputFingerprint.of(b), InputFingerprint.of(saved(a))));
    }

    @Test
    void 공백_대소문자_차이는_같은_입력() {
        ManualInputRequest a = req("헝겊책  2종 ", "cb064r1376-8002a", null, null, null);
        ManualInputRequest b = req(" 헝겊책 2종", "CB064R1376-8002A", null, null, null);
        assertTrue(InputFingerprint.same(InputFingerprint.of(b), InputFingerprint.of(saved(a))));
    }

    @Test
    void KC_번호가_다르면_다른_입력() {
        ManualInputRequest a = req("헝겊책", "CB064R1376-8002A", null, null, null);
        ManualInputRequest b = req("헝겊책", "CB064R1376-8003A", null, null, null);
        assertFalse(InputFingerprint.same(InputFingerprint.of(b), InputFingerprint.of(saved(a))));
    }

    @Test
    void KC_칸_상태가_달라지면_다른_입력() {
        // 예전 스캔은 못 읽음(UNREADABLE), 이번엔 번호를 읽음 — 다시 판정해야 한다
        ManualInputRequest a = req("헝겊책", null, null, "UNREADABLE", null);
        ManualInputRequest b = req("헝겊책", null, null, "DISCLOSED", "CB064R1376-8002A");
        assertFalse(InputFingerprint.same(InputFingerprint.of(b), InputFingerprint.of(saved(a))));
    }

    @Test
    void 빈_입력은_묶지_않는다() {
        assertTrue(InputFingerprint.of(new ManualInputRequest(null, null, null, null, null, null, "https://a/1.jpg",
                null, null)).isBlank());
    }
}
