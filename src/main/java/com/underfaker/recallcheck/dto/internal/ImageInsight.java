package com.underfaker.recallcheck.dto.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Cloud Vision Web Detection 판독 결과.
 *
 * ── 왜 "이미지 유사도"가 아니라 이건가 (9/24) ──
 * 노션 2단계 설계에는 "Google Vision/Google Lens 이미지 유사도"라고 적혀 있다.
 * 그대로는 만들 수 없다. 확인한 사실 두 가지다.
 *   (1) Cloud Vision API 에는 이미지 대 이미지 유사도를 돌려주는 기능이 없다.
 *       LABEL_DETECTION·OBJECT_LOCALIZATION·WEB_DETECTION 전부 이미지 → 텍스트다.
 *   (2) Google Lens 는 공개 API 가 없다. 브라우저·앱 전용이다.
 *
 * 그리고 우리 DB 쪽에도 제약이 있다. recall_file(공표문 첨부 이미지) 커버리지가
 * 700건 중 약 55건(8%)이고 전부 recall_uid >= 10022507 구간에 몰려 있다.
 * 이미지 대 이미지가 가능했더라도 비교 대상 이미지가 92% 없다.
 *
 * 그래서 방향을 바꿨다. WEB_DETECTION 으로 역이미지 검색을 걸어
 * bestGuessLabel(검색엔진이 추정한 이 사진의 이름)과 webEntities(연관 개체명)라는
 * <b>텍스트</b>를 받아 온다. 그 텍스트를 이미 있는 SimilarityCalculator 로
 * 공표문의 모델명·상품명과 대조한다. 새 매칭 엔진을 만들지 않는다.
 *
 * @param bestGuessLabel    검색엔진이 추정한 이 이미지의 이름. 없을 수 있다.
 * @param entities          연관 개체명 (점수 내림차순, 최대 {@code MAX_ENTITIES}개)
 * @param topEntityScore    가장 높은 개체 점수. 판독 신뢰도 대용으로 쓴다.
 * @param matchingPageCount 이 이미지가 발견된 웹페이지 수. 0 이면 웹에서 못 찾은 이미지다.
 * @param pageTitles        이 이미지가 실린 웹페이지 제목 (최대 {@code MAX_PAGE_TITLES}개, HTML 제거·사이트명 분리 후).
 *
 * ── 9/27 pageTitles 추가 ──
 * 실측(슬라임 쿠팡 썸네일 120x120): bestGuess='honeybee', entities=[Western honey bee, Bees, …].
 * Vision 의 bestGuess·webEntities 는 대개 <b>영어</b>라서 한글 공표문("(제품명) 허니 슬라임")과
 * 글자 단위로 겹칠 수가 없다 — 이미지판독 0%. 반면 같은 응답의 pagesWithMatchingImages[].pageTitle 은
 * 그 사진이 실린 판매 페이지 제목이라 한국 쇼핑몰이면 <b>한글 상품명</b>이다. 응답에 이미 들어 있던
 * 값(pages=10)을 버리고 있었다. 추가 호출·추가 비용 없음.
 */
