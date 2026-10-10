package com.underfaker.recallcheck.service;

import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** 10/10 최종 흐름 — 판단 범위(해외직구·구매대행)와 KC 번호 모으기 규칙. Spring 컨텍스트 없이 정적 메서드만 본다. */
class VerificationScopeRulesTest {

    static ManualInputRequest coupang(String title, String certNum, String kcText) {
        return new ManualInputRequest(title, null, null, null, null, certNum,
                "https://thumbnail.coupangcdn.com/a.jpg", certNum == null ? "REFERENCED" : "DISCLOSED", kcText);
    }

    @Test
    void 어린이제품이_아닌_해외직구_구매대행만_판단에서_뺀다() {
        assertEquals("해외직구", VerificationService.excludedOverseas(coupang("[해외직구] 블루투스 스피커", null, "해당없음")));
        // 어린이제품은 구매대행도 KC 표시가 없으면 금지(어린이제품법 제30조) — 판단한다
        assertNull(VerificationService.excludedOverseas(coupang("[해외직구] 아기 치발기 세트", null, "상세페이지 참조")));
        assertNull(VerificationService.excludedOverseas(coupang("구매대행 식판", null,
                "구매대행 · 카테고리상 어린이제품(출산/유아동)인데 번호 없음")));
        // 국내 판매
        assertNull(VerificationService.excludedOverseas(coupang("블루투스 스피커", "HU07123-12345", null)));
    }

    @Test
    void KC_칸에_새_번호가_있을_때만_인증번호를_바꾼다() {
        ManualInputRequest same = coupang("밀대", "CB063R423-5001", "CB063R423-5001");
        assertSame(same, VerificationService.withAllCertNumbers(same));   // 원문 그대로

        ManualInputRequest more = coupang("밀대", "CB063R423-5001", "CB063R423-5001 / B364R970-2001 (리뉴얼)");
        assertEquals("CB063R423-5001, B364R970-2001", VerificationService.withAllCertNumbers(more).certNum());

        ManualInputRequest web = new ManualInputRequest("밀대", null, null, null, null,
                "KC 인증번호: CB063R423-5001", null, null, null);
        assertSame(web, VerificationService.withAllCertNumbers(web));      // 웹 입력 원문도 그대로 둔다
    }
}
