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
                null, 1.0);

        extractionRepository.save(Extraction.builder()
                .verificationId(verificationId)
                .source(SourceType.MANUAL)
                .productName(request.productName())
                .brandName(request.brandName())
                .modelName(request.modelName())
                .makerName(request.makerName())
                .barcodeNum(request.barcodeNum())
                .certNum(request.certNum())
                .confidence(1.0)
                .build());

        // 같은 검증 요청에 이전 추출 결과가 있으면 함께 통합한다
        List<ExtractedProduct> sources = extractionRepository.findByVerificationId(verificationId).stream()
                .map(e -> new ExtractedProduct(e.getProductName(), e.getBrandName(), e.getModelName(),
                        e.getMakerName(), e.getBarcodeNum(), e.getCertNum(), e.getRawText(), e.getConfidence()))
                .toList();

        return sources.isEmpty() ? manual : identityMerger.merge(sources);
    }
}
