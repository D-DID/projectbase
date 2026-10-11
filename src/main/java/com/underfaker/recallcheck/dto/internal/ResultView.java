package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.common.KcCertNumbers;
import com.underfaker.recallcheck.entity.enums.FinalResult;
import com.underfaker.recallcheck.entity.enums.VerificationChannel;

/**
 * 화면에 보여 줄 상태 — 9/27 추가, 10/10 최종 흐름 반영.
 *
 * ── 최종 흐름(팀장 결정 10/10) ──
 *   판단 범위: 국내에서 파는 공산품·유아제품(KC 인증 필수). (어린이제품이 아닌) 해외직구·구매대행 상품은 판단하지 않는다 —
 *             단 어린이제품은 구매대행도 KC 표시가 필수라(어린이제품법 제30조) 판단한다.
 *   1차: KC 인증번호 + 제품명으로 판단
 *   2차: 1차로 판단할 수 없으면(KC 번호를 못 얻음) 사진을 Vision 으로 웹에서 찾아, 여러 사이트에서 반복된
 *        KC 인증번호·제품명·제품번호로 다시 판단 — 사용자가 "사진으로 찾기"를 누를 때만, 결과는 최대 '의심'
 *
 *   MATCH    일치     — 100% 확정 근거(판매자가 적은 인증번호·모델명 완전일치 또는 전 항목 100%)
 *   PARTIAL  의심     — 확정은 아니지만 공표문과 닮았다(사진 찾기 결과는 여기까지)
 *   MISSING  KC 번호 미표기(9/27~10/9 '항목누락') — 텍스트로 못 찾았고 KC 인증번호도 못 얻어 KC 번호로 판단할 수 없다
 *                       → "사진으로 찾기" 버튼. 사진 확인 뒤에도 공표문을 못 찾았으면 그대로 유지(10/7)
 *   NO_MATCH 불일치
 *   UNKNOWN  확인불가 — 비교할 정보가 아예 없다
 *   EXCLUDED 판단 제외(10/10) — (어린이제품이 아닌) 해외직구·구매대행 상품. DB 에는 UNKNOWN 으로 저장된다
 *
 * 'KC 번호 없음'을 '불법'으로 표시하지 않는다(10/10). 어린이제품 KC 중 공급자적합성확인은 사업자가 스스로
 * 확인하는 제도라 인증기관 번호가 없을 수 있고(리콜 공표문 인증번호 칸에도 "공급자적합성" 141건),
 * "상품 상세페이지 참조"는 번호가 상세 이미지 안에 있을 수 있다. 사실(번호 미표기)만 보여 준다.
 *
 * MISSING·EXCLUDED 는 DB 에 판정값으로 저장하지 않는다(final_result 가 MySQL ENUM 이라 값을 늘리면 INSERT 실패).
 * 저장된 판정 + extraction.raw_text 메모(ExtractionNotes)로 여기서 계산한다.
 *
 * @param resultState         MATCH / PARTIAL / MISSING / NO_MATCH / UNKNOWN / EXCLUDED
 * @param imageCheck          사진 확인 결과 FOUND / NONE / FAILED, 안 했으면 null
 * @param imageCheckAvailable "사진으로 찾기" 버튼을 보여 줄지
 * @param missingReason       KC 번호 미표기·판단 제외 사유(화면 안내용). 해당 없으면 null
 * @param imageProductName    10/7 — 사진으로 찾은 상품명(여러 사이트에서 반복된 것). 사진 확인을 안 했거나 못 찾았으면 null
 * @param imageCertNum        10/10 — 사진으로 찾은 페이지들에서 반복된 KC 인증번호. 없으면 null
 * @param imageModelName      10/10 — 사진으로 찾은 페이지들에서 반복된 모델명(제품번호). 없으면 null
 */
