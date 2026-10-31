package com.underfaker.recallcheck.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 10/30 — 쿠팡 썸네일 주소를 큰 사진 주소로 바꾼다('사진으로 찾기' 정확도).
 *
 * 실측(10/30, 바스락바스락 아기 헝겊책): 주문목록 썸네일이 6,530바이트 webp 라 Google Vision 이 같은 사진을
 * 한 곳도 못 찾았다(일치 페이지 50 중 '같은 사진' 0, 비슷한 사진만 — 교보문고 헝겊책 15곳).
 * 쿠팡 CDN 주소는 {@code …coupangcdn.com/thumbnails/remote/120x120ex/image/…} 처럼 크기가 경로에 들어 있어
 * 크기만 바꾸면 같은 사진의 큰 판이 나온다 [확실하지 않음 — 크기 값이 허용되는지는 CDN 실측 전].
 * 그래서 호출부(GoogleVisionClient.annotateRemote)는 큰 주소를 먼저 받아 보고, 못 받으면 원래 주소로 돌아간다.
 * Spring 의존 없음 — 단위 테스트용.
 */
public final class CoupangThumbnail {

    private CoupangThumbnail() {
    }

    /** 바꿀 크기 — 쿠팡 상품 페이지 대표 사진에서 흔히 보이는 값 */
    public static final int LARGE = 492;

    private static final Pattern SIZE = Pattern.compile(
            "^(https?://[A-Za-z0-9.-]*coupangcdn\\.com/thumbnails/remote/)(\\d{2,4})x(\\d{2,4})([a-z]{0,2})(/.+)$");

    /**
     * 큰 사진 주소. 쿠팡 썸네일 꼴이 아니거나 이미 충분히 크면 받은 주소 그대로.
     */
    public static String larger(String url) {
        if (url == null) {
            return null;
        }
        Matcher m = SIZE.matcher(url.trim());
        if (!m.matches()) {
            return url;
        }
        int w = Integer.parseInt(m.group(2));
        int h = Integer.parseInt(m.group(3));
        if (w >= LARGE && h >= LARGE) {
            return url;
        }
        return m.group(1) + LARGE + "x" + LARGE + "ex" + m.group(5);
    }
}
