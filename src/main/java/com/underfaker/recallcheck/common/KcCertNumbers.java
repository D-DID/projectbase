package com.underfaker.recallcheck.common;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * KC 인증번호 칸의 원문에서 조회할 수 있는 번호만 뽑는다 (10/3 추가, KC인증 연동).
 *
 * 왜 필요한가 — 리콜 공표문 3,675건의 cert_num 칸을 실측해 보니 번호 말고도 이런 값이 들어 있다.
 *   "-" (444건), "공급자적합성" (141), "비대상" (112), "안전품질표시", "해당없음",
 *   "CB131R230-0001&lt;br&gt;(인증모델: …)" 처럼 HTML 이 섞인 값,
 *   "B351A030-1001,B351A030-1002,B351A030-1003" 처럼 여러 개를 쉼표로 이은 값(463건),
 *   "GRAB16-EM001332" 같은 전파인증 번호.
 * 판매자가 쿠팡에 적는 값도 똑같이 제각각이라, 그대로 API 에 넣으면 조회가 안 되거나 엉뚱한 게 걸린다.
 *
 * 실측한 번호 모양(앞은 건수):
 *   535 HH07952-13056 · 409 CB063R423-5001 · 308 CB015R0261-6001 · 261 HH07975-11004C
 *   245 B364R970-2001 · 162 HH071257-13008 · 104 JH07132-3001 · 99 B321R051-18001
 *    60 B361R3583-1003 ·  12 XL090008-16192 ·   7 CB063R10802-3001
 *   드물게 B06AH030-9006A(가운데 영문 2자) · B361H400-4003CH · CB064A3166-2004CHC(끝 영문 2~3자)
 * → 영문 1~3자 + 숫자, (영문 1~2자 + 숫자)?, '-', 숫자 4~6자(실측 4자 1,499 · 5자 1,334 · 6자 15, 3자 0), 끝 영문 0~3자.
 *
 * 바로 앞에 영문·숫자·'-' 가 붙어 있으면 번호로 보지 않는다 — 다른 인증번호의 뒷부분을 잘라 오지 않게.
 *   "R-C-K9W-PR100-123"(전파인증) → "PR100-123" 을 뽑으면 안 된다(10/3 리뷰에서 발견).
 * 그 대신 원문 앞에 흔히 붙는 것은 미리 떼어 낸다: 원문자 번호 "①B403R111-7010", 접두어 "KC-"/"KC:".
 * "GRAB16-EM001332"(전파인증)·"JPTUV-144010"(해외인증)은 모양이 달라 걸리지 않는다.
 *
 * KC 목록 API 는 인증번호 조회도 <b>앞부분 일치</b>라서("CB067R2225" 로 "CB067R2225-4001" 이 나온다, 10/3 실측)
 * '-' 뒤 일련번호까지 갖춘 완전한 번호만 뽑는다. Spring 의존 없음 — 단위 테스트용.
 */
public final class KcCertNumbers {

    private KcCertNumbers() {
    }

    /** 한 입력에서 조회할 최대 번호 수 — 칸 하나에 번호가 수십 개 붙은 경우 API 호출 폭주를 막는다. */
    public static final int MAX_NUMBERS = 3;

    private static final Pattern NUMBER = Pattern.compile(
            "(?<![A-Z0-9-])[A-Z]{1,3}\\d{2,6}(?:[A-Z]{1,2}\\d{1,6})?-\\d{4,6}[A-Z]{0,3}(?![A-Z0-9])");

    /** 원문자 번호 ①②… ⑴… ❶… — NFKC 가 숫자로 바꿔 번호 앞에 붙어 버리므로 먼저 공백으로 */
    private static final Pattern ENCLOSED = Pattern.compile("[\\u2460-\\u24FF\\u2776-\\u2793]");

    /** "KC-CB067R2225-4001", "KC:CB067…" 처럼 번호 앞에 붙인 KC 표시 */
    private static final Pattern KC_PREFIX = Pattern.compile("(?<![A-Z0-9])KC\\s*[-:]\\s*(?=[A-Z])");

    /** "인증번호-CB063R423-5001" 처럼 한글 설명·괄호 바로 뒤에 '-' 로 이은 번호 — 그 '-' 는 구분자다(10/3 리뷰) */
    private static final Pattern LABEL_DASH = Pattern.compile("(?<=[가-힣)\\]])-(?=[A-Z])");

    /** NFKC 가 바꾸지 않는 대시류 → '-' */
    private static final Pattern DASHES = Pattern.compile("[\\u2010-\\u2015\\u2212\\uFE58\\uFE63]");

    /** "CB067R2225 - 4001" 처럼 대시 앞뒤에 들어간 공백 */
    private static final Pattern SPACED_DASH = Pattern.compile("\\s*-\\s*");

    /**
     * 조회할 수 있는 인증번호 목록(대문자, 중복 제거, 나온 순서, 최대 {@link #MAX_NUMBERS}개).
     * 번호가 하나도 없으면 빈 목록.
     */
    public static List<String> extract(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String s = ENCLOSED.matcher(raw).replaceAll(" ");
        s = Normalizer.normalize(s, Normalizer.Form.NFKC).toUpperCase(Locale.ROOT);
        s = DASHES.matcher(s).replaceAll("-");
        s = SPACED_DASH.matcher(s).replaceAll("-");
        s = KC_PREFIX.matcher(s).replaceAll(" ");
        s = LABEL_DASH.matcher(s).replaceAll(" ");

        Set<String> found = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(s);
        while (m.find() && found.size() < MAX_NUMBERS) {
            found.add(m.group());
        }
        return new ArrayList<>(found);
    }

    /** 첫 번째 번호. 없으면 null. */
    public static String first(String raw) {
        List<String> numbers = extract(raw);
        return numbers.isEmpty() ? null : numbers.get(0);
    }
}