public record ResultView(String resultState, String imageCheck, boolean imageCheckAvailable,
                         String missingReason, String imageProductName,
                         String imageCertNum, String imageModelName) {

    /** 10/7 이전 형태 호환용. */
    public ResultView(String resultState, String imageCheck, boolean imageCheckAvailable, String missingReason) {
        this(resultState, imageCheck, imageCheckAvailable, missingReason, null, null, null);
    }

    /** 10/10 이전 형태(사진 KC 번호·모델명 없음) 호환용. */
    public ResultView(String resultState, String imageCheck, boolean imageCheckAvailable, String missingReason,
                      String imageProductName) {
        this(resultState, imageCheck, imageCheckAvailable, missingReason, imageProductName, null, null);
    }

    /** 10/10 — 해외직구·구매대행이라 판단하지 않은 건의 화면 상태 */
    public static final String EXCLUDED = "EXCLUDED";

    /** 10/10 — KC 번호로 판단할 수 없는 건(화면 표시: KC 번호 미표기) */
    public static final String MISSING = "MISSING";

    /**
     * @param certNum 판정에 쓴 KC 인증번호(extraction.cert_num). 번호 모양이 하나도 없으면 'KC 번호 없음'으로 본다
     */
    public static ResultView of(FinalResult finalResult, VerificationChannel channel,
                                String rawText, String thumbnailUrl, String certNum) {
        // 10/10 — 국외 판매(해외직구·구매대행)는 판단하지 않는다. 사진 확인도 하지 않는다.
        if (ExtractionNotes.excludedOverseas(rawText)) {
            String signal = ExtractionNotes.overseasSignal(rawText);
            return new ResultView(finalResult == null ? null : EXCLUDED, null, false,
                    "(어린이제품이 아닌) 해외직구·구매대행 상품" + (signal == null ? "" : "(\"" + signal + "\" 표시)")
                            + "이라 판단하지 않습니다.",
                    null, null, null);
        }

        ExtractionNotes.ImageCheck check = ExtractionNotes.imageCheck(rawText);
        // 판독 호출이 실패(FAILED)했으면 안 한 것으로 본다 — 다시 누를 수 있게.
        boolean checked = check == ExtractionNotes.ImageCheck.FOUND || check == ExtractionNotes.ImageCheck.NONE;
        boolean missing = kcNumberMissing(channel, rawText, certNum);
        boolean hasThumb = thumbnailUrl != null && !thumbnailUrl.isBlank();
        // 10/10 — 번호는 적혀 있는데 KC 인증 DB 에 없다. 리콜 공표문 번호 대조는 할 수 있어서 판정은 그대로 두고,
        // KC 번호로 제품을 확인할 수 없으니 사진 찾기(2차)는 열어 둔다.
        KcLookup kc = KcLookup.fromRawText(rawText);
        boolean kcNotFound = kc != null && kc.status() == KcLookup.Status.NOT_FOUND;

        // 10/7 — 사진 확인을 했어도 KC 번호 미표기(구 항목누락)를 유지한다.
        // 사진 확인이 공표문을 찾지 못하면 '번호가 없다'는 사실이 화면 맨 위에서 사라지면 안 된다.
        // 사진으로 의심이 나면(PARTIAL) 그쪽이 우선이다.
        String state;
        if (finalResult == null) {
            state = null;
        } else if (finalResult == FinalResult.NO_MATCH && missing) {
            state = MISSING;
        } else {
            state = finalResult.name();
        }

        // 버튼 조건(10/10) — 1차로 판단할 수 없을 때만: 사진이 있고, 아직 안 눌렀고, 일치·확인불가가 아니고,
        // KC 번호를 못 얻었거나(미표기·미입력·상세페이지 못 읽음) 적힌 번호가 KC 인증 DB 에 없을 때.
        // 9/27~10/9 는 웹 직접입력이면 KC 번호를 넣었어도 버튼을 보였다.
        boolean available = hasThumb && !checked
                && finalResult != null && finalResult != FinalResult.MATCH && finalResult != FinalResult.UNKNOWN
                && (missing || kcNotFound);

        ImageInsight insight = check == ExtractionNotes.ImageCheck.FOUND
                ? ImageInsight.fromRawText(rawText)
                : ImageInsight.NONE;

        String reason = missing ? missingReason(channel, rawText) : null;
        if (reason != null && ExtractionNotes.overseasChild(rawText)) {
            // 10/10 — 해외직구·구매대행 어린이제품. 구매대행도 KC 표시 없이는 중개·대행 금지(어린이제품법 제30조)
            reason += " 해외직구·구매대행 상품이어도 어린이제품은 안전인증·안전확인·공급자적합성확인 표시가 없으면 "
                    + "판매중개·구매대행이 금지됩니다(어린이제품 안전 특별법 제30조).";
        }
        return new ResultView(state, check == null ? null : check.name(), available,
                reason,
                insight.productName(), insight.certNum(), insight.modelName());
    }

    /**
     * KC 번호로 판단할 수 없는가 — 1차 판단 불가.
     *   웹 직접입력: 인증번호 칸에 번호 모양이 없다(안 넣었거나 "없음" 같은 글자만 넣음)
     *   쿠팡 구매이력: 상세페이지 KC 칸에 번호가 없거나(REFERENCED) 상세페이지를 못 읽었는데(UNREADABLE),
     *                 서버가 KC 칸 원문에서도 번호를 못 찾았다(10/10 — 확장이 놓친 번호는 서버가 다시 뽑는다)
     */
    static boolean kcNumberMissing(VerificationChannel channel, String rawText, String certNum) {
        boolean numbered = !KcCertNumbers.extract(certNum).isEmpty();
        if (channel == VerificationChannel.WEB) {
            return !numbered;
        }
        return ExtractionNotes.infoMissing(rawText) && !numbered;
    }

    private static String missingReason(VerificationChannel channel, String rawText) {
        if (channel == VerificationChannel.WEB) {
            return "KC 인증번호(예: CB063R423-5001 형식)가 입력되지 않아 KC 번호로 판단하지 못했습니다.";
        }
        ExtractionNotes.KcStatus s = ExtractionNotes.kcStatus(rawText);
        if (s == ExtractionNotes.KcStatus.UNREADABLE) {
            return "쿠팡 상세페이지를 읽지 못해 KC 인증번호를 확인하지 못했습니다.";
        }
        String kcText = ExtractionNotes.kcText(rawText);
        return "쿠팡 상세페이지의 KC 인증정보 칸에 인증번호가 표기되어 있지 않습니다"
                + (kcText == null ? "." : " (\"" + kcText + "\").");
    }
}
