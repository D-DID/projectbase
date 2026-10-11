package com.underfaker.recallcheck.common;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 10/10 — 사진 찾기(2차)에서 같은 사진이 실린 웹페이지 본문의 KC 인증번호·모델명(제품번호)을 뽑는다.
 *
 * ── 최종 흐름(팀장 결정 10/10) ──
 *   1차: KC 인증번호 + 제품명으로 판단
 *   2차: 1차로 판단할 수 없으면 썸네일을 Vision 으로 웹에서 찾고, 같은 사진이 실린 문서에서
 *        <b>가장 여러 사이트에서 반복된 값</b>(정확도가 높은 기준)으로 KC 인증번호·제품명·제품번호를 가져와 판단
 * Vision 은 페이지 주소와 제목만 준다(본문 없음). 그래서 서버가 그 페이지를 열어 여기서 값을 뽑는다.
 * 사이트별로 뽑은 값을 {@link #consensus} 로 모아, 서로 다른 사이트 N곳 이상에서 같은 값만 쓴다.
 *
 * ── 한계 ──
 * - 상세 정보가 이미지 안에만 있거나 자바스크립트로 그려지는 페이지는 못 읽는다(본문 텍스트만 본다).
 * - 쿠팡 페이지는 서버에서 열면 403 이라 호출부(GoogleVisionClient)가 건너뛴다.
 * - 라벨 근처의 값만 본다. 추천 상품 칸의 다른 상품 번호가 섞일 수 있지만, 사이트 2곳 이상 일치를 요구해 걸러진다.
 * Spring 의존 없음 — 단위 테스트용.
 */
public final class WebPageIdentifiers {

    private WebPageIdentifiers() {
    }

    /** 한 페이지에서 뽑아 둘 최대 개수 — 본문이 긴 목록 페이지에서 후보가 불어나는 것을 막는다. */
    public static final int MAX_PER_PAGE = 5;

    /** 본문 처리 상한(문자 수). 이 뒤는 보지 않는다. */
    static final int MAX_TEXT_CHARS = 2_000_000;

    /** KC 번호를 찾을 라벨 뒤 범위(문자 수) */
    static final int CERT_WINDOW = 120;

    /** 모델명을 찾을 라벨 뒤 범위(문자 수) */
    static final int MODEL_WINDOW = 40;

    /** 뽑은 값. 없으면 빈 목록. */
    public record Found(List<String> certNums, List<String> modelNames) {
        public static final Found EMPTY = new Found(List.of(), List.of());

        public Found {
            certNums = certNums == null ? List.of() : List.copyOf(certNums);
            modelNames = modelNames == null ? List.of() : List.copyOf(modelNames);
        }

        public boolean isEmpty() {
            return certNums.isEmpty() && modelNames.isEmpty();
        }
    }

    private static final Pattern NUM_ENTITY = Pattern.compile("&#(x[0-9a-fA-F]{1,6}|[0-9]{1,7});");
    private static final Pattern SPACES = Pattern.compile("[\\s\\u00a0\\u3000]+");

    /** KC 인증번호가 뒤따르는 라벨. "KC인증번호", "인증번호", "안전확인", "공급자적합성" 등 */
    private static final Pattern CERT_LABEL = Pattern.compile(
            "(?i)\\bKC|인증\\s*번호|인증\\s*정보|안전\\s*인증|안전\\s*확인|공급자\\s*적합성");

    /** 모델명(제품번호)이 뒤따르는 라벨. "품명 및 모델명" 도 "모델명" 으로 걸린다. */
    private static final Pattern MODEL_LABEL = Pattern.compile(
            "(?i)모델\\s*명|모델\\s*번호|제품\\s*번호|품\\s*번|\\bmodel(?:\\s*(?:no\\.?|name|number))?\\b");

    /** 모델명 후보 — 영문·숫자·'-' 3~30자, 숫자와 영문을 둘 다 포함 */
    private static final Pattern MODEL_TOKEN = Pattern.compile(
            "(?<![A-Za-z0-9-])(?=[A-Za-z0-9-]*[0-9])(?=[A-Za-z0-9-]*[A-Za-z])[A-Za-z0-9][A-Za-z0-9-]{2,29}(?![A-Za-z0-9-])");

    /** 모델명이 아니라 용량·수량·규격인 값("100ML", "3EA", "500G") */
    private static final Pattern UNIT_ONLY = Pattern.compile(
            "(?i)\\d+(?:\\.\\d+)?(?:ML|L|G|KG|MG|MM|CM|M|EA|P|PCS|T|W|V|MAH|HZ|GB|TB|X\\d+)");

    private static final Pattern META_CHARSET = Pattern.compile(
            "(?i)<meta[^>]+charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_\\-]+)");
    private static final Pattern HEADER_CHARSET = Pattern.compile("(?i)charset\\s*=\\s*\"?([A-Za-z0-9_\\-]+)");

    /**
     * 응답 바이트를 문자열로. Content-Type 의 charset → 문서 앞부분의 meta charset → UTF-8 순서.
     * 국내 쇼핑몰은 아직 EUC-KR 페이지가 있다.
     */
    public static String decode(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return "";
        }
        Charset cs = charsetOf(contentType == null ? null : HEADER_CHARSET.matcher(contentType));
        if (cs == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            cs = charsetOf(META_CHARSET.matcher(head));
        }
        return new String(body, cs == null ? StandardCharsets.UTF_8 : cs);
    }

    private static Charset charsetOf(Matcher m) {
        if (m == null || !m.find()) {
            return null;
        }
        String name = m.group(1).trim();
        if (name.equalsIgnoreCase("euc-kr") || name.equalsIgnoreCase("ks_c_5601-1987")) {
            name = "x-windows-949"; // EUC-KR 의 상위 집합(CP949). 확장 한글이 깨지지 않게
        }
        try {
            return Charset.forName(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** HTML → 사람이 보는 본문 텍스트(스크립트·스타일·태그 제거, 엔티티 일부 복원, 공백 정리). */
    public static String visibleText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html.length() > MAX_TEXT_CHARS ? html.substring(0, MAX_TEXT_CHARS) : html;
        s = stripTags(s);
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
        Matcher m = NUM_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder(s.length());
        while (m.find()) {
            String g = m.group(1);
            int cp;
            try {
                cp = g.startsWith("x") || g.startsWith("X")
                        ? Integer.parseInt(g.substring(1), 16) : Integer.parseInt(g);
            } catch (NumberFormatException e) {
                cp = ' ';
            }
            String rep = Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : " ";
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return SPACES.matcher(sb).replaceAll(" ").trim();
    }

    /** 내용째 버리는 요소 — 본문 텍스트가 아니다 */
    private static final String[] RAW_TEXT_ELEMENTS = {"script", "style", "noscript"};

    /**
     * 태그·주석·스크립트·스타일을 공백으로 바꾼다. 앞에서 한 번만 훑는다(문서 길이에 비례).
     * 10/10 리뷰 — 정규식(<[^>]*> 등)은 닫히지 않은 '<' 가 많은 문서에서 시간이 제곱으로 늘었다(4만 자 5.6초).
     * 닫히지 않은 '<' 를 만나면 나머지는 본문으로 둔다.
     */
    static String stripTags(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c != '<') {
                out.append(c);
                i++;
                continue;
            }
            if (s.startsWith("<!--", i)) {
                int e = s.indexOf("-->", i + 4);
                i = e < 0 ? n : e + 3;
                out.append(' ');
                continue;
            }
            String raw = rawTextElementAt(s, i);
            if (raw != null) {
                int e = indexOfIgnoreCase(s, "</" + raw, i + 1 + raw.length());
                int gt = e < 0 ? -1 : s.indexOf('>', e);
                i = gt < 0 ? n : gt + 1;
                out.append(' ');
                continue;
            }
            int gt = s.indexOf('>', i + 1);
            if (gt < 0) {
                out.append(s, i, n);
                break;
            }
            out.append(' ');
            i = gt + 1;
        }
        return out.toString();
    }

    /** i 에서 시작하는 태그가 &lt;script·&lt;style·&lt;noscript 면 그 이름 */
    private static String rawTextElementAt(String s, int i) {
        for (String name : RAW_TEXT_ELEMENTS) {
            int end = i + 1 + name.length();
            if (s.regionMatches(true, i + 1, name, 0, name.length())
                    && (end >= s.length() || !Character.isLetterOrDigit(s.charAt(end)))) {
                return name;
            }
        }
        return null;
    }

    private static int indexOfIgnoreCase(String s, String needle, int from) {
        int last = s.length() - needle.length();
        for (int k = Math.max(0, from); k <= last; k++) {
            if (s.regionMatches(true, k, needle, 0, needle.length())) {
                return k;
            }
        }
        return -1;
    }

    /** HTML 에서 KC 인증번호·모델명을 뽑는다. */
    public static Found extract(String html) {
        String text = visibleText(html);
        if (text.isEmpty()) {
            return Found.EMPTY;
        }
        return new Found(certNumbers(text), modelNames(text));
    }

    /** 라벨("KC", "인증번호" …) 뒤 {@link #CERT_WINDOW}자 안의 인증번호 */
    static List<String> certNumbers(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher label = CERT_LABEL.matcher(text);
        while (label.find() && out.size() < MAX_PER_PAGE) {
            int from = label.start();
            int to = Math.min(text.length(), label.end() + CERT_WINDOW);
            for (String n : KcCertNumbers.extract(text.substring(from, to))) {
                if (out.size() >= MAX_PER_PAGE) {
                    break;
                }
                out.add(n);
            }
        }
        return new ArrayList<>(out);
    }

    /** 라벨("모델명", "품번" …) 바로 뒤 {@link #MODEL_WINDOW}자 안의 첫 모델명 후보 */
    static List<String> modelNames(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher label = MODEL_LABEL.matcher(text);
        while (label.find() && out.size() < MAX_PER_PAGE) {
            int to = Math.min(text.length(), label.end() + MODEL_WINDOW);
            Matcher token = MODEL_TOKEN.matcher(text.substring(label.end(), to));
            while (token.find()) {
                String t = token.group().toUpperCase(Locale.ROOT);
                if (UNIT_ONLY.matcher(t).matches() || !KcCertNumbers.extract(t).isEmpty()) {
                    continue; // 용량·수량이거나 KC 인증번호다
                }
                out.add(t);
                break;
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * 사이트별로 뽑은 값 중, 서로 다른 사이트 {@code minSites} 곳 이상에서 나온 값을 고른다.
     * 가장 많은 사이트에서 나온 값, 같으면 먼저 나온 값(호출부가 페이지 순위대로 넣는다). 없으면 null.
     *
     * @param valuesBySite 사이트 → 그 사이트 페이지에서 뽑은 값들
     */
    public static String consensus(Map<String, ? extends Collection<String>> valuesBySite, int minSites) {
        if (valuesBySite == null || valuesBySite.isEmpty()) {
            return null;
        }
        Map<String, Integer> sites = new LinkedHashMap<>();
        for (Collection<String> values : valuesBySite.values()) {
            if (values == null) {
                continue;
            }
            for (String v : new LinkedHashSet<>(values)) {
                if (v != null && !v.isBlank()) {
                    sites.merge(v.trim().toUpperCase(Locale.ROOT), 1, Integer::sum);
                }
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : sites.entrySet()) {
            if (e.getValue() >= minSites && e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return best;
    }
}
