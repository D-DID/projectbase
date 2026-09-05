package com.underfaker.recallcheck.service.matching;

import org.springframework.stereotype.Component;

/**
 * 표기 정규화 — 공백·기호·대소문자.
 *
 * 이 클래스가 존재하는 이유: 판매글의 "아기 물티슈 캡형 100매"와
 * 공표문의 "가정용 물티슈 / 모델 WT-100C"처럼 같은 제품이 다른 문자열로 표기된다.
 * 정규화 없이 문자열을 비교하면 매칭률이 0 이 된다.
 *
 * Spring 의존이 없는 순수 로직으로 유지할 것 — 임계값 튜닝을 단위 테스트로 돌리기 위함.
 */
@Component
public class FieldNormalizer {

    /** 공백·하이픈·괄호·특수문자를 제거하고 대문자로 통일한다. */
    public String normalize(String raw) {
        // TODO null-safe 처리, 전각/반각 통일, 단위 표기 정리
        throw new UnsupportedOperationException("TODO: FieldNormalizer.normalize");
    }

    /** 모델명 전용 정규화 (영문+숫자만 남김). */
    public String normalizeModelName(String raw) {
        throw new UnsupportedOperationException("TODO: FieldNormalizer.normalizeModelName");
    }
}
