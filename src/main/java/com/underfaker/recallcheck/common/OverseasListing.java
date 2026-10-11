package com.underfaker.recallcheck.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 10/10 — 국외 판매 상품(해외직구·구매대행) 판별. 이 상품은 판단하지 않는다(팀장 결정 10/10).
 *
 * ── 기준 ──
 * '국외 제품'은 제조국이 아니라 <b>판매 방식</b>으로 나눈다. 해외 판매자가 국내 소비자에게 직접 보내는
 * 해외직구·구매대행 상품만 뺀다. 중국에서 만들었어도 국내 업체가 수입해 파는 제품은 KC 인증 대상이라
 * 그대로 판단한다. 제조국으로 나누면 유아용품 대부분이 빠진다.
 *   단 어린이제품은 빼지 않는다(팀장 확인 10/10) — 어린이제품 안전 특별법 제30조가 판매중개업자·구매·수입대행업자의
 *   KC 표시(안전인증·안전확인·공급자적합성확인) 없는 안전관리대상어린이제품 중개·대행을 금지한다(국가법령정보센터 원문,
 *   시행 2025.10.1). 판정은 VerificationService.excludedOverseas 가 ChildProductSignal 과 함께 한다.
 *
 * ── 판별 근거(잠정) ──
 * 상품명·KC 칸 원문에 "해외직구", "로켓직구", "(해외)구매대행" 이 적혀 있으면 국외 판매로 본다.
 * 쿠팡 주문목록의 '로켓직구' 배지나 상세페이지 표시로 가르는 방법은 화면 실측 전이라 아직 없다(확장 쪽 작업).
 * "병행수입"은 국내 업체가 들여와 파는 것이라 넣지 않는다. 그냥 "직구"는 "산지직구" 같은 국내 표현과 겹쳐서 넣지 않는다.
 * Spring 의존 없음 — 단위 테스트용.
 */
public final class OverseasListing {

    private OverseasListing() {
    }

    /**
     * 부정 표현이 붙은 경우는 뺀다 — 판매글에 흔하다(10/10 리뷰):
     *   "국내정품 해외직구 아님", "해외직구X 국내배송", "[구매대행 NO] 국내 재고", "NOT 해외직구", "비구매대행"
     * 뒤 6자 안의 아님·아닌·아닙·불가·금지·X·NO·❌, 앞의 NOT·non·비 를 본다. 완전하지 않다 — 확장에서 쿠팡
     * '로켓직구' 표시를 직접 읽게 되면 그쪽을 우선한다.
     */
    private static final Pattern SIGNAL = Pattern.compile(
            "(?<!(?i:not|non)[\\s-]{0,2})(?<!비)"
                    + "(?:해외\\s*직구|로켓\\s*직구|(?:해외\\s*)?구매\\s*대행)"
                    + "(?![^,/|\\]\\)]{0,6}?(?:아님|아닌|아닙|아니|불가|금지|❌|✕|✖|(?<![A-Za-z])(?i:x|no)(?![A-Za-z])))");

    /**
     * 국외 판매 표시를 찾는다. 찾으면 그 표현(공백 제거, 예: "해외직구"), 없으면 null.
     *
     * @param texts 상품명, KC 칸 원문 등. null 은 건너뛴다.
     */
    public static String signal(String... texts) {
        if (texts == null) {
            return null;
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            Matcher m = SIGNAL.matcher(text);
            if (m.find()) {
                return m.group().replaceAll("\\s+", "");
            }
        }
        return null;
    }
}
