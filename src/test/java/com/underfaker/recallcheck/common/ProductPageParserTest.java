package com.underfaker.recallcheck.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/31 — FR-005 URL 자동 추출: 상품 페이지 HTML → 제품 식별 정보 */
class ProductPageParserTest {

    private static final String JSON_LD_PAGE = """
            <html><head><title>핑크풋 슬라임 대용량 : 어린이마켓</title>
            <meta property="og:title" content="핑크풋 슬라임 대용량 (og)">
            <meta property="og:image" content="/img/slime.jpg">
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[
              {"@type":"BreadcrumbList","itemListElement":[]},
              {"@type":"Product","name":"핑크풋 슬라임 대용량","brand":{"@type":"Brand","name":"핑크풋"},
               "mpn":"PF-SLIME-500","gtin13":8809318297513,
               "manufacturer":{"@type":"Organization","name":"(주)핑크풋"},
               "image":["https://cdn.example.com/slime-1.jpg","https://cdn.example.com/slime-2.jpg"]}
            ]}
            </script></head><body>
            <table class="notice">
              <tr><th>KC 인증정보</th><td>KC 인증번호: CB067R2225-4001 (어린이제품)</td></tr>
            </table></body></html>
            """;

    @Test
    void JSON_LD_상품정보를_먼저_쓴다() {
        ProductPageParser.Parsed p = ProductPageParser.parse(JSON_LD_PAGE, "https://shop.example.com/goods/1");
        assertEquals("핑크풋 슬라임 대용량", p.productName());
        assertEquals("핑크풋", p.brandName());
        assertEquals("PF-SLIME-500", p.modelName());
        assertEquals("(주)핑크풋", p.makerName());
        assertEquals("8809318297513", p.barcodeNum());          // 숫자 gtin 이 지수 표기로 깨지지 않는다
        assertEquals("https://cdn.example.com/slime-1.jpg", p.thumbnailUrl());
        assertEquals("CB067R2225-4001", p.certNum());
        assertEquals("DISCLOSED", p.kcStatus());
    }

    @Test
    void 고시_표에서_모델명_제조자_KC를_읽는다() {
        String html = """
                <html><head><meta property="og:title" content="바스락바스락 아기 헝겊책 2종 세트 | 베이비스토어">
                <meta property="og:site_name" content="베이비스토어">
                <meta property="og:image" content="//img.example.com/book.jpg"></head><body>
                <table>
                  <tr><th>품명 및 모델명</th><td>헝겊책 / AB-2023</td><th>제조자(수입자)</th><td>애플비북스</td></tr>
                  <tr><th>KC 인증 필 유무</th><td>CB064R1376-8002A</td></tr>
                  <tr><th>인증기관</th><td>KTR</td></tr>
                </table></body></html>
                """;
        ProductPageParser.Parsed p = ProductPageParser.parse(html, "https://www.example.com/p/9");
        assertEquals("바스락바스락 아기 헝겊책 2종 세트", p.productName()); // 사이트 이름 꼬리를 뗀다
        assertEquals("헝겊책 / AB-2023", p.modelName());
        assertEquals("애플비북스", p.makerName());
        assertEquals("CB064R1376-8002A", p.certNum());
        assertEquals("DISCLOSED", p.kcStatus());
        assertEquals("https://img.example.com/book.jpg", p.thumbnailUrl());
    }

    @Test
    void KC_칸에_번호가_없으면_REFERENCED() {
        String html = "<title>아기 치발기</title><dl><dt>KC인증정보</dt><dd>상품 상세페이지 참조</dd></dl>";
        ProductPageParser.Parsed p = ProductPageParser.parse(html, null);
        assertNull(p.certNum());
        assertEquals("REFERENCED", p.kcStatus());
        assertEquals("상품 상세페이지 참조", p.kcText());
    }

    @Test
    void KC_칸이_해당없음이면_NONE() {
        String html = "<title>아기 과자</title><table><tr><th>KC 인증</th><td>해당사항 없음</td></tr></table>";
        assertEquals("NONE", ProductPageParser.parse(html, null).kcStatus());
    }

    @Test
    void KC_칸이_없으면_본문의_라벨_뒤_번호를_찾고_상태는_비워둔다() {
        String html = "<title>원목 체스</title><div>안전인증번호 : CB067R227-2006 확인</div>";
        ProductPageParser.Parsed p = ProductPageParser.parse(html, null);
        assertEquals("CB067R227-2006", p.certNum());
        assertNull(p.kcStatus());
    }

    @Test
    void 깨진_JSON_LD는_무시하고_메타태그를_쓴다() {
        String html = "<script type='application/ld+json'>{\"@type\":\"Product\",\"name\":</script>"
                + "<meta name=\"twitter:title\" content=\"유아 실리콘 턱받이\">";
        assertEquals("유아 실리콘 턱받이", ProductPageParser.parse(html, null).productName());
    }

    @Test
    void 식별정보가_없으면_비어있다() {
        assertTrue(ProductPageParser.parse("<html><body><img src='a.jpg'></body></html>", null).isEmpty());
        assertTrue(ProductPageParser.parse(null, null).isEmpty());
    }

    @Test
    void 닫히지_않은_태그가_많아도_빨리_끝난다() {
        String junk = "<tr<th<td".repeat(40_000);
        long t0 = System.nanoTime();
        ProductPageParser.parse(junk, null);
        assertTrue(System.nanoTime() - t0 < 3_000_000_000L);
    }

    @Test
    void javascript_주소의_사진은_버린다() {
        String html = "<title>장난감</title><meta property=\"og:image\" content=\"javascript:alert(1)\">";
        assertNull(ProductPageParser.parse(html, "https://a.example.com/").thumbnailUrl());
    }
}
