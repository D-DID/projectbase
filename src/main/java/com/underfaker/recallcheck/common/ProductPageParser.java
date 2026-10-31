package com.underfaker.recallcheck.common;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 10/31 — FR-005 URL 자동 추출: 쇼핑몰 상품 페이지 HTML 에서 제품 식별 정보를 뽑는다. JDK 만 쓴다(단위 테스트용).
 *
 * 읽는 곳(앞이 우선):
 *   1) JSON-LD(schema.org Product) — name · brand · model/mpn · manufacturer · gtin · image
 *   2) 메타 태그 — og:title · og:image · product:brand
 *   3) 상품정보 제공고시 표(&lt;tr&gt;의 th/td, &lt;dl&gt;의 dt/dd) — 품명 및 모델명 · 제조자 · KC 인증정보 등
 *   4) &lt;title&gt;
 * KC 인증번호는 고시 표의 KC 칸에서 먼저 찾고, 없으면 본문에서 'KC·인증번호' 라벨 바로 뒤의 번호를 찾는다
 * (WebPageIdentifiers). KC 칸 상태는 확장과 같은 값(DISCLOSED · REFERENCED · NONE)으로 매긴다 — 칸이 없으면 null.
 *
 * 정규식으로 문서 전체를 훑지 않는다(10/10 리뷰 — 닫히지 않은 '<' 가 많은 문서에서 시간이 제곱으로 늘었다).
 * 태그는 indexOf 로 찾는다. 예외를 던지지 않는다 — 못 찾은 칸은 null.
 */
public final class ProductPageParser {

    private ProductPageParser() {
    }

    /** 문서가 이보다 길면 앞부분만 본다(상품 정보는 앞쪽에 있다) */
    static final int MAX_HTML_CHARS = 1_500_000;
    /** JSON-LD 블록 하나의 최대 길이 */
    static final int MAX_JSON_CHARS = 300_000;
    /** 표 행을 이보다 많이 보지 않는다 */
    static final int MAX_ROWS = 3_000;

    /** 뽑은 값. 칸 길이는 ManualInputRequest 제한(255·64·500)에 맞춰 자른다. */
    public record Parsed(String productName, String brandName, String modelName, String makerName,
                         String barcodeNum, String certNum, String thumbnailUrl,
                         String kcStatus, String kcText) {

        public static final Parsed EMPTY = new Parsed(null, null, null, null, null, null, null, null, null);

        /** 대조에 쓸 값이 하나도 없다(사진 주소만 있는 것도 빈 것으로 본다) */
        public boolean isEmpty() {
            return productName == null && modelName == null && makerName == null && brandName == null
                    && barcodeNum == null && certNum == null;
        }
    }

    public static Parsed parse(String html, String pageUrl) {
        if (html == null || html.isBlank()) {
            return Parsed.EMPTY;
        }
        String doc = html.length() > MAX_HTML_CHARS ? html.substring(0, MAX_HTML_CHARS) : html;

        Map<String, Object> product = jsonLdProduct(doc);
        Map<String, String> meta = metaTags(doc);
        Map<String, String> table = labelValues(doc);

        String name = firstNonBlank(
                str(product, "name"),
                meta.get("og:title"), meta.get("twitter:title"),
                table.get(NAME), titleTag(doc));
        name = stripSiteSuffix(name, meta.get("og:site_name"));

        String brand = firstNonBlank(nameOf(product == null ? null : product.get("brand")),
                meta.get("product:brand"), table.get(BRAND));
        String model = firstNonBlank(nameOf(product == null ? null : product.get("model")),
                str(product, "mpn"), table.get(MODEL));
        String maker = firstNonBlank(nameOf(product == null ? null : product.get("manufacturer")), table.get(MAKER));
        String barcode = firstNonBlank(str(product, "gtin13"), str(product, "gtin"), str(product, "gtin14"),
                str(product, "gtin12"), str(product, "gtin8"), table.get(BARCODE));
        String image = absoluteHttp(firstNonBlank(imageOf(product == null ? null : product.get("image")),
                meta.get("og:image"), meta.get("og:image:url"), meta.get("og:image:secure_url")), pageUrl);

        // KC — 고시 표의 KC 칸이 있으면 그 칸이 기준이다
        String kcText = table.get(KC);
        String cert;
        String kcStatus = null;
        if (kcText != null) {
            List<String> numbers = KcCertNumbers.extract(kcText);
            cert = numbers.isEmpty() ? null : String.join(", ", numbers);
            kcStatus = cert != null ? "DISCLOSED" : (NOT_APPLICABLE.matcher(kcText).find() ? "NONE" : "REFERENCED");
        } else {
            List<String> numbers = WebPageIdentifiers.extract(doc).certNums();
            cert = numbers.isEmpty() ? null : String.join(", ", numbers.subList(0, Math.min(numbers.size(),
                    KcCertNumbers.MAX_NUMBERS)));
        }

        return new Parsed(cut(name, 255), cut(brand, 255), cut(model, 255), cut(maker, 255),
                cut(barcode, 64), cut(cert, 64), image != null && image.length() <= 500 ? image : null,
                kcStatus, cut(kcText, 255));
    }

