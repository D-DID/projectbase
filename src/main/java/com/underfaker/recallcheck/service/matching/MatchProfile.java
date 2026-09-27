package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.common.TextNormalizer;
import com.underfaker.recallcheck.entity.Recall;

import java.util.regex.Pattern;

/**
 * 매칭 프로파일 — 리콜 1건을 어떤 단서로 판정할지 결정한다.
 *
 * 왜 필요한가:
 * 20260723 공표 53건을 적재해 보니 리콜 데이터가 성격이 다른 두 덩어리로 갈린다.
 *
 *   전기·전자·생활용품  recall_model_name 이 깔끔한 품번이다.
 *                      "408PB", "VDT-E05W", "SG1250M", "PK9031NV", "WI-MC31021".
 *                      cert_num 도 대부분 채워져 있다("YU101889-23001").
 *                      → 품번과 인증번호로 거의 확정 판정이 가능하다.
 *
 *   유아·어린이·잡화    품번이 아예 없다. 그 칸에 상품명 문장이 들어온다.
 *                      "(제품명) 허니 슬라임", "(품명) 의류",
 *                      "(온라인)투데이리빙 16개 세트 동물 캐릭터 신발 파츠...".
 *                      cert_num 도 "-" 인 경우가 많다.
 *                      → 상품명과 브랜드 말고는 단서가 없다.
 *
 * 하나의 가중치 세트로 둘 다 처리하면 양쪽이 같이 망가진다. modelName 0.50 을 유지하면
 * 유아제품은 비교할 모델명이 없어 판정이 안 서고, 반대로 productName 비중을 올리면
 * 품번이 정확히 일치하는 전기제품의 점수가 상품명 표기 차이 때문에 깎인다.
 *
 * 판정은 <b>리콜 쪽 데이터</b>로 한다. 사용자 입력이 아니다. 같은 입력이라도 비교 대상 리콜이
 * 어떤 성격이냐에 따라 무엇을 믿어야 할지가 달라지기 때문이다.
 */
public enum MatchProfile {

    /** 품번·인증번호로 식별되는 공산품. 모델명·인증번호에 무게를 싣는다. */
    IDENTIFIED,

    /** 품번이 없는 유아·잡화. 상품명·브랜드에 무게를 싣는다. */
    UNIDENTIFIED;

    /**
     * 품번처럼 생긴 문자열인가.
     *
     * 정규화된 모델명 기준으로 한글이 없고 숫자가 섞여 있으며 너무 길지 않으면 품번으로 본다.
     * 실측 대조:
     *   408PB, VDTE05W, SG1250M, PK9031NV, WIMC31021, SPEEDSTAR2309 → 품번 (O)
     *   허니슬라임, 킨더아동안전우산, 의류, HERC8자형튜브            → 한글 포함이라 상품명 (X)
     *   ARMRINGCLOUDBLUE                                          → 숫자가 없어 상품명 (X)
     *
     * 길이 상한을 둔 이유: "온라인투데이리빙16개세트동물캐릭터신발파츠..." 같은 긴 문장도
     * 한글이 없으면 통과해 버릴 수 있어서다. 실제 품번은 20자를 넘지 않는다.
     */
    private static final Pattern HANGUL = Pattern.compile("[가-힣]");
    private static final Pattern HAS_DIGIT = Pattern.compile("\\d");
    private static final int MAX_MODEL_CODE_LENGTH = 20;

    /**
     * 품번으로 인정할 최대 토큰(공백으로 나눈 단어) 수.
     *
     * 품번은 대개 한 덩어리다 — "408PB", "VDT-E05W", "WI-MC31021".
     * 두 덩어리까지는 품번인 경우가 있다 — "SPEEDSTAR 2309".
     * 세 덩어리부터는 상품명이다 — "SWIM PLAYING 4", "Arm Ring Cloud Blue".
     */
    private static final int MAX_MODEL_CODE_TOKENS = 2;

    /** 괄호 블록 — 라벨 "(품번)" 이나 부가 설명 "(ITB-D25C)" 를 토큰 수 계산에서 뺀다. */
    private static final Pattern BRACKET_BLOCK =
            Pattern.compile("[(\\[（【][^)\\]）】]*[)\\]）】]");

    /** 어린이·유아 카테고리 힌트. category_name 실측 기준. */
    private static final String[] CHILD_CATEGORY_HINTS = {
            "어린이", "유아", "완구", "학용품", "장신구"
    };

