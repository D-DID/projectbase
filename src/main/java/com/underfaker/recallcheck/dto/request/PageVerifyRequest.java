package com.underfaker.recallcheck.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 11/1 — 쇼핑몰 상세페이지 우클릭 검증(크롬 확장 0.7.0 'UnderFaker로 리콜 검증').
 *
 * 강사 피드백(11/1): 입력 방식이 넷(직접 입력·URL·영역 드래그·사진)이라 많다 → 웹은 직접 입력·사진 둘만 두고,
 * 쇼핑몰 상세페이지에서는 우클릭 한 번으로 페이지를 그대로 읽어 검증한다.
 *
 * 서버가 페이지를 받으러 가지 않는다 — 사용자가 이미 보고 있는 페이지의 내용을 확장이 보낸다
 * (쿠팡처럼 서버 접속을 막는 쇼핑몰도 된다. 내부망 주소를 대신 열어 주는 일도 없다).
 *
 * @param url    그 페이지 주소(검증 기록·결과 화면용, 열지 않는다)
 * @param html   페이지 HTML. 서버가 JSON-LD·메타 태그·상품정보 고시 표에서 값을 뽑는다(ProductPageParser — 앞 150만 자만 본다).
 *               확장이 직접 읽은 값(fields)만 보낼 때는 비운다(쿠팡 고시·제품안전정보센터 공표문)
 * @param fields 확장이 그 페이지에서 직접 읽은 값(쿠팡 상세 고시 detail-parser · 공표문 capture-parse). 있으면 HTML 에서 뽑은 값보다 먼저 쓴다
 */
public record PageVerifyRequest(

        @NotBlank
        @Size(max = 1000)
        @Pattern(regexp = "^https?://.+", message = "http 또는 https 로 시작하는 주소여야 합니다.")
        String url,

        @Size(max = 2_000_000)
        String html,

        @Valid
        ManualInputRequest fields
) {
}