    // ------------------------------------------------------------------ 고시 표

    static final String NAME = "name";
    static final String MODEL = "model";
    static final String MAKER = "maker";
    static final String BRAND = "brand";
    static final String BARCODE = "barcode";
    static final String KC = "kc";

    /** KC 칸에 적힌 'KC 대상 아님' 표기 */
    private static final Pattern NOT_APPLICABLE = Pattern.compile("해당\\s*(?:사항\\s*)?없음|비대상|대상\\s*아님|대상\\s*외");

    /** 라벨 → 칸 종류. 공백을 지우고 소문자로 바꾼 라벨로 본다. 먼저 나온 칸을 쓴다. */
    static String kindOf(String label) {
        String l = label.replaceAll("[\\s:：*]", "").toLowerCase(Locale.ROOT);
        if (l.isEmpty() || l.length() > 40) {
            return null;
        }
        if (l.contains("인증기관") || l.contains("인증일")) {
            return null;
        }
        if (l.startsWith("kc") || l.contains("인증번호") || l.contains("인증정보") || l.contains("안전인증")
                || l.contains("안전확인") || l.contains("공급자적합성") || l.contains("인증필")) {
            return KC;
        }
        if (l.contains("모델명") || l.contains("모델번호") || l.equals("품번") || l.equals("제품번호")) {
            return MODEL;
        }
        if (l.startsWith("제조자") || l.startsWith("제조사") || l.startsWith("제조업자") || l.startsWith("제조원")
                || l.startsWith("수입자") || l.startsWith("수입원") || l.startsWith("제조/수입")) {
            return MAKER;
        }
        if (l.equals("브랜드") || l.equals("brand")) {
            return BRAND;
        }
        if (l.contains("바코드") || l.equals("gtin") || l.equals("ean")) {
            return BARCODE;
        }
        if (l.equals("상품명") || l.equals("제품명") || l.equals("품명")) {
            return NAME;
        }
        return null;
    }

    /** &lt;tr&gt; 안의 th/td 를 (라벨, 값) 짝으로, &lt;dl&gt; 의 dt/dd 를 짝으로 읽는다. 칸 종류별 첫 값. */
    static Map<String, String> labelValues(String doc) {
        Map<String, String> out = new LinkedHashMap<>();
        String lower = doc.toLowerCase(Locale.ROOT);
        int rows = 0;
        int i = 0;
        while (rows++ < MAX_ROWS) {
            int tr = lower.indexOf("<tr", i);
            if (tr < 0) {
                break;
            }
            int end = lower.indexOf("</tr", tr);
            if (end < 0) {
                end = Math.min(lower.length(), tr + 20_000);
            }
            List<String> cells = cells(doc, lower, tr, end, "<th", "<td");
            for (int c = 0; c + 1 < cells.size(); c += 2) {
                put(out, cells.get(c), cells.get(c + 1));
            }
            i = end + 1;
        }
        i = 0;
        rows = 0;
        while (rows++ < MAX_ROWS) {
            int dt = lower.indexOf("<dt", i);
            if (dt < 0) {
                break;
            }
            int dtEnd = lower.indexOf("</dt", dt);
            int dd = dtEnd < 0 ? -1 : lower.indexOf("<dd", dtEnd);
            if (dd < 0 || dd - dtEnd > 2_000) {
                i = dt + 3;
                continue;
            }
            int ddEnd = lower.indexOf("</dd", dd);
            if (ddEnd < 0) {
                break;
            }
            put(out, inner(doc, dt, dtEnd), inner(doc, dd, ddEnd));
            i = ddEnd + 1;
        }
        return out;
    }

