package com.underfaker.recallcheck.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 표기 정규화 핵심 로직 — 공백·기호·대소문자·라벨.
 *
 * service.matching.FieldNormalizer(매칭 엔진)와 entity.Recall(저장 시 normalized_* 컬럼)이
 * 똑같은 규칙을 써야 해서 여기 common 패키지에 둔다. 엔티티는 Spring 빈을 주입받을 수 없으므로
 * (JPA 가 new 로 만드는 객체) 순수 static 메서드로 둬서 양쪽 다 이걸 호출한다.
 * 규칙을 바꿀 일이 생기면 여기 한 곳만 고친다.
 *
 * ── 9/20 전면 개정 ──
 * 20260723 공표분 53건을 실제로 적재해 보고 드러난 문제 세 가지를 잡는다.
 *
 * (1) 빈 문자열 반환 → 전건 오탐
 *     국표원 공표 데이터는 값이 없는 칸을 NULL 이 아니라 "-" 문자로 채워 보낸다.
 *     예: recall_uid 10022552 의 cert_num = "-", 10022516 의 barcode_num = "-".
 *     구버전 normalize() 는 하이픈을 "제거 대상 기호"로 취급해서 "-" → "" 를 만들었고,
 *     그 값이 normalized_cert_num 컬럼에 그대로 저장됐다.
 *     그러면 findByNormalizedCertNumContaining("") 이 LIKE '%%' 로 번역되어
 *     recall 테이블 전건이 후보로 끌려온다. 인증번호가 없는 상품 하나가 리콜 전건과
 *     매칭되는 사고가 여기서 난다. 이제 이런 값은 null 을 반환한다.
 *
 * (2) 라벨 접두어가 값에 섞여 있음
 *     recall_model_name 에 품번 대신 라벨이 붙은 문자열이 온다.
 *     예: "(품번) VDECX01", "(제품명) 허니 슬라임", "(STYLE NO) IB61AB562",
 *         "(온라인)투데이리빙 16개 세트 동물 캐릭터...".
 *     구버전은 괄호만 떼서 "품번VDECX01" 을 만들었다. 입력이 "VDECX01" 이면 부분일치로
 *     우연히 걸리긴 하지만 점수가 왜곡되고, "(품명) 의류" 같은 값은 "품명의류" 가 되어
 *     아무 의류에나 걸린다. 이제 라벨 토큰 자체를 걷어낸다.
 *
 * (3) 규칙이 두 벌로 갈라짐
 *     모델명 전용 정규화가 FieldNormalizer 에만 있어서 엔티티 저장 경로와 매칭 경로의
 *     규칙이 달랐다. 9/14 버그와 같은 종류라 모델명 규칙도 여기로 합친다.
 *
 * 반환값 계약이 바뀐 점에 주의할 것. 구버전은 빈 문자열을 반환했고 지금은 null 을 반환한다.
 * "정규화했더니 검색에 쓸 수 있는 글자가 하나도 안 남았다"와 "빈 문자열로 검색하라"는
 * 전혀 다른 뜻인데 구버전은 둘을 구분하지 못했다.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * 값 없음을 뜻하는 플레이스홀더.
     * 국표원 공표 데이터에서 실제로 관측된 것: "-", "" (빈칸).
     * 나머지는 방어적으로 같이 막는다.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile(
            "^(?:[\\s\\-–—_.·]*|N/?A|NONE|NULL|없음|해당\\s*없음|미상|미기재)$",
            Pattern.CASE_INSENSITIVE);

    /**
     * 괄호로 감싼 라벨 토큰 — 문자열 어디에 있든 제거한다.
     * "MJ21 워터건 / (제품명) MJ21 압축 워터건"(10022636)처럼 문장 중간에도 나온다.
     */
    private static final Pattern BRACKETED_LABEL = Pattern.compile(
            "[(\\[（【]\\s*(?:품번|품명|제품명|상품명|모델명|모델|규격|온라인|온라인몰|"
                    + "STYLE\\s*NO\\.?|MODEL\\s*(?:NO\\.?|NAME)?|ITEM\\s*NO\\.?)\\s*[)\\]）】]",
            Pattern.CASE_INSENSITIVE);

    /** 괄호 없이 맨 앞에 붙은 라벨 — "품번: VDECX01" 형태. 콜론이 있을 때만 떼어 낸다. */
    private static final Pattern LEADING_LABEL = Pattern.compile(
            "^\\s*(?:품번|품명|제품명|상품명|모델명|모델)\\s*[:：]\\s*",
            Pattern.CASE_INSENSITIVE);

    /** 제거 대상 기호·공백. 구버전 목록에 전각 괄호·콜론·대시·물음표를 보탰다. */
    private static final Pattern SYMBOLS = Pattern.compile(
            "[\\s\\-–—_/,.()\\[\\]{}<>（）【】·・~!@#$%^&*+=|\\\\'\"?:：;；]");

    /** 모델명 전용 — 영문·숫자·한글만 남긴다. */
    private static final Pattern NON_ALNUM_HANGUL = Pattern.compile("[^0-9A-Za-z가-힣]");

    /**
     * 후보조회 LIKE 에 쓸 수 있는 최소 길이.
     * 1글자로 LIKE '%X%' 를 걸면 사실상 전건 스캔이라 후보조회의 의미가 사라진다.
     */
    public static final int MIN_SEARCH_LENGTH = 2;

    /**
     * 공백·괄호·특수문자 제거 후 대문자 통일.
     *
     * @return 정규화 결과. 값이 없거나("-", 빈칸 등) 정규화 후 남는 글자가 없으면 <b>null</b>.
     */
    public static String normalize(String raw) {
        String cleaned = stripLabels(raw);
        if (cleaned == null) {
            return null;
        }
        return emptyToNull(SYMBOLS.matcher(cleaned).replaceAll("").toUpperCase(Locale.ROOT));
    }

    /**
     * 모델명 전용 정규화 — 영문·숫자·한글만 남긴다.
     * 일반 normalize() 와 달리 목록에 없는 기호까지 전부 떨어뜨린다.
     *
     * @return 정규화 결과, 또는 남는 글자가 없으면 null
     */
    public static String normalizeModelName(String raw) {
        String cleaned = stripLabels(raw);
        if (cleaned == null) {
            return null;
        }
        return emptyToNull(NON_ALNUM_HANGUL.matcher(cleaned).replaceAll("").toUpperCase(Locale.ROOT));
    }

    /**
     * 후보조회 조건으로 쓸 수 있는 값인가.
     * null 이거나 너무 짧으면 그 조건은 통째로 빼야 한다 — 넣으면 전건에 가깝게 끌려온다.
     */
    public static boolean isSearchable(String normalized) {
        return normalized != null && normalized.length() >= MIN_SEARCH_LENGTH;
    }

    /** 정규화한 뒤 후보조회에 쓸 수 있을 때만 돌려준다. 아니면 null. */
    public static String searchKey(String raw) {
        String normalized = normalize(raw);
        return isSearchable(normalized) ? normalized : null;
    }

    /** 모델명을 정규화한 뒤 후보조회에 쓸 수 있을 때만 돌려준다. 아니면 null. */
    public static String modelSearchKey(String raw) {
        String normalized = normalizeModelName(raw);
        return isSearchable(normalized) ? normalized : null;
    }

    /**
     * 목록 구분자 — 콤마·세미콜론·파이프·슬래시.
     *
     * 9/21 슬래시 추가. 아래 splitList 주석 참조.
     */
    private static final Pattern LIST_DELIMITER = Pattern.compile("[,;|/]");

    /**
     * 목록형 값을 개별 값으로 쪼갠다. <b>원본 전체도 후보에 포함한다.</b>
     *
     * recall_model_name 과 cert_num 은 단일 값이 아니라 목록일 수 있다.
     * 예: "MS116-1.6A / MS116-2.5A / MS116-4.0A / MS116-6.3A / MS116-10A"(10022790).
     * 통째로 비교하면 사용자가 가진 모델 하나("MS116-4.0A")와 일치하지 않는다.
     *
     * ── 9/21 슬래시를 구분자에 넣었다 ──
     * 9/20 에는 일부러 뺐다. "MJ21 워터건 / (제품명) MJ21 압축 워터건"(10022636)처럼
     * 슬래시가 목록이 아니라 한 제품의 다른 표기를 잇는 경우가 있어서, 쪼개면 반쪽짜리 값이
     * 비교 대상이 된다고 봤다.
     *
     * 그 우려는 성립하지 않는다. 호출부(MatchingService.addListComparison)가 조각들 중
     * <b>최고점</b>을 쓰기 때문이다. "MJ21 워터건" 을 입력하면 쪼갠 첫 조각과 1.0 이 나오고,
     * 이는 통째로 비교하는 것보다 높다. 손해가 나는 방향이 아니다.
     *
     * 그래도 원본 전체를 후보 목록에 같이 넣는다. 구분자가 값의 일부인 경우
     * (예: 모델명 자체에 슬래시가 들어간 제품) 원본이 더 높은 점수를 내고, 최고점을 쓰므로
     * 원본이 이긴다. 쪼개서 잃을 수 있는 경우의 수를 수학적으로 0 으로 만든다.
     *
     * @return 쪼갠 조각 + 원본 전체(중복 제외). 입력이 비면 빈 목록.
     */
    public static List<String> splitList(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String token : LIST_DELIMITER.split(raw)) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty() && !result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        String whole = raw.trim();
        if (!whole.isEmpty() && !result.contains(whole)) {
            result.add(whole);
        }
        return result;
    }

    /**
     * 플레이스홀더를 걸러내고 라벨 토큰을 떼어 낸다. 기호 제거는 하지 않는다.
     * 기호를 먼저 지우면 "(품번)" 의 괄호가 사라져서 라벨을 알아볼 수 없게 되므로 순서가 중요하다.
     */
    private static String stripLabels(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || PLACEHOLDER.matcher(s).matches()) {
            return null;
        }
        s = BRACKETED_LABEL.matcher(s).replaceAll(" ");
        s = LEADING_LABEL.matcher(s).replaceAll("");
        s = s.trim();
        if (s.isEmpty() || PLACEHOLDER.matcher(s).matches()) {
            return null;
        }
        return s;
    }

    private static String emptyToNull(String s) {
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
