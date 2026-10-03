package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.dto.internal.KcLookup;

/**
 * 10/3 추가 — 검증 결과에 싣는 KC 인증 DB 조회 결과(KC인증 연동).
 *
 * 화면 문구 원칙(9/30 팀 결정과 같음): KC 인증이 있다고 "안전"하다는 뜻이 아니다. 인증상태는 원문 그대로 보여 주고,
 * 리콜 판정(일치/의심/불일치)과는 별개 정보로 표시한다.
 *
 * @param status          FOUND / NOT_FOUND(인증 DB 에 그 번호가 없음) / UNAVAILABLE(조회 실패)
 * @param certNum         조회한 인증번호(정리된 값)
 * @param certState       인증상태 원문 — "적합", "기간만료", "안전인증취소", "사용금지 2개월" 등
 * @param needsAttention  인증상태가 "적합"이 아니면 true (화면에서 주의 표시)
 * @param certDate        인증일 yyyyMMdd
 * @param modelName       인증받은 모델명
 * @param makerName       제조사("-" 그대로 올 수 있음)
 */
public record KcCertResponse(
        String status,
        String certNum,
        String certState,
        boolean needsAttention,
        String certDate,
        String productName,
        String modelName,
        String makerName,
        String makerCntryName,
        String source
) {

    public static KcCertResponse of(KcLookup kc) {
        if (kc == null) {
            return null;
        }
        return new KcCertResponse(
                kc.status() == null ? null : kc.status().name(),
                kc.certNum(), kc.certState(), kc.stateNeedsAttention(), kc.certDate(),
                kc.productName(), kc.modelName(), kc.makerName(), kc.makerCntryName(), kc.source());
    }
}
