package com.underfaker.recallcheck.service.matching;

import com.underfaker.recallcheck.common.KcCertNumbers;
import com.underfaker.recallcheck.common.ListingTextCleaner;
import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.ImageInsight;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.dto.internal.MatchCandidate;
import com.underfaker.recallcheck.dto.internal.MatchOutcome;
import com.underfaker.recallcheck.entity.MatchResult;
import com.underfaker.recallcheck.entity.Recall;
import com.underfaker.recallcheck.entity.enums.Decision;
import com.underfaker.recallcheck.repository.MatchResultRepository;
import com.underfaker.recallcheck.service.sync.RecallQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FR-010 후보 도출 → FR-011 4단계 판정 → FR-012 항목별 대조.
 *
 * ── 9/20 필드 매핑 교정 ──
 * 실데이터 55건을 적재해 보니 비교 대상 컬럼 세 개가 어긋나 있었다.
 *
 * (1) productName ↔ recall_product_name
 *     recall_product_name 은 제품명이 아니라 품목 분류명이다("기타완구(완구)").
 *     실제 상품명은 recall_model_name 쪽("(제품명) 허니 슬라임")에 있으므로 두 칸 중
 *     잘 맞는 쪽으로 비교한다.
 *
 * (2) makerName ↔ maker_name
 *     55건 전부 maker_name 이 비어 있다. 제조·수입사는 recall_cmpny_name 에 들어온다.
 *     구버전은 official 이 비었다는 이유로 비교를 건너뛰어, 가중치 0.10 짜리 항목이
 *     한 번도 동작한 적이 없다. Recall.resolveMakerName() 이 폴백을 담당한다.
 *
 * (3) brandName — 비교 자체가 없었다
 *     ExtractedProduct.brandName 도 recall_brand_name 도 값이 있는데 대조하지 않았다.
 *
 * ── 9/20 프로파일 분기 ──
 * 리콜 데이터가 품번 있는 공산품과 품번 없는 유아·잡화로 갈려서, 비교 대상 리콜마다
 * 프로파일을 판정하고 그에 맞는 가중치·임계값을 쓴다. 근거는 MatchProfile 주석 참조.
 *
 * 판매글 상품명은 그대로 쓰지 않는다. "[무료배송] 아트박스 허니 슬라임 100g, 3개" 같은 값이
 * 공표문의 "(제품명) 허니 슬라임" 과 비교되면 길이 차이만으로 점수가 깎인다.
 * ListingTextCleaner 로 정제본을 하나 더 만들어 두 후보 중 잘 맞는 쪽을 쓴다.
 *
 * ── 9/24 2단계 이미지 판독 → 9/27 사용자 클릭 방식으로 변경 ──
 * 텍스트로 1차 대조(matchWithImage)만 자동으로 한다. Google Vision Web Detection 은 결과가
 * "항목누락"인 건에서 사용자가 "사진으로 찾기"를 누를 때만 돈다(matchByImage). 자세한 건
 * matchWithImage() 주석 참조.
 *
 * ── 9/27 판정 기준 ──
 * 일치(MATCH)는 100% 확정 근거가 있을 때만 — DecisionResolver.resolve(score, profile, comparisons) 참조.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MatchingService {

    private final RecallQueryService recallQueryService;
    private final FieldNormalizer fieldNormalizer;
    private final SimilarityCalculator similarityCalculator;
    private final DecisionResolver decisionResolver;
    private final MatchResultRepository matchResultRepository;

    /**
     * 이미지판독 점수가 이보다 낮으면 판정에 넣지 않는다(가중치 0 으로 근거표에만 남긴다).
     *
     * ── 9/27 추가 ──
     * 실측: 슬라임 썸네일의 판독 결과가 bestGuess='honeybee' 등 전부 영어였고, 한글 공표문
     * "(제품명) 허니 슬라임" 과 글자가 하나도 안 겹쳐 이미지판독 0% → 종합 0.761 → 0.619 로 <b>깎였다</b>.
     * 0% 의 원인이 "다른 제품"이 아니라 "언어가 다름"이라서 반대 증거로 쓸 수 없다.
     * 인증번호(식별자 0점 = 반대 증거)와 다르게 다루는 이유다.
     *
     * 0.5 는 잠정치다(실측 근거 없음). 포함관계 점수의 하한이 0.55 라서, 판독 텍스트에 공표문 상품명이
     * 통째로 들어 있거나 거의 같은 경우만 반영되게 잡았다. 효과: 이미지판독은 판정을 크게 깎지 못한다.
     * 대가: 이미지가 "다른 제품"임을 보여 줘도 그 신호를 버린다.
     */
    static final double IMAGE_LABEL_MIN_SCORE = 0.50;

    /**
     * 상품명 대조에서 모델명 칸 조각을 "쓸 만하다"고 볼 최소 길이(정규화 후).
     * SimilarityCalculator 의 부분 포함 최소 길이(4)와 맞췄다. 이보다 짧은 조각만 있으면
     * 품목명 칸을 대신 쓴다. 9/27 추가.
     */
    static final int MIN_USEFUL_MODEL_PIECE = 4;

    /** 1차 채점 결과를 들고 다니는 내부 묶음. 2차에서 일부만 교체하려고 둔다. */
    private record Scored(Recall recall, MatchProfile profile,
                          List<FieldComparison> comparisons,
                          double score, Decision decision, String reason) {
    }

    /**
     * 사진 판독으로 찾은 후보를 "의심"으로 올릴 이미지판독 점수 하한.
     *
     * ── 9/27 추가 (사진 확인 버튼 흐름) ──
     * 사진 확인은 텍스트로 못 찾은 "항목누락" 건에서 사용자가 누를 때만 돈다. 이때는 가중합에 이미지판독
     * 0.15 를 얹는 것만으로는 판정이 거의 안 바뀐다(텍스트 점수 0.40 미만이면 사진이 100% 라도 불일치).
     * 그래서 사진 판독 텍스트(웹 문서 제목·추정 이름)가 공표문 상품명과 이 값 이상 맞으면 그 후보를
     * 의심(PARTIAL)으로 올린다. <b>일치(MATCH)로는 절대 올리지 않는다</b> — 사진은 식별자가 아니다.
     *
     * 0.65 는 잠정치다(실측 근거 없음). 포함관계 점수는 0.55 + 0.40 × (짧은쪽/긴쪽) 이라,
     * 공표문 상품명이 웹 문서 제목 안에 통째로 들어 있고 제목 길이가 상품명의 네 배 이하이면 넘는다.
     */
    static final double IMAGE_SUSPECT_SCORE = 0.65;

    /** 사진 판독 텍스트로 후보를 더 찾을 때 쓸 텍스트 수 · 전체 후보 상한. 대조 비용이 이에 비례한다. */
    static final int IMAGE_SEARCH_TEXTS = 8;
    static final int IMAGE_SEARCH_MAX_CANDIDATES = 80;

    /**
     * 텍스트 1차 검증 — 추출된 상품명·브랜드·모델명·인증번호를 공공데이터와 대조한다.
     *
     * @param verificationId 검증 요청 식별자
     * @param product        통합된 식별 정보
     * @return 판정된 후보 목록 (점수 내림차순)
     */
    public List<MatchCandidate> match(Long verificationId, ExtractedProduct product) {
        return matchWithImage(verificationId, product).candidates();
    }

    /**
     * 텍스트 1차 검증. 이름은 9/24 호환용으로 남겼다 — <b>9/27 부터 여기서 Vision 을 부르지 않는다</b>.
     *
     * ── 9/27 변경 (팀장 결정 — 쿠팡 검증 흐름) ──
     *   1) 쿠팡 구매이력 → 확장이 상세페이지 '필수 표기 정보'(KC 인증정보·품명 및 모델명·제조자)까지 읽어 온다
     *   2) 텍스트로 1차 대조 → [일치 / 의심 / 항목누락 / 불일치]
     *   3) 항목누락이면 화면에 "사진으로 찾기" 버튼
     *   4) 사용자가 누를 때만 Vision 웹 검색 (matchByImage)
     * 예전에는 PARTIAL 이 나오면 자동으로 Vision 을 불렀다. 이제는 사용자가 누른 건만 호출하므로
     * 월 사용량은 버튼 클릭 수와 같다.
     */
    public MatchOutcome matchWithImage(Long verificationId, ExtractedProduct product) {
        List<Scored> scoredList = new ArrayList<>();
        for (Recall recall : recallQueryService.findCandidates(product)) {
            Scored scored = score(product, recall, ImageInsight.NONE);
            if (scored != null) {
                scoredList.add(scored);
            }
        }
        return new MatchOutcome(saveAndSort(verificationId, scoredList), ImageInsight.NONE);
    }

    /**
     * 사진 확인 (사용자가 버튼을 눌렀을 때) — Vision 웹 검색 결과로 후보를 더 찾고 다시 채점한다.
     *
     * 후보 = 텍스트 후보 ∪ 판독 텍스트(추정 이름·웹 문서 제목)로 찾은 후보.
     * 모든 후보에 이미지판독 행을 얹어 채점하고, 이미지판독이 IMAGE_SUSPECT_SCORE 이상인데 판정이 불일치면
     * 의심으로 올린다. 이 검증의 기존 match_result 는 지우고 새로 저장한다.
     *
     * @param insight VerificationService 가 Vision 으로 받은 판독 결과 (사용 불가면 텍스트 결과와 같다)
     */
    public MatchOutcome matchByImage(Long verificationId, ExtractedProduct product, ImageInsight insight) {
        Map<Long, Recall> pool = new LinkedHashMap<>();
        for (Recall r : recallQueryService.findCandidates(product)) {
            pool.putIfAbsent(r.getRecallUid(), r);
        }
        if (insight != null && insight.isUsable()) {
            int used = 0;
            for (String text : insight.textCandidates()) {
                if (used++ >= IMAGE_SEARCH_TEXTS || pool.size() >= IMAGE_SEARCH_MAX_CANDIDATES) {
                    break;
                }
                for (Recall r : recallQueryService.findCandidates(textOnly(text))) {
                    if (pool.size() >= IMAGE_SEARCH_MAX_CANDIDATES) {
                        break;
                    }
                    pool.putIfAbsent(r.getRecallUid(), r);
                }
            }
        }

        List<Scored> scoredList = new ArrayList<>();
        for (Recall recall : pool.values()) {
            Scored scored = score(product, recall, insight == null ? ImageInsight.NONE : insight);
            if (scored != null) {
                scoredList.add(raiseByImage(scored));
            }
        }
        matchResultRepository.deleteByVerificationId(verificationId);
        return new MatchOutcome(saveAndSort(verificationId, scoredList),
                insight != null && insight.isUsable() ? insight : ImageInsight.NONE);
    }

    /** 사진 판독 텍스트 하나를 상품명으로만 가진 검색용 입력 */
    private static ExtractedProduct textOnly(String text) {
        return new ExtractedProduct(text, null, null, null, null, null, null, null, 1.0);
    }

    /** 이미지판독이 충분히 맞으면 불일치를 의심으로 올린다. 일치로는 올리지 않는다. */
    private Scored raiseByImage(Scored s) {
        if (s.decision() != Decision.NO_MATCH) {
            return s;
        }
        boolean imageHit = s.comparisons().stream()
                .anyMatch(c -> c.field().equals("imageLabel") && c.weight() > 0
                        && c.score() >= IMAGE_SUSPECT_SCORE);
        if (!imageHit) {
            return s;
        }
        return new Scored(s.recall(), s.profile(), s.comparisons(), s.score(), Decision.PARTIAL,
                s.reason() + " · 사진 판독으로 의심");
    }

    private List<MatchCandidate> saveAndSort(Long verificationId, List<Scored> scoredList) {
        List<MatchCandidate> results = new ArrayList<>();
        for (Scored s : scoredList) {
            String matchedField = s.comparisons().stream()
                    .filter(c -> c.score() >= 0.9)
                    .map(FieldComparison::field)
                    .collect(Collectors.joining(","));

            results.add(new MatchCandidate(
                    s.recall().getRecallUid(), s.score(), s.decision(), s.reason(), s.comparisons()));

            matchResultRepository.save(MatchResult.builder()
                    .verificationId(verificationId)
                    .recallUid(s.recall().getRecallUid())
                    .similarityScore(s.score())
                    .matchedField(matchedField.isEmpty() ? null : matchedField)
                    .decision(s.decision())
                    .reason(s.reason())
                    .build());
        }
        // 판정 등급이 높은 것 먼저(일치 > 의심 > 불일치), 같은 등급 안에서는 점수순.
        // 사진으로 의심이 된 후보는 점수가 낮을 수 있어서 점수만으로 정렬하면 불일치 뒤로 밀린다.
        results.sort(Comparator.comparingInt((MatchCandidate c) -> c.decision().ordinal())
                .thenComparing(Comparator.comparingDouble(MatchCandidate::similarityScore).reversed()));
        return results;
    }

    /**
     * FR-014 판정근거용 항목별 대조.
     *
     * 9/27 — 저장된 판독 결과는 이제 사용자가 사진 확인을 눌렀을 때만 생기고, 그때는 모든 후보에
     * 이미지판독을 얹어 채점했다(matchByImage). 그래서 근거도 판독 결과가 있으면 그대로 얹는다.
     */
    @Transactional(readOnly = true)
    public List<FieldComparison> evidenceComparisons(ExtractedProduct product, Recall recall,
                                                     ImageInsight insight) {
        Scored scored = score(product, recall, insight == null ? ImageInsight.NONE : insight);
        return scored == null ? List.of() : scored.comparisons();
    }

    /** 후보 하나를 채점한다. 비교할 항목이 하나도 없으면 null. */
    private Scored score(ExtractedProduct product, Recall recall, ImageInsight insight) {
        MatchProfile profile = MatchProfile.of(recall);
        List<FieldComparison> comparisons = compare(product, recall, profile, insight);
        if (comparisons.isEmpty()) {
            return null;
        }
        double score = similarityCalculator.weightedScore(comparisons);
        Decision decision = decisionResolver.resolve(score, profile, comparisons);
        return new Scored(recall, profile, comparisons, score, decision,
                buildReason(comparisons, score, profile));
    }

    /**
     * 항목별 대조 (FR-012) — 프로파일은 리콜 데이터에서 자동 판정한다.
     *
     * 9/17 public 으로 변경 — FR-014 판정근거 조회(VerificationService.getEvidence)가 이 계산을
     * 그대로 다시 쓴다. match_result 에는 항목별 점수가 저장되지 않아서(종합 점수·matched_field·
     * reason 만) 조회 시점에 재계산해야 하는데, 판정 때와 다른 코드로 계산하면 화면 근거와
     * 실제 판정이 어긋난다. 계산 자체는 DB 를 안 건드리는 순수 함수다.
     */
    @Transactional(readOnly = true)
    public List<FieldComparison> compare(ExtractedProduct product, Recall recall) {
        return compare(product, recall, MatchProfile.of(recall));
    }

    /** 프로파일을 명시하는 버전 — match() 가 이미 판정한 값을 재사용할 때 쓴다. */
    @Transactional(readOnly = true)
    public List<FieldComparison> compare(ExtractedProduct product, Recall recall, MatchProfile profile) {
        return compare(product, recall, profile, ImageInsight.NONE);
    }

    /**
     * 이미지 판독 결과까지 반영하는 버전.
     * 판정근거 화면용으로는 이걸 직접 부르지 말고 evidenceComparisons() 를 쓸 것 —
     * 그쪽이 "1차 판정이 PARTIAL 일 때만 반영" 규칙까지 재현한다.
     */
    @Transactional(readOnly = true)
    public List<FieldComparison> compare(ExtractedProduct product, Recall recall,
                                         MatchProfile profile, ImageInsight insight) {
        List<FieldComparison> comparisons = new ArrayList<>();

        addListComparison(comparisons, "modelName",
                product.modelName(), recall.getRecallModelName(), true, profile);

        addListComparison(comparisons, "certNum",
                product.certNum(), recall.getCertNum(), false, profile);

        // 상품명 — 입력은 원본과 정제본 둘 다, 공식 값은 분류명 칸과 상품명이 실제로 들어 있는
        // 모델명 칸 둘 다. 네 조합 중 가장 잘 맞는 하나를 대표로 쓴다.
        addProductNameComparison(comparisons, product.productName(),
                recall.getRecallModelName(), recall.getRecallProductName(), profile);

        addComparison(comparisons, "makerName",
                product.makerName(), recall.resolveMakerName(), profile);

        if (product.brandName() == null || product.brandName().isBlank()) {
            // 9/27 — 쿠팡 구매이력(확장)은 브랜드를 따로 보내지 않는다. 상품명 안에서 찾는다.
            addBrandFromTitleComparison(comparisons, product.productName(),
                    recall.getRecallBrandName(), profile);
        } else {
            addComparison(comparisons, "brandName",
                    product.brandName(), recall.getRecallBrandName(), profile);
        }

        addImageComparison(comparisons, insight, recall, profile);

        // 비교할 다른 항목이 하나도 없는데 반영 안 되는 인증 모델명 행만 생기면, 대조 대상이 아니던 리콜이
        // 0점 후보로 저장된다(10/3 리뷰). 그래서 반영될 때(완전일치)나 다른 항목이 있을 때만 싣는다.
        addCertModelComparison(comparisons, KcLookup.fromRawText(product.rawText()), recall, profile);

        return comparisons;
    }

    /**
     * 10/3 추가 — KC 인증 DB 모델명 대조(KC인증 연동).
     *
     * 검증 때 인증번호로 조회한 인증 DB 모델명([kc-lookup] 블록)을 공표문 모델명 칸과 잰다. 재는 규칙은
     * 모델명 칸과 같다(addListComparison 의 modelStyle). 다만 <b>정확히 같을 때만</b> 모델명 가중치로 반영하고,
     * 아니면 가중치 0 으로 근거표에만 남긴다 — 이미지판독 하한(IMAGE_LABEL_MIN_SCORE)과 같은 방식이다.
     *
     * 왜 한쪽만 반영하나: KC 번호 하나에 모델이 여러 개 묶이고, 인증 DB 는 대표 모델 하나만 준다(10/3 실측:
     * 리콜 번호 2,100개 중 107개가 모델 2개 이상, B361R3583-1003 은 인증 "테디웨딩베어" / 리콜 "허스키미니").
     * 대표 모델명이 다르다는 건 "다른 제품"의 증거가 아니라서 판정을 깎으면 진짜 리콜을 놓친다.
     * 같으면 "그 번호로 인증받은 바로 그 모델이 리콜됐다"는 뜻이라 인증번호 완전일치와 같은 급의 근거다
     * (DecisionResolver.IDENTIFIER_FIELDS).
     *
     * 오탐을 막는 조건 두 가지(10/3 리뷰):
     *   1) 공표문 인증번호 칸에 <b>다른</b> 번호가 있으면 반영하지 않는다 — 다른 번호로 인증받은 제품이다.
     *      칸이 비었거나 "-"·"공급자적합성" 같은 값이면, 또는 우리 번호가 들어 있으면 반영한다.
     *   2) 품번꼴이 아닌 모델명(상품명 문장)은 공표문 모델명 칸 <b>전체</b>와 같을 때만 인정한다 —
     *      "곰인형 / 토끼인형" 같은 칸의 조각 하나와 짧은 일반명이 우연히 같아지는 걸 막는다.
     */
    private void addCertModelComparison(List<FieldComparison> out, KcLookup kc,
                                        Recall recall, MatchProfile profile) {
        String certModel = kc == null ? null : kc.modelNameForMatching();
        if (certModel == null) {
            return;
        }
        String normalizedCert = fieldNormalizer.normalizeModelName(certModel);
        boolean identifier = isIdentifier(true, recall.getRecallModelName());
        List<String> officials = identifier
                ? fieldNormalizer.splitList(recall.getRecallModelName())
                : (recall.getRecallModelName() == null ? List.of() : List.of(recall.getRecallModelName()));
        if (normalizedCert == null || officials.isEmpty()) {
            return;
        }
        double best = -1.0;
        String bestOfficial = null;
        for (String official : officials) {
            String normalizedOfficial = fieldNormalizer.normalizeModelName(official);
            if (normalizedOfficial == null) {
                continue;
            }
            double score = identifier
                    ? similarityCalculator.identifierSimilarity(normalizedCert, normalizedOfficial)
                    : similarityCalculator.similarity(normalizedCert, normalizedOfficial);
            if (score > best) {
                best = score;
                bestOfficial = official;
            }
        }
        if (bestOfficial == null) {
            return;
        }
        boolean sameCertFamily = sameCertFamily(kc.certNum(), recall.getCertNum());
        double weight = best >= DecisionResolver.EXACT && sameCertFamily
                ? similarityCalculator.weightOf("modelName", profile) : 0.0;
        if (weight == 0.0 && out.isEmpty()) {
            return;
        }
        out.add(new FieldComparison(CERT_MODEL_FIELD, certModel, bestOfficial, best, weight));
    }

    /** 공표문 인증번호 칸에 번호가 없거나(플레이스홀더) 우리 번호가 들어 있으면 true. 다른 번호만 있으면 false. */
    static boolean sameCertFamily(String ourCertNum, String recallCertCell) {
        List<String> recallNumbers = KcCertNumbers.extract(recallCertCell);
        if (recallNumbers.isEmpty()) {
            return true;
        }
        return ourCertNum != null && recallNumbers.stream().anyMatch(n -> n.equalsIgnoreCase(ourCertNum.trim()));
    }

    /** 근거표 항목 이름 — 인증 DB 모델명 대조 */
    public static final String CERT_MODEL_FIELD = "certModelName";

    /**
     * 이미지 판독 결과 대조.
     *
     * Vision Web Detection 이 돌려준 텍스트(bestGuessLabel·webEntities)를 공표문의
     * 모델명 칸·상품명 칸과 전부 맞춰 보고 <b>최고점 하나</b>를 대표로 쓴다.
     * 후보를 여럿 두고 최고점을 쓰는 방식은 addProductNameComparison·addListComparison 과 같다.
     *
     * 새 유사도 계산기를 만들지 않는다. 판독 결과가 텍스트라서 기존 SimilarityCalculator 가
     * 그대로 쓰인다 — 이 설계의 요점이다.
     */
    private void addImageComparison(List<FieldComparison> out, ImageInsight insight,
                                    Recall recall, MatchProfile profile) {
        if (insight == null || !insight.isUsable()) {
            return;
        }

        double best = -1.0;
        String bestInput = null;
        String bestOfficial = null;

        for (String candidate : insight.textCandidates()) {
            // 9/25 — 라벨을 " / " 기준으로 쪼개서 조각마다 대조한다(원문 전체도 후보에 남는다).
            // 쿠팡 썸네일 실측(vendorItemId 80630039672): bestGuess 가
            //   "Moremo Keratin / 모레모 케라틴 Moremo Keratin Root Touch Up Magic Straight /
            //    모레모 케라틴 루트 터치 업 매직스트레이트"
            // 처럼 영문·한글 표기를 슬래시로 이어 붙여 온다. 통째로 비교하면 길이 차이 때문에
            // 같은 제품 공표문과도 0.647 밖에 안 나왔고, 쪼개면 1.000 이 나왔다(무관 제품 대조군 0.056).
            for (String piece : fieldNormalizer.splitList(candidate)) {
                // 9/27 — 페이지 제목은 판매글이라 "[무료배송] … 100g 3개입 특가" 같은 문구가 붙는다.
                // 상품명 대조(addProductNameComparison)와 같은 정제본을 후보에 더한다.
                for (String variant : ListingTextCleaner.candidates(piece)) {
                    String normalizedInput = fieldNormalizer.normalize(variant);
                    if (normalizedInput == null) {
                        continue;
                    }
                    // 브랜드 칸은 일부러 대조하지 않는다. 이미지 라벨에는 브랜드명("MOREMO")이 거의 항상
                    // 들어 있어서, 같은 브랜드의 다른 제품 공표문에도 1.000 이 찍힌다. UNIDENTIFIED
                    // 가중치 0.15 면 PARTIAL 을 MATCH 로 밀어 올릴 수 있는 크기라 오탐 위험이 크다.
                    for (String official : new String[]{
                            recall.getRecallModelName(), recall.getRecallProductName()}) {
                        String normalizedOfficial = fieldNormalizer.normalize(official);
                        if (normalizedOfficial == null) {
                            continue;
                        }
                        double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
                        if (score > best) {
                            best = score;
                            bestInput = variant;
                            bestOfficial = official;
                        }
                    }
                }
            }
        }

        if (bestOfficial == null) {
            return;
        }
        // 9/27 — 겹치는 게 거의 없으면 가중치 0 으로 근거표에만 남긴다(IMAGE_LABEL_MIN_SCORE 참조).
        double weight = best >= IMAGE_LABEL_MIN_SCORE
                ? similarityCalculator.weightOf("imageLabel", profile)
                : 0.0;
        out.add(new FieldComparison("imageLabel", bestInput, bestOfficial, best, weight));
    }

    /**
     * 브랜드 칸이 비었을 때 — 상품명 안에 공표문 브랜드가 <b>단어로</b> 들어 있으면 브랜드 일치로 본다.
     *
     * ── 9/27 추가 ──
     * 확장(background.js toManualInputRequest)은 주문목록에서 상품명·썸네일만 뽑고 brandName 은 늘 null 이다.
     * 그래서 쿠팡 구매이력 검증에서는 브랜드 대조(상품명기준 가중치 0.20)가 한 번도 돌지 않았다.
     * 공공데이터 적재분(db/recall-dump.sql, 778건) 중 어린이·유아 391건의 39%(151건)는 공표문에 브랜드가 있다.
     *
     * 단어 단위로만 찾는다(부분 문자열 아님). 공표문 브랜드에 "모모", "팡팡", "K2" 같은 2자짜리가 있어서
     * 부분 문자열로 찾으면 무관한 상품명에도 걸린다. 띄어쓰기가 다른 경우("브라운브레스 키즈")를 위해
     * 연속한 단어 1~3개를 붙여 본다.
     *
     * 못 찾으면 행을 만들지 않는다(0점으로 넣지 않는다). 판매글이 브랜드를 빼고 쓰는 경우가 있어서
     * "상품명에 브랜드가 없다"는 반대 증거가 되지 못한다.
     */
    private void addBrandFromTitleComparison(List<FieldComparison> out, String title,
                                             String officialBrand, MatchProfile profile) {
        String brand = fieldNormalizer.normalize(officialBrand);
        if (brand == null || title == null || title.isBlank()) {
            return;
        }
        String cleaned = ListingTextCleaner.clean(title);
        if (cleaned == null) {
            return;
        }
        String[] words = cleaned.trim().split("\\s+");
        for (int i = 0; i < words.length; i++) {
            StringBuilder joined = new StringBuilder();
            StringBuilder shown = new StringBuilder();
            for (int j = i; j < Math.min(words.length, i + 3); j++) {
                String w = fieldNormalizer.normalize(words[j]);
                if (w == null) {
                    break;
                }
                joined.append(w);
                shown.append(j == i ? "" : " ").append(words[j]);
                if (joined.toString().equals(brand)) {
                    out.add(new FieldComparison("brandName", shown + " (상품명에서 찾음)", officialBrand, 1.0,
                            similarityCalculator.weightOf("brandName", profile)));
                    return;
                }
                if (joined.length() >= brand.length()) {
                    break;
                }
            }
        }
    }

    /** 단일 값 대조 */
    private void addComparison(List<FieldComparison> out, String field,
                               String input, String official, MatchProfile profile) {
        String normalizedInput = fieldNormalizer.normalize(input);
        String normalizedOfficial = fieldNormalizer.normalize(official);
        if (normalizedInput == null || normalizedOfficial == null) {
            return;
        }
        double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
        out.add(new FieldComparison(field, input, official, score,
                similarityCalculator.weightOf(field, profile)));
    }

    /**
     * 상품명 대조 — 입력 후보(원본·정제본) × 공식 후보(모델명 칸·분류명 칸)를 모두 재고
     * 가장 높은 점수를 쓴다.
     *
     * 입력 쪽을 둘로 두는 이유: 판매글 문구를 걷어낸 쪽이 대개 잘 맞지만, 공표문이 오히려
     * 긴 상품명인 경우(10022552 의 "(온라인)투데이리빙 16개 세트 …")에는 원본이 더 맞는다.
     * 공식 쪽을 둘로 두는 이유: 상품명이 어느 칸에 들어 있는지가 카테고리마다 다르다.
     */
    private void addProductNameComparison(List<FieldComparison> out, String input,
                                          String officialModelName, String officialProductName,
                                          MatchProfile profile) {
        String[] inputCandidates = ListingTextCleaner.candidates(input);
        if (inputCandidates.length == 0) {
            return;
        }

        double best = -1.0;
        String bestInput = null;
        String bestOfficial = null;

        // 9/27 — 공공데이터(db/recall-dump.sql) 기준으로 두 가지를 바꿨다. 근거·수치는
        // Claude outputs/APPLIED-20260927.md §7, 재현은 Claude outputs/recall-eval/run-eval.sh.
        //
        // (1) 모델명 칸을 조각으로 쪼개서 조각마다도 대조한다(원본 전체도 후보에 남는다).
        //     어린이·유아 리콜은 모델명 칸에 "(품번) AP VJP1 / (온라인) V배색 점퍼 블랙" 처럼 여러 값이
        //     한 칸에 들어 있다. 칸 전체와 비교하면 구매 상품명이 그중 한 조각과 똑같아도 길이 차이 때문에
        //     점수가 바닥이었다(10020278 "베이비잼 남여공용 통샌들" 26%).
        //
        // (2) 품목명 칸(recall_product_name)은 모델명 칸에 쓸 만한 조각(정규화 4자 이상)이 없을 때만 쓴다.
        //     이 칸은 "외의류(아섬)(아동용 섬유제품)" 같은 분류명이라 구매 상품명과 맞아도 식별 근거가 안 된다.
        //     실측: 어린이·유아 391건에서 이 칸을 빼도 자기 적중은 그대로(388건)였고, 다른 리콜에 붙는
        //     의심은 114쌍 → 40쌍으로 줄었다. 모델명 칸이 "욕실화" 처럼 짧은 경우(10019660)만 품목명 칸이 답이라
        //     그때는 남긴다.
        List<String> officials = new ArrayList<>(fieldNormalizer.splitList(officialModelName));
        if (officialProductName != null && !officials.contains(officialProductName)
                && officials.stream().map(fieldNormalizer::normalize)
                        .noneMatch(o -> o != null && o.length() >= MIN_USEFUL_MODEL_PIECE)) {
            officials.add(officialProductName);
        }

        for (String inputCandidate : inputCandidates) {
            String normalizedInput = fieldNormalizer.normalize(inputCandidate);
            if (normalizedInput == null) {
                continue;
            }
            for (String official : officials) {
                String normalizedOfficial = fieldNormalizer.normalize(official);
                if (normalizedOfficial == null) {
                    continue;
                }
                double score = similarityCalculator.similarity(normalizedInput, normalizedOfficial);
                if (score > best) {
                    best = score;
                    bestInput = inputCandidate;
                    bestOfficial = official;
                }
            }
        }
        if (bestOfficial == null) {
            return;
        }
        out.add(new FieldComparison("productName", bestInput, bestOfficial, best,
                similarityCalculator.weightOf("productName", profile)));
    }

    /**
     * 콤마 구분 목록 대응 — 가장 잘 맞는 항목 하나를 대표로 쓴다.
     *
     * 9/20 — 모델명은 저장 시점(Recall.normalizeFields)과 같은 normalizeModelName 규칙을 쓴다.
     * 구버전은 저장은 normalize(), 비교는 normalizeModelName() 이라 규칙이 갈라져 있었다.
     *
     * 9/27 — 인증번호·품번꼴 모델명은 편집거리 부분점수를 주지 않는다(isIdentifier 참조).
     */
    private void addListComparison(List<FieldComparison> out, String field,
                                   String input, String officialList,
                                   boolean modelStyle, MatchProfile profile) {
        String normalizedInput = modelStyle
                ? fieldNormalizer.normalizeModelName(input)
                : fieldNormalizer.normalize(input);
        if (normalizedInput == null) {
            return;
        }

        List<String> officials = fieldNormalizer.splitList(officialList);
        if (officials.isEmpty()) {
            return;
        }

        boolean identifier = isIdentifier(modelStyle, officialList);

        double best = -1.0;
        String bestOfficial = null;
        for (String official : officials) {
            String normalizedOfficial = modelStyle
                    ? fieldNormalizer.normalizeModelName(official)
                    : fieldNormalizer.normalize(official);
            if (normalizedOfficial == null) {
                continue;
            }
            double score = identifier
                    ? similarityCalculator.identifierSimilarity(normalizedInput, normalizedOfficial)
                    : similarityCalculator.similarity(normalizedInput, normalizedOfficial);
            if (score > best) {
                best = score;
                bestOfficial = official;
            }
        }
        // 공식 값이 전부 플레이스홀더("-" 등)였다면 비교 대상이 아니다.
        if (bestOfficial == null) {
            return;
        }
        out.add(new FieldComparison(field, input, bestOfficial, best,
                similarityCalculator.weightOf(field, profile)));
    }

    /**
     * 이 칸을 식별자로 보고 "완전일치·포함만 인정" 규칙으로 잴 것인가.
     *
     * ── 9/27 추가 ──
     * 인증번호 칸은 항상 식별자다.
     * 모델명 칸은 품번꼴 조각이 하나라도 있으면 식별자로 본다 — 프로파일 판정(MatchProfile.of)과
     * 같은 hasModelCode() 기준이다. 즉 IDENTIFIED 리콜의 모델명은 엄격 비교, UNIDENTIFIED 리콜의
     * 모델명 칸("(제품명) 허니 슬라임" 같은 상품명 문장)은 기존 편집거리 비교를 유지한다.
     *
     * 조각마다 따로 정하지 않고 칸 단위로 정하는 이유: splitList 는 원본 전체도 후보에 넣는데,
     * "MS116-1.6A / MS116-2.5A / …" 원본 전체는 20자를 넘어 품번꼴이 아니라서 조각 단위로 정하면
     * 그 후보 하나만 편집거리로 재어져 부분점수가 새어 나간다.
     *
     * 근거는 SimilarityCalculator.identifierSimilarity() 주석 참조(검증 577 오탐).
     */
    private boolean isIdentifier(boolean modelStyle, String officialList) {
        return !modelStyle || MatchProfile.hasModelCode(officialList);
    }

    /**
     * 판정 근거 문자열.
     * 어떤 프로파일로 쟀는지 같이 남긴다 — 같은 점수라도 임계값이 달라서
     * 결과가 갈리므로, 나중에 로그만 보고 판정을 재현하려면 이 정보가 필요하다.
     */
    private String buildReason(List<FieldComparison> comparisons, double score, MatchProfile profile) {
        String detail = comparisons.stream()
                .map(c -> String.format("%s %.0f%%%s", korean(c.field()), c.score() * 100,
                        c.weight() == 0.0 ? "(미반영)" : ""))
                .collect(Collectors.joining(" · "));
        return String.format("[%s] 종합 %.0f%% (%s)", korean(profile), score * 100, detail);
    }

    private String korean(MatchProfile profile) {
        return profile == MatchProfile.IDENTIFIED ? "품번기준" : "상품명기준";
    }

    private String korean(String field) {
        return switch (field) {
            case "modelName" -> "모델명";
            case "certNum" -> "인증번호";
            case "productName" -> "제품명";
            case "makerName" -> "제조사";
            case "brandName" -> "브랜드";
            case "imageLabel" -> "이미지판독";
            case CERT_MODEL_FIELD -> "인증 모델명";
            default -> field;
        };
    }
}