    /**
     * 리콜 1건의 프로파일을 판정한다.
     *
     * <b>판정은 모델명 하나로 한다.</b> 카테고리는 쓰지 않는다.
     *
     * 처음에는 "어린이 카테고리면 UNIDENTIFIED" 를 먼저 보게 짰다가 회귀를 냈다.
     * 10022559(어린이&gt;아동용 섬유제품)는 모델명이 "(품번) VDECX01" 로 멀쩡한 품번인데
     * 카테고리 검사에 먼저 걸려 UNIDENTIFIED 로 떨어졌고, 그 프로파일의 modelName 가중치가
     * 낮아서 품번이 정확히 일치하는데도 점수가 0.833 → 0.464 로 <b>떨어졌다</b>.
     * 카테고리는 "이 물건이 무엇인가"를 말할 뿐 "품번으로 식별 가능한가"를 말하지 않는다.
     * 판정 근거는 모델명이 품번처럼 생겼는지 하나뿐이어야 한다.
     *
     * isChildCategory() 는 유아제품 판별(적재 필터 등)이라는 다른 용도로 남겨 둔다.
     */
    public static MatchProfile of(Recall recall) {
        if (recall == null) {
            return UNIDENTIFIED;
        }
        return hasModelCode(recall.getRecallModelName()) ? IDENTIFIED : UNIDENTIFIED;
    }

    /**
     * 모델명 칸을 목록으로 쪼갠 뒤, <b>조각 하나라도</b> 품번꼴이면 참.
     *
     * ── 9/21 추가 ──
     * 그 전에는 모델명 칸 전체를 그대로 looksLikeModelCode() 에 넘겼다. 그래서 10022790 의
     * "MS116-1.6A / MS116-2.5A / MS116-4.0A / MS116-6.3A / MS116-10A"(57자)가 20자 제한에
     * 걸려 UNIDENTIFIED 로 떨어졌다. 조각 하나하나는 누가 봐도 품번인데 다섯 개가 한 칸에
     * 들어 있다는 이유로 유아·잡화 취급을 받은 것이다. modelName 가중치가 0.50 에서 0.05 로
     * 내려가, 사용자가 모델명만 입력하는 현실적 상황에서 공산품 검증이 통째로 약해진다.
     *
     * 판정 기준은 목록의 성격이 아니라 <b>개별 값이 품번처럼 생겼는가</b> 여야 한다.
     * splitList 가 원본 전체도 후보에 넣으므로, 쪼갤 구분자가 없는 단일 값도 그대로 검사된다.
     *
     * 기존 판정이 유지되는지 확인한 케이스:
     *   "MJ21 워터건 / (제품명) MJ21 압축 워터건" → 조각 전부 한글 포함 → UNIDENTIFIED (유지)
     *   "SWIM PLAYING 4"                        → 조각 1개, 세 덩어리 → UNIDENTIFIED (유지)
     *   "(제품명) 허니 슬라임"                    → 한글            → UNIDENTIFIED (유지)
     *   "(품번) VDECX01"                        → 품번            → IDENTIFIED   (유지)
     */
    public static boolean hasModelCode(String rawModelName) {
        for (String part : TextNormalizer.splitList(rawModelName)) {
            if (looksLikeModelCode(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 품번꼴 판정 — 한글이 없고, 숫자가 있고, 20자 이하이며, 토큰이 두 덩어리 이하.
     *
     * 실측 대조:
     *   408PB, VDT-E05W, SG1250M, WI-MC31021, SPEEDSTAR 2309, (품번) VDECX01 → 품번 (O)
     *   허니슬라임, 킨더아동안전우산, 의류, HERC8자형튜브  → 한글이 있어 상품명 (X)
     *   Arm Ring (Cloud Blue)                        → 숫자가 없어 상품명 (X)
     *   SWIM PLAYING 4                               → 세 덩어리라 상품명 (X)
     */
    public static boolean looksLikeModelCode(String rawModelName) {
        String normalized = TextNormalizer.normalizeModelName(rawModelName);
        if (normalized == null || normalized.length() > MAX_MODEL_CODE_LENGTH) {
            return false;
        }
        if (HANGUL.matcher(normalized).find() || !HAS_DIGIT.matcher(normalized).find()) {
            return false;
        }
        return tokenCount(rawModelName) <= MAX_MODEL_CODE_TOKENS;
    }

    /** 괄호 블록을 뺀 나머지의 단어 수 */
    private static int tokenCount(String raw) {
        if (raw == null) {
            return 0;
        }
        String stripped = BRACKET_BLOCK.matcher(raw).replaceAll(" ").trim();
        return stripped.isEmpty() ? 0 : stripped.split("\\s+").length;
    }

    /**
     * 어린이·유아 카테고리인가.
     *
     * category_name 실측값이 "어린이&gt;완구" 뿐 아니라 "기타어린이제품&gt;" 형태도 있어서
     * prefix 가 아니라 포함 검사를 쓴다.
     *
     * <b>프로파일 판정에는 쓰지 않는다</b>(위 of() 주석 참조). 유아제품만 적재하거나
     * 화면에서 구분해 보여줄 때 쓰는 용도다.
     */
    public static boolean isChildCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return false;
        }
        for (String hint : CHILD_CATEGORY_HINTS) {
            if (categoryName.contains(hint)) {
                return true;
            }
        }
        return false;
    }
}
