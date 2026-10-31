package com.underfaker.recallcheck.dto.response;

import com.underfaker.recallcheck.dto.internal.ExtractionNotes;
import com.underfaker.recallcheck.entity.Extraction;

/**
 * 10/31 — 결과 화면 '내가 입력한 제품'.
 * 예전에는 결과 응답에 입력값이 없어서 화면이 판정 근거(comparisons)의 inputValue 로 채웠는데,
 * 대조할 리콜 후보가 없으면 근거가 비어 입력값이 전부 '-'로 나왔다(10/30 헝겊책 실측).
 * 저장된 입력(extraction 의 MANUAL 행)을 그대로 내려준다.
 *
 * @param certNum KC 인증번호 — KC 칸 원문에 있던 번호까지 모은 값(대조에 실제로 쓴 값)
 * @param kcStatus 확장·URL 읽기가 판단한 KC 칸 상태(DISCLOSED·REFERENCED·NONE·UNREADABLE). 직접 입력이면 null
 * @param kcText KC 칸 원문. 직접 입력이면 null
 */
public record ProductInputResponse(
        String productName,
        String brandName,
        String modelName,
        String makerName,
        String barcodeNum,
        String certNum,
        String thumbnailUrl,
        String kcStatus,
        String kcText
) {

    public static ProductInputResponse of(Extraction e) {
        if (e == null) {
            return null;
        }
        String raw = e.getRawText();
        return new ProductInputResponse(
                e.getProductName(), e.getBrandName(), e.getModelName(), e.getMakerName(),
                e.getBarcodeNum(), e.getCertNum(), e.getThumbnailUrl(),
                ExtractionNotes.kcStatusName(raw), ExtractionNotes.kcText(raw));
    }
}
