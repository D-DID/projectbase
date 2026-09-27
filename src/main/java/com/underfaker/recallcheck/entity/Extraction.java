package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.SourceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * extraction — 입력 소스별 추출 식별 정보.
 *
 * 9/17 추가 — thumbnail_url. 크롬 확장이 쿠팡 주문내역에서 가져온 항목 썸네일 URL 을
 * 여기 보관한다(9/12 수신 규격 결정). 1단계에서는 저장만 하고 판정에는 쓰지 않는다 —
 * 2단계 Vision/Lens 이미지 유사도 판정의 입력이 된다.
 * 주의: ddl-auto=validate 라 schema.sql 을 고쳐도 실행 DB 에는 반영되지 않는다.
 * 운영 중인 DB 에 ALTER TABLE 을 직접 실행해야 한다(channel 컬럼 때와 동일).
 */
@Getter
@Entity
@Table(name = "extraction")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Extraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "extraction_id")
    private Long id;

    @Column(name = "verification_id", nullable = false)
    private Long verificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private SourceType source;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "brand_name", length = 255)
    private String brandName;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "maker_name", length = 255)
    private String makerName;

    @Column(name = "barcode_num", length = 64)
    private String barcodeNum;

    @Column(name = "cert_num", length = 64)
    private String certNum;

    /** thumbnailUrl — 확장이 보낸 주문내역 항목 썸네일 이미지 URL (9/17 추가) */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * raw_text 끝에 텍스트 블록을 덧붙인다. 기존 내용은 지우지 않는다.
     *
     * 9/24 추가 — Google Vision 판독 결과(ImageInsight.toRawTextBlock)를 여기 남긴다.
     * 새 컬럼 대신 raw_text 를 쓰는 이유는 ImageInsight.RAW_TEXT_BEGIN 주석 참조
     * (ddl-auto=validate 라 컬럼을 늘리면 ALTER 안 한 DB 에서 기동 실패).
     */
    public void appendRawText(String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        this.rawText = (rawText == null || rawText.isBlank()) ? block : rawText + "\n" + block;
    }

    @Builder
    public Extraction(Long verificationId, SourceType source, String productName, String brandName,
                      String modelName, String makerName, String barcodeNum, String certNum,
                      String thumbnailUrl, String rawText, Double confidence) {
        this.verificationId = verificationId;
        this.source = source;
        this.productName = productName;
        this.brandName = brandName;
        this.modelName = modelName;
        this.makerName = makerName;
        this.barcodeNum = barcodeNum;
        this.certNum = certNum;
        this.thumbnailUrl = thumbnailUrl;
        this.rawText = rawText;
        this.confidence = confidence;
    }
}
