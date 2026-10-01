package com.underfaker.recallcheck.service.sync;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 품목명 키워드 적재(syncByKeywords)의 조회 대기열. Spring 의존 없음 — 단위 테스트용으로 분리.
 *
 * ── 10/1 추가 ──
 * 공표일(publishDate) 조회는 2023-07-12 이후만 나온다(2022-01 은 0건, 2023-07 은 12일부터).
 * 품목명(recallProductName) 조회는 부분 일치라서 "완구" 하나로 2012-03-05 공표분까지 나온다.
 * 그래서 과거분은 날짜가 아니라 품목명으로 훑는다. 다만 목록 API 는 1회 1,000건 상한이
 * 있고 페이징이 없으므로, 넓은 키워드가 상한에 걸리면 그 결과에서 나온 품목명으로 더
 * 잘게 다시 조회해야 한다. 이 클래스가 그 순서를 관리한다.
 *
 * 건너뛰기(prune) 규칙:
 *   키워드 Q 가 상한에 안 걸리고 끝났다면(=Q 가 들어간 품목명은 전부 받았다면),
 *   Q 를 포함하는 더 긴 키워드 K("완구" → "기타완구")는 새로 줄 게 없다.
 *   K 가 들어간 품목명은 Q 도 들어가 있기 때문이다. 이 규칙이 호출 수를 크게 줄인다.
 *   전제는 API 의 품목명 조회가 순수한 부분 일치라는 것 — 아니라면 prune=false 로 끈다.
 */
final class KeywordQueue {

    /** 맨 앞 괄호 머리말은 떼어 낸다. "(직)전기방석(…)" → "전기방석(…)" */
    private static final Pattern LEADING_BRACKET = Pattern.compile("^(?:\\s*[(\\[（【][^)\\]）】]*[)\\]）】])+");

    /** 품목명에서 키워드를 뽑을 때 괄호 뒤는 버린다. "기타완구(완구)" → "기타완구" */
    private static final Pattern BRACKET_TAIL = Pattern.compile("[(\\[（【].*$");

    /** 한글·영문·숫자가 아닌 문자로 쪼갠다. "전기냉장*냉동기기" → [전기냉장, 냉동기기] */
    private static final Pattern NON_WORD = Pattern.compile("[^가-힣A-Za-z0-9]+");

    /** 쿼리 문자열에서 뜻이 바뀌는 문자. '+' 는 공백으로 읽힐 수 있다. */
    private static final Pattern UNSAFE = Pattern.compile("[+&=#%?/\\\\]");

    /** 너무 넓어서 키워드로 의미가 없는 말 */
    private static final Set<String> STOP_WORDS = Set.of(
            "기타", "제품", "용품", "기기", "부품", "기구", "일반", "전용", "세트", "해당");

    private final Deque<String> queue = new ArrayDeque<>();
    private final Set<String> known = new HashSet<>();
    private final List<String> complete = new ArrayList<>();
    private final boolean prune;
    private int skipped;

    KeywordQueue(boolean prune) {
        this.prune = prune;
    }

    /** 대기열 끝에 넣는다. 이미 넣은 적 있는 키워드면 false. */
    boolean offer(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return false;
        }
        String k = keyword.trim();
        if (!known.add(k)) {
            return false;
        }
        queue.addLast(k);
        return true;
    }

    /** 다음에 조회할 키워드. 이미 받은 키워드에 포함되는 건 건너뛴다. 없으면 null. */
    String next() {
        while (!queue.isEmpty()) {
            String k = queue.pollFirst();
            if (prune && coveredBy(k) != null) {
                skipped++;
                continue;
            }
            return k;
        }
        return null;
    }

    /** 조회가 성공했고 상한(1,000건)에 안 걸렸다 — 이 키워드가 들어간 품목명은 전부 받았다. */
    void markComplete(String keyword) {
        complete.add(keyword);
    }

    /** k 를 이미 덮는(=k 의 부분 문자열인) 완료 키워드. 없으면 null. */
    String coveredBy(String k) {
        for (String c : complete) {
            if (k.contains(c)) {
                return c;
            }
        }
        return null;
    }

    int skipped() {
        return skipped;
    }

    /** 아직 조회하지 않은 키워드(건너뛸 것은 빼고). maxCalls 에 걸렸을 때 응답에 싣는다. */
    List<String> remaining() {
        List<String> out = new ArrayList<>();
        for (String k : queue) {
            if (!prune || coveredBy(k) == null) {
                out.add(k);
            }
        }
        return out;
    }

    /**
     * 사용자가 넘긴 키워드 정리. 앞뒤 공백만 떼고, 쿼리에서 뜻이 바뀌는 문자가 있으면 버린다.
     * 사람이 고른 키워드라 공백("아동용 섬유제품")은 그대로 둔다.
     */
    static String cleanUserKeyword(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty() || UNSAFE.matcher(s).find()) {
            return null;
        }
        return s;
    }

    /**
     * 조회 결과의 품목명(recall_product_name)에서 다음 조회용 키워드를 뽑는다.
     *
     *   "기타완구(완구)"                   → "기타완구"
     *   "중의류(아섬)(아동용 섬유제품)"    → "중의류"
     *   "전지(전지(충전지만 해당))"        → "전지"
     *   "전기냉장*냉동기기(와인셀러)"      → "전기냉장"   (특수문자로 쪼개 가장 긴 조각, 같으면 앞)
     *   "자동차 타이어"                    → "자동차"
     *   "(직)전기방석(직류전원을 …)"       → "전기방석"   (맨 앞 괄호 머리말은 떼고 본다)
     *
     * 결과는 항상 원래 품목명의 부분 문자열이다. 그래야 부분 일치 조회로 원래 행이 다시 걸린다.
     * 한 글자, 불용어(기타·제품 …), 두 글자 이하 영문·숫자는 버린다(null).
     */
    static String keywordOf(String productName) {
        if (productName == null) {
            return null;
        }
        String body = LEADING_BRACKET.matcher(productName).replaceFirst("");
        String head = BRACKET_TAIL.matcher(body).replaceFirst("").trim();
        String best = null;
        for (String part : NON_WORD.split(head)) {
            if (best == null || part.length() > best.length()) {
                best = part;
            }
        }
        if (best == null || best.length() < 2 || STOP_WORDS.contains(best)) {
            return null;
        }
        boolean hasHangul = best.chars().anyMatch(c -> c >= '가' && c <= '힣');
        if (!hasHangul && best.length() < 3) {
            return null;
        }
        return best;
    }
}
