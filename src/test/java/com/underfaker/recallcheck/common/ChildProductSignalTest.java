package com.underfaker.recallcheck.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 10/10 — 어린이제품은 해외직구·구매대행이어도 판단한다(어린이제품법 제30조). 그 판별. */
class ChildProductSignalTest {

    @Test
    void 상품명이나_KC_칸에_어린이제품_표현이_있으면_어린이제품() {
        assertTrue(ChildProductSignal.likely("[해외직구] 퍼기 큐피드 손목 치발기 세트"));
        assertTrue(ChildProductSignal.likely("해외구매대행 Baby Walker", null));
        assertTrue(ChildProductSignal.likely("구매대행 식판", "카테고리상 어린이제품(출산/유아동>유아식기)인데 번호 없음"));
        assertTrue(ChildProductSignal.likely("토이천국 아이놀이터 빠방 밀대 장난감"));
    }

    @Test
    void 어린이제품_표현이_없으면_아니다() {
        assertFalse(ChildProductSignal.likely("해외직구 블루투스 스피커", "구매대행 상품"));
        assertFalse(ChildProductSignal.likely("Babybel cheese"));   // 영어 단어 안의 baby 는 보지 않는다
        assertFalse(ChildProductSignal.likely((String) null));
    }
}
