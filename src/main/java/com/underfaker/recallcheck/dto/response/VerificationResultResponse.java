package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.entity.enums.FinalResult;

import java.time.LocalDateTime;

/**
 * FR-013 검증 결과 — 판정 결과, 리콜 사유, 위해 정보, 소비자 행동요령, 공표일.
 * 판정을 단정하지 않고 4단계로 구분해 반환하며, 근거를 함께 제공한다.
 */
public record VerificationResultResponse(

        Long verificationId,
        FinalResult finalResult,
        Double similarityScore,

        /* 매칭된 공식 리콜 정보 (NO_MATCH·UNKNOWN 이면 null) */
        Long recallUid,
        String recallProductName,
        String recallBrandName,
        String recallModelName,
        String recallTypeName,
        String recallMeans,
        String recallCmpnyName,
        String makerName,
        /** 제품 결함 */
        String harmDscr,
        /** 위해 정보 */
        String accidentCaseDscr,
        /** 소비자 행동요령 */
        String publishActionDscr,
        /** 공표일 yyyyMMdd */
        String publishDate,

        LocalDateTime createdAt,

        /* 9/27 — 화면 4단계 상태와 사진 확인 버튼 (dto.internal.ResultView 참조) */
        String resultState,
        String imageCheck,
        Boolean imageCheckAvailable,
        String missingReason,

        /* 10/3 — KC 인증 DB 조회 결과(KC인증 연동). 인증번호를 안 넣었거나 10/3 이전 검증이면 null */
        KcCertResponse kc,

        /* 10/7 — 사진으로 찾은 상품명(같은 사진이 실린 서로 다른 사이트 2곳 이상에서 반복). 없으면 null */
        String imageProductName,

        /* 10/10 — 사진으로 찾은 페이지 본문에서 서로 다른 사이트 2곳 이상 같은 KC 인증번호·모델명(제품번호). 없으면 null */
        String imageCertNum,
        String imageModelName,

        /* 10/31 — 결과 화면 '내가 입력한 제품'. 리콜 후보가 없어도(불일치) 입력값은 보여야 한다. 입력 행이 없으면 null */
        ProductInputResponse input,

        /* 10/31 — 매칭된 공표문의 KC 인증번호(콤마 구분 목록일 수 있음). 대조 축이 제품명 + KC 인증번호라 화면에 나란히 보인다 */
        String recallCertNum
) {

    /** 10/31 이전 형태(입력값·공표문 인증번호 없음) 호환용. */
    public VerificationResultResponse(Long verificationId, FinalResult finalResult, Double similarityScore,
                                      Long recallUid, String recallProductName, String recallBrandName,
                                      String recallModelName, String recallTypeName, String recallMeans,
                                      String recallCmpnyName, String makerName, String harmDscr,
                                      String accidentCaseDscr, String publishActionDscr, String publishDate,
                                      LocalDateTime createdAt, String resultState, String imageCheck,
                                      Boolean imageCheckAvailable, String missingReason, KcCertResponse kc,
                                      String imageProductName, String imageCertNum, String imageModelName) {
        this(verificationId, finalResult, similarityScore, recallUid, recallProductName, recallBrandName,
                recallModelName, recallTypeName, recallMeans, recallCmpnyName, makerName, harmDscr,
                accidentCaseDscr, publishActionDscr, publishDate, createdAt, resultState, imageCheck,
                imageCheckAvailable, missingReason, kc, imageProductName, imageCertNum, imageModelName, null, null);
    }

    /** 10/10 이전 형태(사진 KC 번호·모델명 없음) 호환용. */
    public VerificationResultResponse(Long verificationId, FinalResult finalResult, Double similarityScore,
                                      Long recallUid, String recallProductName, String recallBrandName,
                                      String recallModelName, String recallTypeName, String recallMeans,
                                      String recallCmpnyName, String makerName, String harmDscr,
                                      String accidentCaseDscr, String publishActionDscr, String publishDate,
                                      LocalDateTime createdAt, String resultState, String imageCheck,
                                      Boolean imageCheckAvailable, String missingReason, KcCertResponse kc,
                                      String imageProductName) {
        this(verificationId, finalResult, similarityScore, recallUid, recallProductName, recallBrandName,
                recallModelName, recallTypeName, recallMeans, recallCmpnyName, makerName, harmDscr,
                accidentCaseDscr, publishActionDscr, publishDate, createdAt, resultState, imageCheck,
                imageCheckAvailable, missingReason, kc, imageProductName, null, null, null, null);
    }

    /** 10/7 이전 형태(사진 상품명 없음) 호환용 — 다른 호출부가 있어도 컴파일이 깨지지 않게 둔다. */
    public VerificationResultResponse(Long verificationId, FinalResult finalResult, Double similarityScore,
                                      Long recallUid, String recallProductName, String recallBrandName,
                                      String recallModelName, String recallTypeName, String recallMeans,
                                      String recallCmpnyName, String makerName, String harmDscr,
                                      String accidentCaseDscr, String publishActionDscr, String publishDate,
                                      LocalDateTime createdAt, String resultState, String imageCheck,
                                      Boolean imageCheckAvailable, String missingReason, KcCertResponse kc) {
        this(verificationId, finalResult, similarityScore, recallUid, recallProductName, recallBrandName,
                recallModelName, recallTypeName, recallMeans, recallCmpnyName, makerName, harmDscr,
                accidentCaseDscr, publishActionDscr, publishDate, createdAt, resultState, imageCheck,
                imageCheckAvailable, missingReason, kc, null, null, null, null, null);
    }
}