    private static void put(Map<String, String> out, String label, String value) {
        String kind = label == null ? null : kindOf(label);
        if (kind == null || value == null || value.isBlank() || out.containsKey(kind)) {
            return;
        }
        if (value.length() > 300) {
            return; // 설명 문단이지 칸 값이 아니다
        }
        out.put(kind, value);
    }

    /** [from, to) 구간의 셀(태그 이름이 openA 또는 openB 로 시작) 본문 텍스트 */
    private static List<String> cells(String doc, String lower, int from, int to, String openA, String openB) {
        List<String> out = new ArrayList<>();
        int i = from + 3;
        while (i < to) {
            int a = lower.indexOf(openA, i);
            int b = lower.indexOf(openB, i);
            int start = a < 0 || a >= to ? (b < 0 || b >= to ? -1 : b) : (b < 0 || b >= to ? a : Math.min(a, b));
            if (start < 0) {
                break;
            }
            String close = "</" + lower.substring(start + 1, start + 3);
            int end = lower.indexOf(close, start);
            if (end < 0 || end > to) {
                end = to;
            }
            out.add(inner(doc, start, end));
            i = end + 3;
        }
        return out;
    }

    /** 여는 태그 start 의 '>' 뒤부터 end 까지의 본문 텍스트 */
    private static String inner(String doc, int start, int end) {
        int gt = doc.indexOf('>', start);
        if (gt < 0 || gt >= end) {
            return "";
        }
        return WebPageIdentifiers.visibleText(doc.substring(gt + 1, end));
    }

    // ------------------------------------------------------------------ 메타·제목

    static Map<String, String> metaTags(String doc) {
        Map<String, String> out = new LinkedHashMap<>();
        String lower = doc.toLowerCase(Locale.ROOT);
        int i = 0;
        int count = 0;
        while (count++ < 500) {
            int m = lower.indexOf("<meta", i);
            if (m < 0) {
                break;
            }
            int gt = lower.indexOf('>', m);
            if (gt < 0) {
                break;
            }
            String tag = doc.substring(m, gt);
            String key = attr(tag, "property");
            if (key == null) {
                key = attr(tag, "name");
            }
            String content = attr(tag, "content");
            if (key != null && content != null && !content.isBlank()) {
                out.putIfAbsent(key.toLowerCase(Locale.ROOT).trim(), WebPageIdentifiers.visibleText(content));
            }
            i = gt + 1;
        }
        return out;
    }

    /** 태그 문자열에서 속성 값(따옴표 있음·없음) */
    static String attr(String tag, String name) {
        String lower = tag.toLowerCase(Locale.ROOT);
        int i = 0;
        while (true) {
            int p = lower.indexOf(name, i);
            if (p < 0) {
                return null;
            }
            boolean boundary = p > 0 && !Character.isLetterOrDigit(lower.charAt(p - 1)) && lower.charAt(p - 1) != '-'
                    && lower.charAt(p - 1) != ':';
            int q = p + name.length();
            while (q < tag.length() && tag.charAt(q) == ' ') {
                q++;
            }
            if (!boundary || q >= tag.length() || tag.charAt(q) != '=') {
                i = p + name.length();
                continue;
            }
            q++;
            while (q < tag.length() && tag.charAt(q) == ' ') {
                q++;
            }
            if (q >= tag.length()) {
                return null;
            }
            char quote = tag.charAt(q);
            if (quote == '"' || quote == '\'') {
                int close = tag.indexOf(quote, q + 1);
                return close < 0 ? tag.substring(q + 1) : tag.substring(q + 1, close);
            }
            int sp = q;
            while (sp < tag.length() && !Character.isWhitespace(tag.charAt(sp)) && tag.charAt(sp) != '/') {
                sp++;
            }
            return tag.substring(q, sp);
        }
    }

