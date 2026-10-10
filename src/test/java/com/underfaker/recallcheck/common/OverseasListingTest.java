package com.underfaker.recallcheck.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 10/10 — 국외 판매(해외직구·구매대행) 판별. 이 상품은 판단하지 않는다(팀장 결정). */
class OverseasListingTest {

    @Test
    void 상품명이나_KC_칸에_해외직구_구매대행이_있으면_국외_판매() {
        assertEquals("해외직구", OverseasListing.signal("[해외직구] 아기 손목 치발기 세트", null));
        assertEquals("로켓직구", OverseasListing.signal("로켓 직구 유아 식판", null));
        assertEquals("해외구매대행", OverseasListing.signal("아기 치발기", "해외 구매대행 상품으로 KC 인증 대상 아님"));
        assertEquals("구매대행", OverseasListing.signal(null, "구매대행 상품"));
    }

    @Test
    void 국내_수입_판매나_비슷한_국내_표현은_국외_판매가_아니다() {
        // 병행수입은 국내 업체가 들여와 파는 것 — KC 인증 대상이라 판단한다
        assertNull(OverseasListing.signal("병행수입 유모차", "상품 상세페이지 참조"));
        assertNull(OverseasListing.signal("산지직구 사과 5kg", null));
        assertNull(OverseasListing.signal("토이천국 아이놀이터 빠방 밀대 장난감", "CB063R423-5001"));
        assertNull(OverseasListing.signal((String) null));
        assertNull(OverseasListing.signal());
    }

    @Test
    void 부정_표현이_붙으면_국외_판매가_아니다() {
        // 10/10 리뷰 — 판매글에 흔한 표현. 국내 판매(KC 대상)를 판단에서 빼면 안 된다
        assertNull(OverseasListing.signal("국내정품 해외직구 아님 아기 식판"));
        assertNull(OverseasListing.signal("해외직구X 국내배송 유아 의자"));
        assertNull(OverseasListing.signal("[구매대행 NO] 국내 재고 치발기"));
        assertNull(OverseasListing.signal("NOT 해외직구 정식수입 유모차"));
        assertNull(OverseasListing.signal("해외직구 상품 아님"));
        assertNull(OverseasListing.signal("비구매대행 국내 정품"));
        // 부정이 아닌 영문 단어는 그대로 국외 판매
        assertEquals("해외직구", OverseasListing.signal("해외직구 Xbox 컨트롤러"));
    }
}
