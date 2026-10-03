package com.underfaker.recallcheck.dto.internal;

import com.underfaker.recallcheck.common.KcCertNumbers;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * KC 인증번호로 인증 DB(certification 캐시 + KC 목록 API)를 조회한 결과 (10/3 추가, KC인증 연동).
 *
 * <h3>판정에 어떻게 쓰이나</h3>
 * 인증 DB 모델명을 리콜 공표문 모델명과 대조하는 "인증 모델명" 근거로 쓴다(MatchingService.addCertModelComparison).
 * 정확히 같으면 확정 근거(일치), 다르면 가중치 0 — 판정을 깎지 않는다. 같은 모델명으로 후보 리콜도 찾는다.
 * 10/3 실측: CB067R2225-4001 → 인증 모델명 "핑크풋 슬라임" = 9/16 공표 리콜 모델명 → 일치.
 * 모델명 칸을 직접 채우지 않는 이유는 applyTo 주석 참조(KC 번호 하나에 모델 여러 개).
 *
 * <h3>왜 raw_text 블록으로 저장하나</h3>
 * extraction.source 가 MySQL ENUM('URL','IMAGE','MANUAL') 이라 'KC' 행을 새로 넣으면 INSERT 가 실패하고,
 * 컬럼을 늘리면 ddl-auto=validate 로 ALTER 안 한 팀원 DB 에서 앱이 안 뜬다. 그래서 [detail]·[vision] 블록과
 * 같은 방식으로 MANUAL 행의 raw_text 에 남긴다. 판정근거(FR-014)를 나중에 다시 계산할 때도 이 블록을 읽어
 * 판정 당시와 같은 입력으로 다시 계산한다(ExtractionService.loadMerged → applyFromRawText).
 *
 * <pre>
 * [kc-lookup]
 * result: FOUND
 * certNum: CB067R2225-4001
 * certState: 적합
 * certDate: 20240419
 * productName: 완구
 * modelName: 핑크풋 슬라임
 * makerName: -
 * makerCntryName: 중국
 * source: API
 * [/kc-lookup]
 * </pre>
 *
 * Spring 의존 없음 — 단위 테스트용.
 *
 * @param source API(방금 조회) / CACHE(certification 캐시, 7일 이내) / STALE_CACHE(API 실패 → 오래된 캐시)
 */
