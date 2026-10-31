package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.config.MatchingProperties;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.entity.enums.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 10/31 팀장 결정 — '일치'는 식별자(인증번호·모델명·인증 모델명) 완전일치일 때만.
 * 제품명·제조사·브랜드 같은 텍스트가 전부 100% 여도 '의심'까지만.
 */
class DecisionResolverTest {

    private final DecisionResolver resolver = new DecisionResolver(new MatchingProperties());

    private static FieldComparison row(String field, double score) {
        return new FieldComparison(field, "입력", "공표문", score, 0.2);
    }

    @Test
    void 제품명만_100퍼센트면_의심() {
        assertEquals(Decision.PARTIAL,
                resolver.resolve(1.0, MatchProfile.UNIDENTIFIED, List.of(row("productName", 1.0))));
    }

    @Test
    void 텍스트_항목이_전부_100퍼센트여도_식별자가_없으면_의심() {
        assertEquals(Decision.PARTIAL, resolver.resolve(1.0, MatchProfile.UNIDENTIFIED,
                List.of(row("productName", 1.0), row("makerName", 1.0), row("brandName", 1.0))));
    }

    @Test
    void 인증번호_완전일치면_일치() {
        assertEquals(Decision.MATCH, resolver.resolve(0.4, MatchProfile.IDENTIFIED,
                List.of(row("productName", 0.2), row("certNum", 1.0))));
    }

    @Test
    void 모델명_완전일치면_일치() {
        assertEquals(Decision.MATCH, resolver.resolve(0.6, MatchProfile.IDENTIFIED,
                List.of(row("modelName", 1.0))));
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