    static String titleTag(String doc) {
        String lower = doc.toLowerCase(Locale.ROOT);
        int t = lower.indexOf("<title");
        if (t < 0) {
            return null;
        }
        int end = lower.indexOf("</title", t);
        if (end < 0) {
            return null;
        }
        String s = inner(doc, t, end);
        return s.isBlank() ? null : s;
    }

    /** 사이트 이름 꼬리 단어 */
    private static final Pattern SHOP_WORD = Pattern.compile(
            "(?i)쇼핑|몰|마켓|스토어|11번가|g마켓|옥션|ssg|쿠팡|네이버|롯데on|롯데온|위메프|티몬|인터파크|store|shop|mall");

    /** "상품명 : 사이트" · "상품명 | 사이트" · "상품명 - 사이트" 의 사이트 꼬리를 뗀다 */
    static String stripSiteSuffix(String title, String siteName) {
        if (title == null) {
            return null;
        }
        String t = title.trim();
        for (String sep : new String[]{" | ", " : ", " - ", " :: "}) {
            int k = t.lastIndexOf(sep);
            if (k <= 0) {
                continue;
            }
            String tail = t.substring(k + sep.length()).trim();
            boolean site = (siteName != null && !siteName.isBlank() && tail.equalsIgnoreCase(siteName.trim()))
                    || (tail.length() <= 15 && SHOP_WORD.matcher(tail).find());
            if (site) {
                t = t.substring(0, k).trim();
            }
        }
        return t.isEmpty() ? null : t;
    }

    // ------------------------------------------------------------------ JSON-LD

