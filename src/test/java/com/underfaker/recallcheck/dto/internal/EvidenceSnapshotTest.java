package com.underfaker.recallcheck.dto.internal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/31 — 판정 근거 스냅샷([evidence] 블록) */
class EvidenceSnapshotTest {

    private static final List<FieldComparison> ROWS = List.of(
            new FieldComparison("certNum", "KC 인증번호: CB067R2225-4001\t(어린이)", "CB067R2225-4001", 1.0, 0.25),
            new FieldComparison("productName", "핑크풋 슬라임\n대용량", null, 0.62, 0.15));

    @Test
    void 저장한_값을_그대로_되살린다() {
        String raw = "[detail]\nkc: DISCLOSED\n[/detail]\n" + ExtractionNotes.evidenceBlock(10023006L, ROWS);
        ExtractionNotes.EvidenceSnapshot s = ExtractionNotes.evidence(raw);
        assertEquals(10023006L, s.recallUid());
        assertEquals(2, s.rows().size());
        assertEquals("certNum", s.rows().get(0).field());
        assertEquals("KC 인증번호: CB067R2225-4001 (어린이)", s.rows().get(0).inputValue()); // 탭은 공백으로
        assertEquals(1.0, s.rows().get(0).score(), 1e-9);
        assertEquals(0.25, s.rows().get(0).weight(), 1e-9);
        assertEquals("핑크풋 슬라임 대용량", s.rows().get(1).inputValue());
        assertNull(s.rows().get(1).officialValue());
        assertEquals("DISCLOSED", ExtractionNotes.kcStatusName(raw)); // 다른 블록은 그대로
    }

    @Test
    void 새_스냅샷으로_바꾸면_예전_것은_지워진다() {
        String raw = "[detail]\nkc: DISCLOSED\n[/detail]\n" + ExtractionNotes.evidenceBlock(1L, ROWS);
        String cleared = ExtractionNotes.withoutBlocks(raw, ExtractionNotes.EVIDENCE_BEGIN, ExtractionNotes.EVIDENCE_END);
        assertNull(ExtractionNotes.evidence(cleared));
        assertTrue(cleared.contains("[detail]"));
        String next = cleared + "\n" + ExtractionNotes.evidenceBlock(2L, ROWS.subList(0, 1));
        assertEquals(2L, ExtractionNotes.evidence(next).recallUid());
        assertEquals(1, ExtractionNotes.evidence(next).rows().size());
    }

    @Test
    void 후보가_없으면_블록을_만들지_않는다() {
        assertNull(ExtractionNotes.evidenceBlock(null, ROWS));
        assertNull(ExtractionNotes.evidenceBlock(1L, List.of()));
        assertNull(ExtractionNotes.evidence(null));
        assertNull(ExtractionNotes.evidence("[evidence]\nrecall: x\n[/evidence]"));
    }
}
