package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.SourceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** extraction — 입력 소스별 추출 식별 정보 */
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

    @Builder
    public Extraction(Long verificationId, SourceType source, String productName, String brandName,
                      String modelName, String makerName, String barcodeNum, String certNum,
                      String rawText, Double confidence) {
        this.verificationId = verificationId;
        this.source = source;
        this.productName = productName;
        this.brandName = brandName;
        this.modelName = modelName;
        this.makerName = makerName;
        this.barcodeNum = barcodeNum;
        this.certNum = certNum;
        this.rawText = rawText;
        this.confidence = confidence;
    }
}
