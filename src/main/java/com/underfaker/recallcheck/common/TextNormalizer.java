package com.underfaker.recallcheck.common;

import java.util.Locale;

/**
 * 표기 정규화 규칙의 유일한 소스.
 *
 * 공백·괄호·특수문자를 제거하고 영문·숫자·한글만 남긴 뒤 대문자로 통일한다.
 * entity.Recall#normalizeFields() 와 service.matching.FieldNormalizer 양쪽 모두
 * 이 클래스 하나만 사용해서 규칙이 두 군데로 갈라지지 않게 한다.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^0-9A-Za-z가-힣]", "").toUpperCase(Locale.ROOT);
    }
}