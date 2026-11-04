package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.internal.ExtractionNotes;
import com.underfaker.recallcheck.dto.internal.FieldComparison;
import com.underfaker.recallcheck.dto.internal.ImageInsight;
import com.underfaker.recallcheck.dto.internal.KcLookup;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.entity.Extraction;
import com.underfaker.recallcheck.entity.enums.SourceType;
import com.underfaker.recallcheck.repository.ExtractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** FR-007 통합 진입점, 소스별 분기 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExtractionService {

    private final ExtractionRepository extractionRepository;
    private final WebTextExtractor webTextExtractor;
    private final IdentityMerger identityMerger;

    /**
     * FR-005 경로(10/31 구현) — 서버가 상품 페이지에서 뽑은 값(WebTextExtractor)을 source=URL 로 저장하고 통합한다.
     * 칸·[detail] 블록(KC 칸 상태·원문)은 직접입력과 같다.
     */
    public ExtractedProduct extractFromUrl(Long verificationId, ManualInputRequest parsed) {
        return saveAndMerge(verificationId, SourceType.URL, parsed);
    }

    /**
     * FR-004 경로(10/31) — 사진 업로드 검증. 판매자 입력이 없으므로 사진으로 찾은 상품명(여러 사이트에서 반복된 제목)만
     * source=IMAGE 행에 둔다. 못 찾았으면 상품명도 비어 있다(판정 '확인불가').
     */
    public ExtractedProduct extractFromImage(Long verificationId, String productNameFromImage) {
        return saveAndMerge(verificationId, SourceType.IMAGE,
                new ManualInputRequest(productNameFromImage, null, null, null, null, null, null, null, null));
    }

    /**
     * FR-008 경로 — 사용자 직접 입력.
     * 자동 추출이 실패해도 검증을 이어갈 수 있게 하는 경로이자,
     * 현재 유일하게 end-to-end 로 동작하는 경로다.
     */
    public ExtractedProduct extractFromManualInput(Long verificationId, ManualInputRequest request) {
        return saveAndMerge(verificationId, SourceType.MANUAL, request);
    }

    private ExtractedProduct saveAndMerge(Long verificationId, SourceType source, ManualInputRequest request) {
        ExtractedProduct manual = new ExtractedProduct(
                request.productName(), request.brandName(), request.modelName(),
                request.makerName(), request.barcodeNum(), request.certNum(),
                request.thumbnailUrl(), null, 1.0);

        extractionRepository.save(Extraction.builder()
                .verificationId(verificationId)
                .source(source)
                .productName(request.productName())
                .brandName(request.brandName())
                .modelName(request.modelName())
                .makerName(request.makerName())
                .barcodeNum(request.barcodeNum())
                .certNum(request.certNum())
                .thumbnailUrl(request.thumbnailUrl())
                // 9/27 — 확장이 읽은 상세페이지 KC 인증정보 상태. 없으면 null(블록 안 만듦)
                .rawText(ExtractionNotes.detailBlock(request.kcStatus(), request.kcText()))
                .confidence(1.0)
                .build());

        // 같은 검증 요청에 이전 추출 결과가 있으면 함께 통합한다
        List<ExtractedProduct> sources = extractionRepository.findByVerificationId(verificationId).stream()
                .map(ExtractionService::toExtractedProduct)
                .toList();

        return sources.isEmpty() ? manual : identityMerger.merge(sources);
    }

    /**
     * 10/31 — 판정 근거 스냅샷을 남긴다(ExtractionNotes.evidenceBlock). 이전 스냅샷은 지우고 새로 붙인다.
     * 대표 후보가 없으면 지우기만 한다.
     */
    public void saveEvidence(Long verificationId, Long recallUid, List<FieldComparison> comparisons) {
        Extraction target = primaryRow(verificationId);
        if (target == null) {
            return;
        }
        target.replaceRawText(ExtractionNotes.withoutBlocks(target.getRawText(),
                ExtractionNotes.EVIDENCE_BEGIN, ExtractionNotes.EVIDENCE_END));
        target.appendRawText(ExtractionNotes.evidenceBlock(recallUid, comparisons));
        extractionRepository.save(target);
    }

    /**
     * 9/17 추가 — 이미 저장돼 있는 추출 행을 다시 읽어 검증 당시와 같은 방식으로 통합한다.
     *
     * FR-014 판정근거(comparisons)를 조회 시점에 다시 계산하려면 검증 당시의 "입력값"이
     * 필요한데, match_result 에는 종합 점수·matched_field 문자열·reason 만 저장돼 있어
     * 항목별 입력값을 복원할 수 없다. 그래서 extraction 행을 다시 읽어서 합친다.
     * 검증할 때 쓰는 통합 규칙(IdentityMerger)을 그대로 재사용하므로 같은 값이 나온다.
     *
     * 10/3 — [kc-lookup] 블록이 있으면 검증 때와 같게 적용한다(정리된 인증번호, 인증 모델명 근거).
     * 이걸 안 하면 판정근거 화면이 KC 조회 없이 다시 계산돼 판정과 근거가 어긋난다.
     *
     * @return 추출 행이 하나도 없으면 null (호출 쪽에서 빈 근거로 처리)
     */
    @Transactional(readOnly = true)
    public ExtractedProduct loadMerged(Long verificationId) {
        List<ExtractedProduct> sources = extractionRepository.findByVerificationId(verificationId).stream()
                .map(ExtractionService::toExtractedProduct)
                .toList();

        return sources.isEmpty() ? null : KcLookup.applyFromRawText(identityMerger.merge(sources));
    }

    /**
     * 10/3 추가 — KC 인증 DB 조회 결과를 남긴다(KC인증 연동).
     *
     * saveImageInsight 와 같은 방식 — 첫 추출 행(직접입력이면 MANUAL 행)의 raw_text 에 [kc-lookup] 블록을 덧붙인다.
     * 새 컬럼·새 source 값을 쓰지 않는 이유는 KcLookup 주석 참조(ddl-auto=validate, source ENUM).
     */
    public void saveKcLookup(Long verificationId, KcLookup lookup) {
        if (lookup == null) {
            return;
        }
        Extraction target = primaryRow(verificationId);
        if (target == null) {
            return;
        }
        target.appendRawText(lookup.toBlock());
        extractionRepository.save(target);
    }

    /**
     * 9/24 추가 — 이번 검증에서 쓴 Google Vision 판독 결과를 남긴다.
     *
     * 같은 검증 요청의 첫 추출 행(직접입력이면 MANUAL 행)의 raw_text 에 블록으로 덧붙인다.
     * 관리 상태 엔티티라 트랜잭션 커밋 때 UPDATE 된다.
     * 판독 결과가 없거나 추출 행이 없으면 아무것도 하지 않는다 — 판정 자체를 막지 않는다.
     */
    public void saveImageInsight(Long verificationId, ImageInsight insight) {
        if (insight == null || !insight.isUsable()) {
            return;
        }
        List<Extraction> rows = extractionRepository.findByVerificationId(verificationId);
        if (rows.isEmpty()) {
            return;
        }
        Extraction target = rows.stream()
                .filter(e -> e.getSource() == SourceType.MANUAL)
                .findFirst()
                .orElse(rows.get(0));
        target.appendRawText(insight.toRawTextBlock());
        extractionRepository.save(target);
    }

    /**
     * 9/27 추가 — 사진 확인(사용자 클릭)을 했다는 표시와 결과를 남긴다.
     * 같은 건을 다시 눌러도 Vision 을 또 부르지 않게 하는 데 쓴다(FAILED 는 다시 누를 수 있음).
     */
    public void markImageCheck(Long verificationId, ExtractionNotes.ImageCheck result) {
        Extraction target = primaryRow(verificationId);
        if (target == null) {
            return;
        }
        target.appendRawText(ExtractionNotes.imageCheckBlock(result));
        extractionRepository.save(target);
    }

    /**
     * 10/10 — 해외직구·구매대행 상품이라 판단하지 않았다는 메모를 남긴다(ExtractionNotes.overseasBlock).
     * 화면 상태 EXCLUDED 는 이 메모로 계산한다(ResultView).
     */
    public void markOverseas(Long verificationId, String signal) {
        Extraction target = primaryRow(verificationId);
        if (target == null) {
            return;
        }
        target.appendRawText(ExtractionNotes.overseasBlock(signal));
        extractionRepository.save(target);
    }

    /**
     * 10/10 — 해외직구·구매대행이지만 어린이제품이라 판단한다는 메모를 남긴다(ExtractionNotes.overseasChildBlock).
     * 어린이제품은 구매대행도 KC 표시가 없으면 금지(어린이제품법 제30조) — 화면 안내(ResultView)에 쓴다.
     */
    public void markOverseasChild(Long verificationId, String signal) {
        Extraction target = primaryRow(verificationId);
        if (target == null) {
            return;
        }
        target.appendRawText(ExtractionNotes.overseasChildBlock(signal));
        extractionRepository.save(target);
    }

    /** 9/27 — 메모 블록이 붙는 행(직접입력이면 MANUAL 행). 없으면 null. */
    @Transactional(readOnly = true)
    public Extraction primaryRow(Long verificationId) {
        List<Extraction> rows = extractionRepository.findByVerificationId(verificationId);
        if (rows.isEmpty()) {
            return null;
        }
        return rows.stream()
                .filter(e -> e.getSource() == SourceType.MANUAL)
                .findFirst()
                .orElse(rows.get(0));
    }

    /**
     * 9/24 추가 — saveImageInsight 로 남긴 판독 결과를 되살린다. 없으면 ImageInsight.NONE.
     * FR-014 판정근거를 판정 당시와 같은 값으로 다시 계산할 때 쓴다.
     */
    @Transactional(readOnly = true)
    public ImageInsight loadImageInsight(Long verificationId) {
        for (Extraction e : extractionRepository.findByVerificationId(verificationId)) {
            ImageInsight insight = ImageInsight.fromRawText(e.getRawText());
            if (insight.isUsable()) {
                return insight;
            }
        }
        return ImageInsight.NONE;
    }

    private static ExtractedProduct toExtractedProduct(Extraction e) {
        return new ExtractedProduct(
                e.getProductName(), e.getBrandName(), e.getModelName(),
                e.getMakerName(), e.getBarcodeNum(), e.getCertNum(),
                e.getThumbnailUrl(), e.getRawText(), e.getConfidence());
    }
}
