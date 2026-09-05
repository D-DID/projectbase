package com.underfaker.recallcheck.service.matching;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 표기 정규화 — 공백·기호·대소문자.
 *
 * 이 클래스가 있는 이유: 판매글의 "아기 물티슈 캡형 100매"와
 * 공표문의 "가정용 물티슈 / 모델 WT-100C"처럼 같은 제품이 다른 문자열로 표기된다.
 * 정규화 없이 비교하면 매칭률이 0 이 된다.
 *
 * Spring 의존이 없는 순수 로직이라 단위 테스트로 임계값을 튜닝할 수 있다.
 */
@Component
public class FieldNormalizer {

    /** 공백·괄호·특수문자 제거 후 대문자 통일 */
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[\\s\\-_/,.()\\[\\]{}<>·・~!@#$%^&*+=|\\\\'\"]", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    /** 모델명 전용 — 영문·숫자·한글만 남긴다 */
    public String normalizeModelName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^0-9A-Za-z가-힣]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * 콤마로 구분된 목록을 개별 값으로 쪼갠다.
     *
     * recall.recall_model_name 과 recall.cert_num 은 단일 값이 아니라
     * 콤마 구분 목록이다. 통째로 비교하면 절대 일치하지 않는다.
     */
    public List<String> splitList(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String token : raw.split("[,;|]")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