public record ImageInsight(
        String bestGuessLabel,
        List<String> entities,
        double topEntityScore,
        int matchingPageCount,
        List<String> pageTitles,
        /* 10/7 — 같은 사진이 실린 서로 다른 사이트 2곳 이상에서 반복된 상품명(GoogleVisionClient.consensusTitle). 없으면 null */
        String productName
) {

    /** 판독하지 않았거나 실패한 경우. null 대신 이걸 쓴다. */
    public static final ImageInsight NONE =
            new ImageInsight(null, List.of(), 0.0, 0, List.of(), null);

    /**
     * 9/27 — 호출 자체가 실패했다(네트워크·API 오류·토큰·월 상한). NONE(웹에서 못 찾음)과 구분해야
     * 사진 확인 버튼을 "다시 누를 수 있음"으로 남길 수 있다. isUsable() 은 false 라 기존 호출부는 그대로다.
     */
    public static final ImageInsight FAILED =
            new ImageInsight(null, List.of(), 0.0, -1, List.of(), null);

    /** 호출 실패로 받은 결과인가 */
    public boolean failed() {
        return matchingPageCount < 0;
    }

    /** 저장·대조에 쓸 페이지 제목 최대 개수. 대조 비용이 후보 수에 비례한다. */
    public static final int MAX_PAGE_TITLES = 5;

    /** 9/24 형태(페이지 제목 없음) 호환용. */
    public ImageInsight(String bestGuessLabel, List<String> entities, double topEntityScore, int matchingPageCount) {
        this(bestGuessLabel, entities, topEntityScore, matchingPageCount, List.of(), null);
    }

    /** 9/27 형태(상품명 없음) 호환용. */
    public ImageInsight(String bestGuessLabel, List<String> entities, double topEntityScore, int matchingPageCount,
                        List<String> pageTitles) {
        this(bestGuessLabel, entities, topEntityScore, matchingPageCount, pageTitles, null);
    }

    /**
     * 이 점수 미만의 개체는 버린다.
     *
     * Vision 은 확신이 없어도 개체를 돌려준다. 0.2 짜리 "Product", "Toy" 같은
     * 범용어가 섞여 들어오면 공표문의 "기타완구(완구)" 같은 분류명과 우연히 붙어서
     * 근거 없는 점수를 만든다. 잠정치다.
     */
    public static final double MIN_ENTITY_SCORE = 0.40;

    public ImageInsight {
        entities = entities == null ? List.of() : List.copyOf(entities);
        pageTitles = pageTitles == null ? List.of() : List.copyOf(pageTitles);
        productName = productName == null || productName.isBlank() ? null : productName.trim();
    }

    /** 대조에 쓸 만한 판독 결과가 있는가. */
    public boolean isUsable() {
        return (bestGuessLabel != null && !bestGuessLabel.isBlank())
                || !entities.isEmpty() || !pageTitles.isEmpty();
    }

    /**
     * 공표문과 대조해 볼 텍스트 후보들.
     * bestGuessLabel 을 맨 앞에 둔다 — 개체명보다 구체적이라 대개 잘 맞는다.
     * 페이지 제목은 맨 뒤에 둔다. 순서는 동점일 때 어느 값을 근거로 보여 줄지만 바꾼다.
     */
    public List<String> textCandidates() {
        List<String> out = new ArrayList<>();
        if (bestGuessLabel != null && !bestGuessLabel.isBlank()) {
            out.add(bestGuessLabel.trim());
        }
        for (String e : entities) {
            if (e != null && !e.isBlank() && !out.contains(e.trim())) {
                out.add(e.trim());
            }
        }
        for (String t : pageTitles) {
            if (t != null && !t.isBlank() && !out.contains(t.trim())) {
                out.add(t.trim());
            }
        }
        return out;
    }

    /**
     * Vision 이 돌려준 pageTitle 을 대조용으로 다듬는다. 쓸 게 없으면 null.
     *
     * pageTitle 은 HTML 이 섞일 수 있다고 API 문서에 적혀 있다("may contain HTML markups").
     * 태그·엔티티를 지우고, 사이트명 구분자(" - ", " : ", " :: ", " | ")를 "|" 로 바꿔 둔다 —
     * 대조 쪽 splitList 가 "|" 로 쪼개므로 "아트박스 허니 슬라임 - 쿠팡" 의 상품명 조각이 따로 대조된다.
     */
    public static String cleanPageTitle(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.replaceAll("<[^>]*>", " ")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&lt;", " ").replace("&gt;", " ").replace("&nbsp;", " ")
                .replaceAll("\\s+(?:-|:|::|\\||｜|–|—)\\s+", " | ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        return s.isEmpty() ? null : s;
    }

    /** 개체명을 한 줄로. 로그·화면용. */
    public String entitiesAsText() {
        return entities.isEmpty() ? null : String.join(", ", entities);
    }

    // ------------------------------------------------------------------ extraction.raw_text 저장

    /**
     * extraction.raw_text 에 붙일 블록의 머리말.
     *
     * ── 왜 새 컬럼이 아니라 raw_text 인가 (9/24) ──
     * extraction 은 ddl-auto=validate 다. 컬럼을 늘리면 ALTER TABLE 을 안 돌린 DB 에서
     * 앱이 기동하지 못한다. 팀원 넷이 각자 로컬 DB 를 쓰고 30일 직전이라 그 위험을 지지 않는다.
     *
     * raw_text 는 "입력 소스에서 뽑아낸 원문 텍스트" 칸이고, 직접입력(MANUAL) 행에서는
     * 항상 비어 있다. 매칭 코드는 rawText 를 읽지 않는다(IdentityMerger 가 이어 붙이기만 한다).
     * Vision 판독 결과는 "썸네일 이미지에서 뽑아낸 텍스트"이므로 이 칸의 뜻과도 맞는다.
     *
     * 형식은 사람이 DB 에서 바로 읽을 수 있게 줄 단위로 쓴다.
     * <pre>
     * [vision]
     * best: 허니 슬라임
     * top: 0.8300
     * pages: 4
     * entity: Slime
     * entity: Toy
     * title: 아트박스 허니 슬라임 | 쿠팡      (9/27 추가 — 없던 블록도 그대로 읽힌다)
     * [/vision]
     * </pre>
     */
    public static final String RAW_TEXT_BEGIN = "[vision]";
    public static final String RAW_TEXT_END = "[/vision]";

    /** raw_text 에 붙일 블록. 판독 결과가 없으면 null. */
    public String toRawTextBlock() {
        if (!isUsable()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(RAW_TEXT_BEGIN).append('\n');
        if (bestGuessLabel != null && !bestGuessLabel.isBlank()) {
            sb.append("best: ").append(oneLine(bestGuessLabel)).append('\n');
        }
        sb.append("top: ").append(String.format(java.util.Locale.ROOT, "%.4f", topEntityScore)).append('\n');
        sb.append("pages: ").append(matchingPageCount).append('\n');
        for (String e : entities) {
            sb.append("entity: ").append(oneLine(e)).append('\n');
        }
        for (String t : pageTitles) {
            sb.append("title: ").append(oneLine(t)).append('\n');
        }
        if (productName != null) {
            sb.append("product: ").append(oneLine(productName)).append('\n');
        }
        return sb.append(RAW_TEXT_END).toString();
    }

    /**
     * raw_text 에서 블록을 찾아 되살린다. 블록이 없거나 깨졌으면 NONE.
     * 블록 앞뒤에 다른 텍스트(OCR 원문 등)가 있어도 된다.
     */
    public static ImageInsight fromRawText(String rawText) {
        if (rawText == null) {
            return NONE;
        }
        int begin = rawText.indexOf(RAW_TEXT_BEGIN);
        if (begin < 0) {
            return NONE;
        }
        int end = rawText.indexOf(RAW_TEXT_END, begin);
        String body = rawText.substring(begin + RAW_TEXT_BEGIN.length(), end < 0 ? rawText.length() : end);

        String best = null;
        double top = 0.0;
        int pages = 0;
        List<String> entities = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        String product = null;
        for (String line : body.split("\\R")) {
            String l = line.trim();
            if (l.startsWith("best:")) {
                best = l.substring(5).trim();
            } else if (l.startsWith("top:")) {
                top = parseDouble(l.substring(4).trim());
            } else if (l.startsWith("pages:")) {
                pages = (int) parseDouble(l.substring(6).trim());
            } else if (l.startsWith("entity:")) {
                String e = l.substring(7).trim();
                if (!e.isEmpty()) {
                    entities.add(e);
                }
            } else if (l.startsWith("title:")) {
                String t = l.substring(6).trim();
                if (!t.isEmpty()) {
                    titles.add(t);
                }
            } else if (l.startsWith("product:")) {
                product = l.substring(8).trim();
            }
        }
        ImageInsight insight = new ImageInsight(best == null || best.isEmpty() ? null : best,
                entities, top, pages, titles, product);
        return insight.isUsable() ? insight : NONE;
    }

    private static String oneLine(String s) {
        return s.replaceAll("[\\r\\n]+", " ").trim();
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
