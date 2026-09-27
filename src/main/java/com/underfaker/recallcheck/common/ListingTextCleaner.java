package com.underfaker.recallcheck.common;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 판매글 상품명에서 매칭을 방해하는 문구를 걷어낸다.
 *
 * 쿠팡 주문목록에서 오는 상품명은 공표문의 상품명과 형태가 다르다. 확장은
 * <code>img[alt]</code> 를 그대로 읽어 보내는데, 그 값에는 제품 식별과 무관한 판매 문구가
 * 잔뜩 섞여 있다. 예를 들면 이런 것들이다.
 *
 *   [무료배송] 아트박스 허니 슬라임 100g, 3개, 옐로우
 *   (1+1) 리틀뎁 아동 팬츠 2개입 특가
 *
 * 공표문 쪽은 "(제품명) 허니 슬라임" 처럼 짧다. 이 상태로 편집거리를 재면 길이 차이만으로
 * 점수가 깎여서, 같은 제품인데도 NO_MATCH 로 떨어진다.
 *
 * <b>원본을 버리지 않는다.</b> 정제는 비교용 후보를 하나 더 만드는 것이고, 정제 결과가
 * 지나치게 짧아지면(식별에 쓸 글자가 안 남으면) 원본을 그대로 쓴다. 판매 문구를 지우려다
 * 제품명까지 날리는 쪽이 더 나쁘기 때문이다.
 *
 * ※ 쿠팡 alt 의 정확한 포맷은 배포마다 달라질 수 있다. 여기 규칙은 9/17 확장 실측 주석과
 *   일반적인 판매글 표기를 근거로 한 것이고, 실제 수집 데이터를 보고 조정해야 한다.
 */
public final class ListingTextCleaner {

    private ListingTextCleaner() {
    }

    /** 정제 후 최소한 이만큼은 남아야 정제본을 쓴다. 안 남으면 원본으로 되돌린다. */
    private static final int MIN_USEFUL_LENGTH = 2;

    /** 앞머리 대괄호·소괄호 홍보 블록 — [무료배송], (1+1), 【특가】 */
    private static final Pattern PROMO_BLOCK = Pattern.compile(
            "[\\[\\(【][^\\]\\)】]{0,20}[\\]\\)】]");

    /** 수량·용량·규격만으로 이루어진 세그먼트 — "3개", "100g", "500ml", "2개입", "1세트" */
    private static final Pattern QUANTITY_SEGMENT = Pattern.compile(
            "^\\s*\\d+\\s*(개입|개|매|장|팩|세트|박스|묶음|입|g|kg|ml|l|mm|cm|호|년|주|개월)?\\s*$",
            Pattern.CASE_INSENSITIVE);

    /** 문장 안에 박혀 있는 수량·용량 토큰 */
    private static final Pattern QUANTITY_TOKEN = Pattern.compile(
            "(?<![0-9A-Za-z가-힣])\\d+(\\.\\d+)?\\s*(개입|개|매|장|팩|세트|박스|묶음|입|g|kg|ml|l)(?![0-9A-Za-z가-힣])",
            Pattern.CASE_INSENSITIVE);

    /** "1+1", "2+1" 같은 증정 표기 */
    private static final Pattern PLUS_DEAL = Pattern.compile("\\d\\s*\\+\\s*\\d");

    /**
     * 제품 식별과 무관한 판매 상용어.
     * 색상·사이즈는 넣지 않았다 — 같은 제품의 다른 옵션을 구분해야 할 수도 있어서다.
     */
    private static final String[] SELLING_WORDS = {
            "무료배송", "당일발송", "당일출고", "로켓배송", "로켓프레시", "새벽배송",
            "정품", "공식", "공식판매", "본사직영", "사은품", "증정", "덤",
            "특가", "최저가", "할인", "세일", "행사", "기획", "한정", "품절임박",
            "베스트", "인기", "추천", "신상", "신상품", "리뉴얼", "국내배송"
    };

    /**
     * 판매글 상품명을 매칭에 쓰기 좋게 정제한다.
     *
     * @return 정제된 상품명. 입력이 비었거나 정제 후 쓸 글자가 안 남으면 원본을 trim 해서 돌려준다.
     *         입력이 null 이면 null.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        String original = raw.trim();
        if (original.isEmpty()) {
            return null;
        }

        String s = PROMO_BLOCK.matcher(original).replaceAll(" ");
        s = PLUS_DEAL.matcher(s).replaceAll(" ");

        // 콤마로 나뉜 옵션 세그먼트 중 수량·용량만 있는 것을 버린다.
        // "허니 슬라임 100g, 3개, 옐로우" → "허니 슬라임 100g" + "옐로우"
        List<String> kept = new ArrayList<>();
        for (String segment : s.split(",")) {
            String seg = segment.trim();
            if (seg.isEmpty() || QUANTITY_SEGMENT.matcher(seg).matches()) {
                continue;
            }
            kept.add(seg);
        }
        s = String.join(" ", kept);

        s = QUANTITY_TOKEN.matcher(s).replaceAll(" ");
        for (String word : SELLING_WORDS) {
            s = s.replace(word, " ");
        }
        s = s.replaceAll("\\s{2,}", " ").trim();

        // 정제하다 제품명까지 날린 경우 — 원본을 쓴다.
        if (TextNormalizer.normalize(s) == null
                || TextNormalizer.normalize(s).length() < MIN_USEFUL_LENGTH) {
            return original;
        }
        return s;
    }

    /**
     * 비교에 쓸 후보들 — 원본과 정제본.
     *
     * 둘 다 넘겨서 점수가 높은 쪽을 쓰는 용도다. 정제가 항상 이득은 아니라서
     * (공표문 쪽이 오히려 긴 상품명인 경우가 있다) 한쪽만 고르지 않는다.
     *
     * @return 중복을 뺀 후보 배열. 입력이 비면 빈 배열.
     */
    public static String[] candidates(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[0];
        }
        String original = raw.trim();
        String cleaned = clean(raw);
        if (cleaned == null || cleaned.equals(original)) {
            return new String[]{original};
        }
        return new String[]{cleaned, original};
    }
}
