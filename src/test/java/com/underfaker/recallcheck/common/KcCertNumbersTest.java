package com.underfaker.recallcheck.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** KC 인증번호 추출 — 입력값은 전부 리콜 공표문 cert_num 칸 실측값(10/3)과 그 변형이다. */
class KcCertNumbersTest {

    @Test
    void 실측_번호_모양을_전부_뽑는다() {
        for (String n : List.of("HH07952-13056", "CB063R423-5001", "CB015R0261-6001", "HH07975-11004C",
                "B364R970-2001", "HH071257-13008", "JH07132-3001", "B321R051-18001", "B361R3583-1003",
                "XL090008-16192", "CB063R10802-3001", "CB067R2225-4001",
                "B06AH030-9006A", "B361H400-4003CH")) {
            assertEquals(List.of(n), KcCertNumbers.extract(n), n);
        }
    }

    @Test
    void 번호가_아닌_값은_빈_목록() {
        for (String v : List.of("-", "공급자적합성", "비대상", "안전품질표시", "해당없음", ":", "-)",
                "BLOCK", "MSIP", "GRAB16-EM001332", "-EM005642", "MSIP-REI-ABC-123",
                "R-R-Mk8-BTC-E200", "T2320: JPTUV-144010",
                // 10/3 리뷰에서 나온 오탐 — 다른 인증번호의 뒷부분을 잘라 오면 안 된다
                "R-C-K9W-PR100-123", "MSIP-CRM-ABC-HW12-100", "KCCB131R230-0001", "S234-24-01302", "AB12-345")) {
            assertTrue(KcCertNumbers.extract(v).isEmpty(), v);
        }
        assertTrue(KcCertNumbers.extract(null).isEmpty());
        assertTrue(KcCertNumbers.extract("   ").isEmpty());
    }

    @Test
    void 일련번호가_없는_앞부분만으로는_조회하지_않는다() {
        // KC 목록 API 는 앞부분 일치라 "CB067R2225" 로 다른 번호가 걸린다(10/3 실측)
        assertTrue(KcCertNumbers.extract("CB067R2225").isEmpty());
    }

    @Test
    void 여러_개를_이은_값은_나눠서_최대_3개() {
        assertEquals(List.of("B351A030-1001", "B351A030-1002", "B351A030-1003"),
                KcCertNumbers.extract("B351A030-1001,B351A030-1002,B351A030-1003"));
        assertEquals(3, KcCertNumbers.extract("A11-1001, A11-1002 / A11-1003; A11-1004").size());
        assertEquals(List.of("HH07952-13056"), KcCertNumbers.extract("HH07952-13056, HH07952-13056"));
    }

    @Test
    void html_섞인_값과_설명문에서_번호만_뽑는다() {
        assertEquals(List.of("CB131R230-0001"), KcCertNumbers.extract("CB131R230-0001<br>(인증모델: 꼬마버스)"));
        assertEquals(List.of("XL090008-16192"), KcCertNumbers.extract("<br>XL090008-16192"));
        assertEquals(List.of("HH07952-13056"), KcCertNumbers.extract("KC 인증번호: HH07952-13056 (어린이제품)"));
        assertEquals(List.of("B403R111-7010", "B403R5032-18002"), KcCertNumbers.extract("①B403R111-7010, ②B403R5032-18002"));
        assertEquals(List.of("CB064A3166-2004CHC"), KcCertNumbers.extract("cb064a3166-2004chC"));
    }

    @Test
    void 소문자_공백_전각_대시를_정리한다() {
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("cb067r2225-4001"));
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("CB067R2225 - 4001"));
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("CB067R2225–4001"));   // en dash
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("ＣＢ０６７Ｒ２２２５－４００１")); // 전각
    }

    @Test
    void 번호_앞의_KC_접두어는_떼고_뽑는다() {
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("KC-CB067R2225-4001"));
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("kc: CB067R2225-4001"));
        assertEquals(List.of("CB067R2225-4001"), KcCertNumbers.extract("KC CB067R2225-4001"));
        assertEquals(List.of("CB063R423-5001"), KcCertNumbers.extract("인증번호-CB063R423-5001"));
        assertEquals(List.of("CB063R423-5001"), KcCertNumbers.extract("인증번호 - CB063R423-5001"));
        assertEquals(List.of("CB063R423-5001"), KcCertNumbers.extract("(안전확인)-CB063R423-5001"));
    }

    @Test
    void first_는_첫_번호_없으면_null() {
        assertEquals("A11-1001", KcCertNumbers.first("x A11-1001 A11-1002"));
        assertNull(KcCertNumbers.first("공급자적합성"));
    }
}