public record KcLookup(
        Status status,
        String certNum,
        String certState,
        String certDate,
        String productName,
        String brandName,
        String modelName,
        String makerName,
        String makerCntryName,
        String source
) {

    public enum Status {
        /** 인증 DB 에 그 번호가 있다 */
        FOUND,
        /** API 는 정상 응답했는데 그 번호가 없다 — 번호 오기·위조 가능성. 안전/위험 판단은 하지 않는다. */
        NOT_FOUND,
        /** API 호출 실패(타임아웃·서버 오류)이고 캐시도 없다 — 판정은 계속한다 */
        UNAVAILABLE
    }

    public static final String BEGIN = "[kc-lookup]";

    /** 블록 한 값의 최대 길이 — certification 칸(VARCHAR 255)과 같게. 판정 값이 잘리지 않는다. */
    static final int MAX_VALUE = 255;
    public static final String END = "[/kc-lookup]";

    /** 인증 DB 의 '값 없음' 표기. 10/3 실측: makerName 이 "-" 로 온다. */
    private static final Set<String> PLACEHOLDERS = Set.of("-", "--", ".", "N/A", "NA", "없음", "해당없음", "0");

    /** 인증상태 정상 값. 10/3 실측 "적합". 나머지(기간만료·안전인증취소·개선명령·사용금지 N개월 …)는 원문 그대로 보여 준다. */
    public static final String STATE_OK = "적합";

    public static KcLookup notFound(String certNum) {
        return new KcLookup(Status.NOT_FOUND, certNum, null, null, null, null, null, null, null, "API");
    }

    public static KcLookup unavailable(String certNum) {
        return new KcLookup(Status.UNAVAILABLE, certNum, null, null, null, null, null, null, null, null);
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }

    /** 인증상태가 '적합'이 아닌 인증 — 기간만료·취소·사용금지 등. 화면에서 주의 표시용. */
    public boolean stateNeedsAttention() {
        return isFound() && certState != null && !certState.isBlank() && !STATE_OK.equals(certState.trim());
    }

    /**
     * 판정 입력에 이 조회 결과를 싣는다.
     *
     * 1) raw_text 에 [kc-lookup] 블록을 붙인다(없을 때만). MatchingService·RecallQueryService 가 이 블록에서
     *    인증 DB 모델명을 읽어 "인증 모델명" 근거와 후보 검색에 쓴다.
     * 2) FOUND 이고 인증번호 칸 원문에 번호가 <b>딱 하나</b>면 그 정리된 번호로 바꾼다. 판매자가
     *    "KC 인증번호: CB067R2225-4001 (어린이제품)" 처럼 적으면 원문 그대로는 공표문 인증번호와 비교·검색이 안 된다.
     *    번호가 여럿이면 원문을 둔다 — 찾은 번호 하나로 바꾸면 나머지 번호로만 공표된 리콜을 놓친다.
     *
     * <b>모델명·제조사 칸은 채우지 않는다(10/3 실조회 후 변경).</b> KC 번호 하나에 모델이 여러 개 묶인다 —
     * 리콜 데이터에서 번호 2,100개 중 107개가 모델 2개 이상(예: B362A237-7001A 에 큐브 4종),
     * B361R3583-1003 은 인증 DB 대표 모델 "테디웨딩베어" / 리콜 공표 모델 "허스키미니".
     * 대표 모델명으로 빈칸을 채우면 다른 모델일 때 "모델명 불일치"가 반대 증거가 되어 진짜 리콜을 놓칠 수 있다.
     * 그래서 인증 모델명은 MatchingService 에서 <b>정확히 같을 때만</b> 확정 근거로 쓰고, 다르면 가중치 0 으로 근거표에만 남긴다.
     *
     * 원문은 extraction.cert_num 에 그대로 남는다. 같은 입력이면 같은 결과라 판정근거 재계산(applyFromRawText)에서
     * 다시 적용해도 판정 때와 같다.
     */
    public ExtractedProduct applyTo(ExtractedProduct p) {
        if (p == null) {
            return null;
        }
        String raw = p.rawText();
        if (fromRawText(raw) == null) {
            raw = (raw == null || raw.isBlank()) ? toBlock() : raw + "\n" + toBlock();
        }
        String certNumForMatching = p.certNum();
        if (isFound()) {
            List<String> numbersInInput = KcCertNumbers.extract(p.certNum());
            if (numbersInInput.size() == 1 && certNum != null && numbersInInput.get(0).equalsIgnoreCase(certNum)) {
                certNumForMatching = certNum;
            }
        }
        return new ExtractedProduct(p.productName(), p.brandName(), p.modelName(), p.makerName(),
                p.barcodeNum(), certNumForMatching, p.thumbnailUrl(), raw, p.confidence());
    }

    /** 판정에 쓸 인증 DB 모델명. FOUND 이고 실제 값일 때만, 아니면 null. */
    public String modelNameForMatching() {
        return isFound() && meaningful(modelName) ? modelName.trim() : null;
    }

    // ------------------------------------------------------------------ raw_text 블록

    public String toBlock() {
        StringBuilder sb = new StringBuilder(BEGIN).append('\n');
        line(sb, "result", status == null ? null : status.name());
        line(sb, "certNum", certNum);
        line(sb, "certState", certState);
        line(sb, "certDate", certDate);
        line(sb, "productName", productName);
        line(sb, "brandName", brandName);
        line(sb, "modelName", modelName);
        line(sb, "makerName", makerName);
        line(sb, "makerCntryName", makerCntryName);
        line(sb, "source", source);
        return sb.append(END).toString();
    }

    /** raw_text 에서 마지막 [kc-lookup] 블록을 읽는다. 없거나 깨졌으면 null. */
    public static KcLookup fromRawText(String rawText) {
        if (rawText == null) {
            return null;
        }
        int b = rawText.lastIndexOf(BEGIN);
        if (b < 0) {
            return null;
        }
        int e = rawText.indexOf(END, b);
        String body = rawText.substring(b + BEGIN.length(), e < 0 ? rawText.length() : e);

        Status status = null;
        String certNum = null, certState = null, certDate = null, productName = null, brandName = null;
        String modelName = null, makerName = null, makerCntryName = null, source = null;
        for (String raw : body.split("\\R")) {
            int colon = raw.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = raw.substring(0, colon).trim();
            String v = raw.substring(colon + 1).trim();
            if (v.isEmpty()) {
                continue;
            }
            switch (key) {
                case "result" -> status = parseStatus(v);
                case "certNum" -> certNum = v;
                case "certState" -> certState = v;
                case "certDate" -> certDate = v;
                case "productName" -> productName = v;
                case "brandName" -> brandName = v;
                case "modelName" -> modelName = v;
                case "makerName" -> makerName = v;
                case "makerCntryName" -> makerCntryName = v;
                case "source" -> source = v;
                default -> { }
            }
        }
        if (status == null) {
            return null;
        }
        return new KcLookup(status, certNum, certState, certDate, productName, brandName,
                modelName, makerName, makerCntryName, source);
    }

    /** raw_text 에 블록이 있으면 판정 때와 같게 적용한다(판정근거 재계산용). 없으면 그대로. */
    public static ExtractedProduct applyFromRawText(ExtractedProduct p) {
        if (p == null) {
            return null;
        }
        KcLookup kc = fromRawText(p.rawText());
        return kc == null ? p : kc.applyTo(p);
    }

    // ------------------------------------------------------------------ 내부

    /** 값 없음 표기("-" 등)·빈 값이 아닌 실제 값인가 */
    public static boolean meaningful(String s) {
        if (s == null) {
            return false;
        }
        String t = s.trim();
        return !t.isEmpty() && !PLACEHOLDERS.contains(t.toUpperCase(Locale.ROOT));
    }

    private static Status parseStatus(String v) {
        try {
            return Status.valueOf(v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void line(StringBuilder sb, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        // 255 = certification 칸 최대 길이. 판정 때 쓴 값과 블록에서 되살린 값이 같아야 판정근거가 어긋나지 않는다.
        String one = value.replaceAll("[\\r\\n]+", " ").trim();
        if (one.length() > MAX_VALUE) {
            one = one.substring(0, MAX_VALUE);
        }
        sb.append(key).append(": ").append(one).append('\n');
    }
}
