package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.entity.Extraction;

import java.util.Locale;
import java.util.Objects;

/**
 * 10/31 — 검증 이력 중복 정리용 "같은 입력" 판별 키.
 *
 * 같은 쿠팡 주문을 다시 스캔하거나 웹에서 같은 값을 다시 넣으면 예전에는 검증이 매번 새로 쌓였다
 * (10/8 파이프라인 문서 '알려진 한계'). 이제 사용자·채널·이 키가 같으면 기존 검증을 다시 쓴다
 * (VerificationService#verifyByManualInput).
 *
 * 비교 항목: 제품명·브랜드·모델명·제조사·바코드·KC 인증번호, 확장이 읽은 KC 칸 상태·원문.
 * 사진 주소(thumbnailUrl)는 뺀다 — 같은 상품이어도 쿠팡 CDN 크기·확장 버전에 따라 주소가 달라진다.
 * 공백은 하나로 줄이고 앞뒤를 자르며, 영문 대소문자는 구분하지 않는다. 빈 값과 null 은 같다.
 */
public record InputFingerprint(
        String productName,
        String brandName,
        String modelName,
        String makerName,
        String barcodeNum,
        String certNum,
        String kcStatus,
        String kcText
) {

    /** 요청 쪽 — 저장될 때와 같은 규칙(ExtractionNotes.detailBlock)을 한 번 거쳐 KC 칸 값을 맞춘다 */
    public static InputFingerprint of(ManualInputRequest r) {
        if (r == null) {
            return null;
        }
        String detail = ExtractionNotes.detailBlock(r.kcStatus(), r.kcText());
        return new InputFingerprint(norm(r.productName()), norm(r.brandName()), norm(r.modelName()),
                norm(r.makerName()), norm(r.barcodeNum()), norm(r.certNum()),
                norm(ExtractionNotes.kcStatusName(detail)), norm(ExtractionNotes.kcText(detail)));
    }

    /** 저장된 쪽 — 직접입력·확장 검증의 MANUAL 추출 행 */
    public static InputFingerprint of(Extraction e) {
        if (e == null) {
            return null;
        }
        String raw = e.getRawText();
        return new InputFingerprint(norm(e.getProductName()), norm(e.getBrandName()), norm(e.getModelName()),
                norm(e.getMakerName()), norm(e.getBarcodeNum()), norm(e.getCertNum()),
                norm(ExtractionNotes.kcStatusName(raw)), norm(ExtractionNotes.kcText(raw)));
    }

    /** 비교할 값이 하나도 없으면 같은 입력으로 묶지 않는다(빈 검증끼리 합쳐지지 않게) */
    public boolean isBlank() {
        return productName.isEmpty() && brandName.isEmpty() && modelName.isEmpty() && makerName.isEmpty()
                && barcodeNum.isEmpty() && certNum.isEmpty();
    }

    /** null 이면 false. 두 키가 같은 입력인가 */
    public static boolean same(InputFingerprint a, InputFingerprint b) {
        return a != null && b != null && Objects.equals(a, b);
    }

    static String norm(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
