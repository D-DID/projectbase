package com.underfaker.recallcheck.dto.internal;

import java.util.Locale;

/**
 * extraction.raw_text 에 남기는 메모 블록 두 가지 — 9/27 추가.
 *
 * <pre>
 * [detail]                         ← 확장이 쿠팡 상세페이지 '필수 표기 정보'에서 읽은 KC 인증정보 상태
 * kc: REFERENCED
 * kcText: 상품 상세페이지 참조
 * [/detail]
 *
 * [image-check]                    ← 사용자가 "사진으로 찾기"를 눌러 Vision 을 돌렸는지와 그 결과
 * result: FOUND
 * [/image-check]
 * </pre>
 *
 * 새 컬럼이 아니라 raw_text 에 두는 이유는 ImageInsight 의 [vision] 블록과 같다 —
 * ddl-auto=validate 라 컬럼을 늘리면 ALTER 를 안 한 DB 에서 앱이 뜨지 않는다.
 * verification.final_result 도 MySQL ENUM('MATCH','PARTIAL','NO_MATCH','UNKNOWN') 이라
 * "항목누락"을 새 판정값으로 넣으면 INSERT 가 실패한다. 그래서 항목누락은 저장하지 않고
 * 이 메모와 판정값으로 화면에 보여 줄 때 계산한다(ResultView).
 */
public final class ExtractionNotes {

    private ExtractionNotes() {
    }

    public static final String DETAIL_BEGIN = "[detail]";
    public static final String DETAIL_END = "[/detail]";
    public static final String CHECK_BEGIN = "[image-check]";
    public static final String CHECK_END = "[/image-check]";

    /**
     * 상세페이지 '필수 표기 정보'의 KC 인증정보 칸 상태 (확장 detail-parser.js 와 같은 값).
     *
     * DISCLOSED  — KC 칸에 인증번호가 적혀 있다(certNum 으로 같이 넘어온다)
     * REFERENCED — KC 칸은 있는데 "상품 상세페이지 참조" 처럼 번호가 없다  → 항목누락
     * NONE       — 표에 KC 칸 자체가 없다(식품 등 KC 대상이 아닌 품목)
     * UNREADABLE — 상세페이지를 못 읽었다(차단·구조 변경·네트워크)         → 항목누락
     */
    public enum KcStatus { DISCLOSED, REFERENCED, NONE, UNREADABLE }

    /** 사진 확인 결과 */
    public enum ImageCheck { FOUND, NONE, FAILED }

    public static KcStatus parseKcStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return KcStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 확장이 kcStatus 를 안 보냈으면(웹 직접입력 등) null — 블록을 만들지 않는다. */
    public static String detailBlock(String kcStatus, String kcText) {
        KcStatus status = parseKcStatus(kcStatus);
        if (status == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(DETAIL_BEGIN).append('\n')
                .append("kc: ").append(status.name()).append('\n');
        if (kcText != null && !kcText.isBlank()) {
            sb.append("kcText: ").append(oneLine(kcText)).append('\n');
        }
        return sb.append(DETAIL_END).toString();
    }

    public static String imageCheckBlock(ImageCheck result) {
        return CHECK_BEGIN + "\nresult: " + result.name() + "\n" + CHECK_END;
    }

    public static KcStatus kcStatus(String rawText) {
        return parseKcStatus(field(rawText, DETAIL_BEGIN, DETAIL_END, "kc:"));
    }

    public static String kcText(String rawText) {
        return field(rawText, DETAIL_BEGIN, DETAIL_END, "kcText:");
    }

    /** 사진 확인을 아직 안 했으면 null */
    public static ImageCheck imageCheck(String rawText) {
        String v = field(rawText, CHECK_BEGIN, CHECK_END, "result:");
        if (v == null) {
            return null;
        }
        try {
            return ImageCheck.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** KC 인증정보가 필요한 품목인데 번호를 못 얻었다 — 항목누락 */
    public static boolean infoMissing(String rawText) {
        KcStatus s = kcStatus(rawText);
        return s == KcStatus.REFERENCED || s == KcStatus.UNREADABLE;
    }

    /** 블록 안에서 "key: 값" 줄의 값을 꺼낸다. 블록이 여러 개면 마지막 블록을 쓴다(최신). */
    private static String field(String rawText, String begin, String end, String key) {
        if (rawText == null) {
            return null;
        }
        int b = rawText.lastIndexOf(begin);
        if (b < 0) {
            return null;
        }
        int e = rawText.indexOf(end, b);
        String body = rawText.substring(b + begin.length(), e < 0 ? rawText.length() : e);
        for (String line : body.split("\\R")) {
            String l = line.trim();
            if (l.startsWith(key)) {
                String v = l.substring(key.length()).trim();
                return v.isEmpty() ? null : v;
            }
        }
        return null;
    }

    private static String oneLine(String s) {
        String t = s.replaceAll("[\\r\\n]+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }
}
