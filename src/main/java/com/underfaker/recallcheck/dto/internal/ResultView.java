package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;

/**
 * 화면에 보여 줄 4단계 상태 — 9/27 추가.
 *
 * 팀장 결정(9/27): 결과는 [일치 / 의심 / 항목누락 / 불일치].
 *   MATCH    일치     — 100% 확정 근거(인증번호·모델명 완전일치 또는 전 항목 100%)
 *   PARTIAL  의심     — 확정은 아니지만 공표문과 닮았다
 *   MISSING  항목누락 — 텍스트로는 못 찾았고, KC 인증정보가 필요한 품목인데 번호를 못 얻었다
 *                       → "사진으로 찾기" 버튼(사용자가 누를 때만 Vision)
 *   NO_MATCH 불일치
 *   UNKNOWN  확인불가 — 비교할 정보가 아예 없다(기존 그대로)
 *
 * 항목누락은 DB 에 판정값으로 저장하지 않는다(final_result 가 MySQL ENUM 이라 값을 늘리면 INSERT 실패).
 * 저장된 판정 + extraction.raw_text 메모(ExtractionNotes)로 여기서 계산한다.
 *
 * @param resultState         MATCH / PARTIAL / MISSING / NO_MATCH / UNKNOWN
 * @param imageCheck          사진 확인 결과 FOUND / NONE / FAILED, 안 했으면 null
 * @param imageCheckAvailable "사진으로 찾기" 버튼을 보여 줄지
 * @param missingReason       항목누락 사유(화면 안내용). 해당 없으면 null
 */
public record ResultView(String resultState, String imageCheck, boolean imageCheckAvailable,
                         String missingReason) {

    public static ResultView of(FinalResult finalResult, VerificationChannel channel,
                                String rawText, String thumbnailUrl) {
        ExtractionNotes.ImageCheck check = ExtractionNotes.imageCheck(rawText);
        // 판독 호출이 실패(FAILED)했으면 안 한 것으로 본다 — 다시 누를 수 있게.
        boolean checked = check == ExtractionNotes.ImageCheck.FOUND || check == ExtractionNotes.ImageCheck.NONE;
        boolean missing = ExtractionNotes.infoMissing(rawText);
        boolean hasThumb = thumbnailUrl != null && !thumbnailUrl.isBlank();

        String state;
        if (finalResult == null) {
            state = null;
        } else if (finalResult == FinalResult.NO_MATCH && missing && !checked) {
            state = "MISSING";
        } else {
            state = finalResult.name();
        }

        // 버튼 조건 — 사진이 있고, 아직 안 눌렀고, 이미 일치로 확정된 게 아닐 때.
        // 쿠팡 구매이력(EXTENSION)은 항목누락일 때만(의심이어도 KC 번호가 없으면 포함),
        // 웹 직접입력(WEB)은 사진 주소를 넣은 경우 언제나.
        boolean available = hasThumb && !checked
                && finalResult != null && finalResult != FinalResult.MATCH && finalResult != FinalResult.UNKNOWN
                && (missing || channel == VerificationChannel.WEB);

        return new ResultView(state, check == null ? null : check.name(), available,
                missing ? missingReason(rawText) : null);
    }

    private static String missingReason(String rawText) {
        ExtractionNotes.KcStatus s = ExtractionNotes.kcStatus(rawText);
        if (s == ExtractionNotes.KcStatus.UNREADABLE) {
            return "쿠팡 상세페이지를 읽지 못해 KC 인증정보를 확인하지 못했습니다.";
        }
        String kcText = ExtractionNotes.kcText(rawText);
        return "쿠팡 상세페이지의 KC 인증정보에 번호가 없습니다"
                + (kcText == null ? "." : " (\"" + kcText + "\").");
    }
}
