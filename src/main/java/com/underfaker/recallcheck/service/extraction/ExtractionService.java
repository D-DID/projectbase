package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.dto.internal.ExtractedProduct;
import com.underfaker.recallcheck.dto.request.ManualInputRequest;
import com.underfaker.recallcheck.repository.ExtractionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** FR-007 통합 진입점, 소스별 분기 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExtractionService {

    private final ExtractionRepository extractionRepository;
    private final WebTextExtractor webTextExtractor;
    private final ImageExtractor imageExtractor;
    private final IdentityMerger identityMerger;

    /** FR-005 경로 — URL 에서 추출 */
    public ExtractedProduct extractFromUrl(Long verificationId, String url) {
        // TODO webTextExtractor 호출 → Extraction 저장 → identityMerger 로 통합
        throw new UnsupportedOperationException("TODO: extractFromUrl");
    }

    /** FR-006 경로 — 이미지에서 추출 */
    public ExtractedProduct extractFromImage(Long verificationId, MultipartFile image) {
        // TODO imageExtractor 호출 → Extraction 저장 → identityMerger 로 통합
        throw new UnsupportedOperationException("TODO: extractFromImage");
    }

    /** FR-008 경로 — 사용자 직접 입력 */
    public ExtractedProduct extractFromManualInput(Long verificationId, ManualInputRequest request) {
        // TODO MANUAL 소스 Extraction 저장 후 기존 추출 결과와 통합
        throw new UnsupportedOperationException("TODO: extractFromManualInput");
    }
}
