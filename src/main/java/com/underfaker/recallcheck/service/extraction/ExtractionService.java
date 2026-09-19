package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.entity.Extraction;
import com.underfaker.recallcheck.entity.enums.SourceType;
import com.underfaker.recallcheck.repository.ExtractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** FR-007 통합 진입점, 소스별 분기 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExtractionService {

    private final ExtractionRepository extractionRepository;
    private final WebTextExtractor webTextExtractor;
    private final ImageExtractor imageExtractor;
    private final IdentityMerger identityMerger;

    /** FR-005 경로 — URL 에서 추출 (미구현) */
    public ExtractedProduct extractFromUrl(Long verificationId, String url) {
        throw new UnsupportedOperationException("TODO: FR-005 URL 파싱 미구현");
    }

    /** FR-006 경로 — 이미지에서 추출 (미구현) */
    public ExtractedProduct extractFromImage(Long verificationId, MultipartFile image) {
        throw new UnsupportedOperationException("TODO: FR-006 OCR 미구현");
    }

    /**
     * FR-008 경로 — 사용자 직접 입력.
     * 자동 추출이 실패해도 검증을 이어갈 수 있게 하는 경로이자,
     * 현재 유일하게 end-to-end 로 동작하는 경로다.
     */
    public ExtractedProduct extractFromManualInput(Long verificationId, ManualInputRequest request) {
        ExtractedProduct manual = new ExtractedProduct(
                request.productName(), request.brandName(), request.modelName(),
                request.makerName(), request.barcodeNum(), request.certNum(),
                request.thumbnailUrl(), null, 1.0);

        extractionRepository.save(Extraction.builder()
                .verificationId(verificationId)
                .source(SourceType.MANUAL)
                .productName(request.productName())
                .brandName(request.brandName())
                .modelName(request.modelName())
                .makerName(request.makerName())
                .barcodeNum(request.barcodeNum())
                .certNum(request.certNum())
                .thumbnailUrl(request.thumbnailUrl())
                .confidence(1.0)
                .build());

        // 같은 검증 요청에 이전 추출 결과가 있으면 함께 통합한다
        List<ExtractedProduct> sources = extractionRepository.findByVerificationId(verificationId).stream()
                .map(ExtractionService::toExtractedProduct)
                .toList();

        return sources.isEmpty() ? manual : identityMerger.merge(sources);
    }

    /**
     * 9/17 추가 — 이미 저장돼 있는 추출 행을 다시 읽어 검증 당시와 같은 방식으로 통합한다.
     *
     * FR-014 판정근거(comparisons)를 조회 시점에 다시 계산하려면 검증 당시의 "입력값"이
     * 필요한데, match_result 에는 종합 점수·matched_field 문자열·reason 만 저장돼 있어
     * 항목별 입력값을 복원할 수 없다. 그래서 extraction 행을 다시 읽어서 합친다.
     * 검증할 때 쓰는 통합 규칙(IdentityMerger)을 그대로 재사용하므로 같은 값이 나온다.
     *
     * @return 추출 행이 하나도 없으면 null (호출 쪽에서 빈 근거로 처리)
     */
    @Transactional(readOnly = true)
    public ExtractedProduct loadMerged(Long verificationId) {
        List<ExtractedProduct> sources = extractionRepository.findByVerificationId(verificationId).stream()
                .map(ExtractionService::toExtractedProduct)
                .toList();

        return sources.isEmpty() ? null : identityMerger.merge(sources);
    }

    private static ExtractedProduct toExtractedProduct(Extraction e) {
        return new ExtractedProduct(
                e.getProductName(), e.getBrandName(), e.getModelName(),
                e.getMakerName(), e.getBarcodeNum(), e.getCertNum(),
                e.getThumbnailUrl(), e.getRawText(), e.getConfidence());
    }
}
