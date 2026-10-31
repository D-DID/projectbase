package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.entity.enums.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 10/31 팀장 결정 — 2단계 판정.
 * 일치 = KC 인증번호 완전일치 + 제품명 50% 이상, 또는 품번(모델명)·인증 모델명 완전일치.
 * KC 번호만 같고 제품명이 다르거나, 제품명이 거의 같은데 KC 번호가 다르면 의심.
 */
class DecisionResolverTest {

    private final DecisionResolver resolver = new DecisionResolver(new MatchingProperties());

    private static FieldComparison row(String field, double score) {
        return new FieldComparison(field, "입력", "공표문", score, 0.2);
    }

    @Test
    void KC번호_같고_제품명도_비슷하면_일치() {
        assertEquals(Decision.MATCH, resolver.resolve(0.8, MatchProfile.UNIDENTIFIED,
                List.of(row("certNum", 1.0), row("productName", 0.73))));
    }

    @Test
    void KC번호만_같고_제품명이_다르면_의심() {
        List<FieldComparison> rows = List.of(row("certNum", 1.0), row("productName", 0.2));
        assertEquals(Decision.PARTIAL, resolver.resolve(0.4, MatchProfile.UNIDENTIFIED, rows));
        assertNotNull(resolver.note(rows));
    }

    @Test
    void KC번호만_넣고_제품명이_없으면_의심() {
        assertEquals(Decision.PARTIAL, resolver.resolve(1.0, MatchProfile.IDENTIFIED, List.of(row("certNum", 1.0))));
    }

    @Test
    void 품번이_같으면_이름과_상관없이_일치() {
        assertEquals(Decision.MATCH, resolver.resolve(0.6, MatchProfile.IDENTIFIED,
                List.of(row("modelName", 1.0), row("productName", 0.1))));
        assertNull(resolver.note(List.of(row("modelName", 1.0), row("productName", 0.1))));
    }

    @Test
    void 품번은_같은데_KC번호가_다르면_의심() {
        assertEquals(Decision.PARTIAL, resolver.resolve(0.6, MatchProfile.IDENTIFIED,
                List.of(row("modelName", 1.0), row("certNum", 0.0))));
    }

    @Test
    void 제품명이_거의_같은데_KC번호가_다르면_의심() {
        List<FieldComparison> rows = List.of(row("productName", 0.95), row("certNum", 0.0));
        assertEquals(Decision.PARTIAL, resolver.resolve(0.3, MatchProfile.IDENTIFIED, rows));
        assertNotNull(resolver.note(rows));
    }

    @Test
    void 제품명이_조금_비슷하고_KC번호가_다르면_점수대로() {
        // 이름 73% + KC 다름 — 50% 기준으로 올리면 정상 제품마다 다른 공표문이 의심이 된다(자기 대조 6.2건/제품)
        assertEquals(Decision.NO_MATCH, resolver.resolve(0.51, MatchProfile.UNIDENTIFIED,
                List.of(row("productName", 0.73), row("certNum", 0.0))));
    }

    @Test
    void 텍스트만_전부_100퍼센트면_의심() {
        assertEquals(Decision.PARTIAL, resolver.resolve(1.0, MatchProfile.UNIDENTIFIED,
                List.of(row("productName", 1.0), row("makerName", 1.0), row("brandName", 1.0))));
    }

    @Test
    void 사진으로_찾은_인증번호는_일치_근거가_아니다() {
        assertEquals(Decision.PARTIAL, resolver.resolve(1.0, MatchProfile.IDENTIFIED,
                List.of(row("imageCertNum", 1.0), row("productName", 1.0))));
    }

    @Test
    void 가중치_0인_행은_보지_않는다() {
        FieldComparison unused = new FieldComparison("certNum", "a", "a", 1.0, 0.0);
        assertEquals(Decision.NO_MATCH, resolver.resolve(0.1, MatchProfile.IDENTIFIED, List.of(unused)));
    }
}
