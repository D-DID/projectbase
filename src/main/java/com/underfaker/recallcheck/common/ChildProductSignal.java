package com.underfaker.recallcheck.common;

import java.util.regex.Pattern;

/**
 * 10/10 — 어린이제품으로 보이는 상품인가(상품명·KC 칸 원문 기준).
 *
 * 쓰는 곳 — 해외직구·구매대행 상품을 판단에서 뺄지 정할 때(VerificationService).
 * 어린이제품은 구매대행·판매중개도 KC 표시(안전인증·안전확인·공급자적합성확인)가 없으면 금지라서
 * 해외직구·구매대행이어도 판단한다. 근거: 어린이제품 안전 특별법 제30조(중개 및 구매·수입대행의 금지,
 * 시행 2025.10.1, 법률 제21065호 — 팀장이 국가법령정보센터 원문 확인 10/10),
 * 제4차 어린이제품 안전관리 기본계획(2025.1, 국가기술표준원) 2-③ "KC 미인증 구매대행 어린이제품 등 불법제품 안전관리 강화".
 *
 * 넓게 잡는다 — 어린이제품이 아닌데 어린이제품으로 보면 판단 대상에 들어갈 뿐이지만(손해 적음),
 * 어린이제품을 놓치면 법 위반 가능성이 있는 상품을 판단에서 빼게 된다. "강아지 장난감" 같은 오탐은 감수한다.
 * Spring 의존 없음 — 단위 테스트용.
 */
public final class ChildProductSignal {

    private ChildProductSignal() {
    }

    private static final Pattern CHILD = Pattern.compile(
            "어린이|유아|아기|아가|영아|영유아|신생아|아동|키즈|베이비|완구|장난감|치발기|딸랑이|유모차|보행기|"
                    + "카시트|아기띠|젖병|쪽쪽이|공갈\\s*젖꼭지|걸음마|(?i:\\bbaby\\b|\\bkids?\\b|\\btoddler\\b|\\binfant\\b)");

    /** 어린이제품으로 보이면 true. */
    public static boolean likely(String... texts) {
        if (texts == null) {
            return false;
        }
        for (String text : texts) {
            if (text != null && !text.isBlank() && CHILD.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }
}
