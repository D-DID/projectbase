package com.underfaker.recallcheck.common;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/10 — 사진 찾기(2차) 페이지 본문에서 KC 인증번호·모델명 뽑기와, 여러 사이트에서 반복된 값 고르기. */
class WebPageIdentifiersTest {

    static final String PRODUCT_PAGE = "<html><head><title>빠방 밀대</title>"
            + "<script>var sku='ZZ999-1234';</script><style>.kc{}</style></head><body>"
            + "<table><tr><th>품명 및 모델명</th><td>아이놀이터 빠방 밀대 / TC-2024</td></tr>"
            + "<tr><th>KC 인증정보</th><td>어린이제품 안전확인 CB063R423-5001</td></tr>"
            + "<tr><th>제조국</th><td>중국</td></tr><tr><th>용량</th><td>모델: 500ML</td></tr></table>"
            + "<!-- 추천 상품 B364R970-2001 --></body></html>";

    @Test
    void 라벨_근처의_KC_번호와_모델명만_뽑는다() {
        WebPageIdentifiers.Found f = WebPageIdentifiers.extract(PRODUCT_PAGE);
        assertEquals(List.of("CB063R423-5001"), f.certNums());   // 스크립트·주석 안의 번호는 안 본다
        assertEquals(List.of("TC-2024"), f.modelNames());          // "500ML" 같은 용량은 모델명이 아니다
    }

    @Test
    void 라벨이_없거나_번호가_없으면_빈_결과() {
        assertTrue(WebPageIdentifiers.extract("<p>KC 인증정보: 상품 상세페이지 참조</p>").isEmpty());
        assertTrue(WebPageIdentifiers.extract("<p>관련 상품 CB063R423-5001</p>").certNums().isEmpty());
        assertTrue(WebPageIdentifiers.extract(null).isEmpty());
    }

    @Test
    void 엔티티와_EUC_KR_페이지도_읽는다() throws Exception {
        String html = "<meta charset=\"euc-kr\"><p>KC&nbsp;인증번호&#58; CB063R423-5001</p>";
        byte[] body = html.getBytes(Charset.forName("x-windows-949"));
        String decoded = WebPageIdentifiers.decode(body, "text/html");
        assertEquals(List.of("CB063R423-5001"), WebPageIdentifiers.extract(decoded).certNums());
        assertEquals("가나", WebPageIdentifiers.decode("가나".getBytes("UTF-8"), "text/html; charset=UTF-8"));
    }

    @Test
    void 서로_다른_사이트_두_곳_이상에서_같은_값만_고른다() {
        Map<String, List<String>> bySite = new LinkedHashMap<>();
        bySite.put("sobit.co.kr", List.of("CB063R423-5001", "B364R970-2001"));
        bySite.put("minimalstand.com", List.of("cb063r423-5001"));
        bySite.put("other.com", List.of("HH07952-13056"));
        assertEquals("CB063R423-5001", WebPageIdentifiers.consensus(bySite, 2));

        Map<String, List<String>> single = new LinkedHashMap<>();
        single.put("a.com", List.of("CB063R423-5001", "CB063R423-5001"));   // 한 사이트 안의 반복은 한 번
        single.put("b.com", List.of("B364R970-2001"));
        assertNull(WebPageIdentifiers.consensus(single, 2));
        assertNull(WebPageIdentifiers.consensus(Map.of(), 2));
    }
}
