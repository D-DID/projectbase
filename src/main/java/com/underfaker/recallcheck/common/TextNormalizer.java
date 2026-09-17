package com.underfaker.recallcheck.common;

import java.util.Locale;

/**
 * 표기 정규화 핵심 로직 — 공백·기호·대소문자.
 *
 * service.matching.FieldNormalizer(매칭 엔진에서 씀)와 entity.Recall(저장 시 normalized_*
 * 컬럼을 채울 때 씀)이 똑같은 규칙을 써야 해서 여기 common 패키지로 뽑아둠. 엔티티는
 * Spring 빈(FieldNormalizer)을 직접 주입받을 수 없어서(JPA가 new로 만드는 객체라
 * 생성자 주입이 안 됨), 순수 static 메서드로 둬서 양쪽 다 이걸 그대로 호출하게 함.
 * 규칙을 바꿀 일이 생기면 여기 한 곳만 고치면 된다 — 두 군데서 따로 관리하다 규칙이
 * 갈라지는 게 이번 버그(정규화 누락)랑 같은 종류의 문제라 이렇게 합쳐둠.
 */

public final class TextNormalizer {

    private TextNormalizer() {
    }

    /** 공백·괄호·특수문자 제거 후 대문자 통일 */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s\\-_/,.()\\[\\]{}<>·・~!@#$%^&*+=|\\\\'\"]", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }
}