    /** 문서의 ld+json 블록 중 첫 schema.org Product 객체(@graph 안도 본다). 없으면 null */
    @SuppressWarnings("unchecked")
    static Map<String, Object> jsonLdProduct(String doc) {
        String lower = doc.toLowerCase(Locale.ROOT);
        int i = 0;
        int count = 0;
        while (count++ < 30) {
            int s = lower.indexOf("<script", i);
            if (s < 0) {
                return null;
            }
            int gt = lower.indexOf('>', s);
            if (gt < 0) {
                return null;
            }
            int end = lower.indexOf("</script", gt);
            if (end < 0) {
                return null;
            }
            i = end + 1;
            String open = lower.substring(s, gt);
            if (!open.contains("ld+json") || end - gt > MAX_JSON_CHARS) {
                continue;
            }
            Object parsed = MiniJson.parse(doc.substring(gt + 1, end));
            Map<String, Object> product = findProduct(parsed, 0);
            if (product != null) {
                return product;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> findProduct(Object node, int depth) {
        if (node == null || depth > 6) {
            return null;
        }
        if (node instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> p = findProduct(o, depth + 1);
                if (p != null) {
                    return p;
                }
            }
            return null;
        }
        if (node instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (isProductType(map.get("@type"))) {
                return map;
            }
            for (String key : new String[]{"@graph", "mainEntity", "itemOffered"}) {
                Map<String, Object> p = findProduct(map.get(key), depth + 1);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private static boolean isProductType(Object type) {
        if (type instanceof String s) {
            return s.equalsIgnoreCase("Product") || s.endsWith("/Product") || s.equalsIgnoreCase("ProductGroup");
        }
        if (type instanceof List<?> list) {
            for (Object o : list) {
                if (isProductType(o)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String str(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object v = map.get(key);
        if (v instanceof String s) {
            return blankToNull(WebPageIdentifiers.visibleText(s));
        }
        if (v instanceof Number n) {
            // 바코드(gtin)가 숫자로 오면 Double 이라 지수 표기("8.8E12")가 된다
            try {
                return new java.math.BigDecimal(n.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** "brand": "A" 또는 "brand": {"name": "A"} 또는 [..] */
    @SuppressWarnings("unchecked")
    private static String nameOf(Object v) {
        if (v instanceof String s) {
            return blankToNull(WebPageIdentifiers.visibleText(s));
        }
        if (v instanceof Map<?, ?> m) {
            return str((Map<String, Object>) m, "name");
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            return nameOf(list.get(0));
        }
        return null;
    }

    /** "image": "url" 또는 ["url", ..] 또는 {"url": ..} */
    @SuppressWarnings("unchecked")
    private static String imageOf(Object v) {
        if (v instanceof String s) {
            return blankToNull(s.trim());
        }
        if (v instanceof List<?> list && !list.isEmpty()) {
            return imageOf(list.get(0));
        }
        if (v instanceof Map<?, ?> m) {
            Object u = ((Map<String, Object>) m).get("url");
            return u instanceof String s ? blankToNull(s.trim()) : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ 기타

    /** 상대 주소·'//host' 주소를 페이지 주소 기준 절대 주소로. http(s) 가 아니면 null */
    static String absoluteHttp(String url, String pageUrl) {
        if (url == null) {
            return null;
        }
        try {
            URI u = pageUrl == null ? new URI(url.trim()) : new URI(pageUrl.trim()).resolve(url.trim());
            String scheme = u.getScheme();
            return scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    ? u.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }

    /**
     * JSON-LD 를 읽을 만큼만 되는 작은 JSON 파서. 객체 → LinkedHashMap, 배열 → ArrayList, 문자열·숫자(Double)·
     * true/false/null. 깊이 40 넘게 중첩되거나 문법이 틀리면 null(예외를 던지지 않는다).
     */
    static final class MiniJson {

        private final String s;
        private int i;

        private MiniJson(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            if (text == null) {
                return null;
            }
            // HTML 안의 JSON-LD 앞뒤에 주석 표시(<!-- -->)나 CDATA 가 붙는 경우가 있다
            String t = text.trim();
            if (t.startsWith("<!--")) {
                t = t.substring(4);
            }
            if (t.endsWith("-->")) {
                t = t.substring(0, t.length() - 3);
            }
            try {
                MiniJson p = new MiniJson(t);
                p.ws();
                Object v = p.value(0);
                return v;
            } catch (RuntimeException e) {
                return null;
            }
        }

        private Object value(int depth) {
            if (depth > 40) {
                throw new IllegalStateException("too deep");
            }
            ws();
            char c = s.charAt(i);
            switch (c) {
                case '{': return object(depth);
                case '[': return array(depth);
                case '"': return string();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return number();
            }
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            ws();
            if (s.charAt(i) == '}') {
                i++;
                return m;
            }
            while (true) {
                ws();
                String key = string();
                ws();
                if (s.charAt(i++) != ':') {
                    throw new IllegalStateException(":");
                }
                m.put(key, value(depth + 1));
                ws();
                char c = s.charAt(i++);
                if (c == '}') {
                    return m;
                }
                if (c != ',') {
                    throw new IllegalStateException(",");
                }
            }
        }

        private List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            i++;
            ws();
            if (s.charAt(i) == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(value(depth + 1));
                ws();
                char c = s.charAt(i++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalStateException(",");
                }
            }
        }

        private String string() {
            if (s.charAt(i) != '"') {
                throw new IllegalStateException("\"");
            }
            i++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                    }
                    default -> sb.append(e); // \" \\ \/
                }
            }
        }

        private Double number() {
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            if (start == i) {
                throw new IllegalStateException("value");
            }
            return Double.valueOf(s.substring(start, i));
        }

        private void expect(String word) {
            if (!s.startsWith(word, i)) {
                throw new IllegalStateException(word);
            }
            i += word.length();
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
