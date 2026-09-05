package com.underfaker.recallcheck.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * certification — KC인증 API 캐시.
 * PK 는 API 의 certUid 를 그대로 사용한다(Recall 과 동일한 이유로 Persistable 구현).
 */
@Getter
@Entity
@Table(name = "certification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification implements Persistable<Long> {

    /** certUid — 인증정보 아이디 */
    @Id
    @Column(name = "cert_uid")
    private Long certUid;

    /** certNum — 인증번호(필증번호) */
    @Column(name = "cert_num", length = 64)
    private String certNum;

    /** certState — 인증상태 (적합 / 안전인증취소 / 개선명령 / 기간만료 등) */
    @Column(name = "cert_state", length = 100)
    private String certState;

    /** certDate — 인증일자 (yyyyMMdd) */
    @Column(name = "cert_date", columnDefinition = "CHAR(8)")
    private String certDate;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "brand_name", length = 255)
    private String brandName;

    /** modelName — 모델명(로트번호) */
    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "maker_name", length = 255)
    private String makerName;

    /** makerCntryName — 제조국명 */
    @Column(name = "maker_cntry_name", length = 255)
    private String makerCntryName;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Transient
    private boolean isNew = true;

    @Override
    public Long getId() {
        return certUid;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }

    @Builder
    public Certification(Long certUid, String certNum, String certState, String certDate,
                         String productName, String brandName, String modelName,
                         String makerName, String makerCntryName) {
        this.certUid = certUid;
        this.certNum = certNum;
        this.certState = certState;
        this.certDate = certDate;
        this.productName = productName;
        this.brandName = brandName;
        this.modelName = modelName;
        this.makerName = makerName;
        this.makerCntryName = makerCntryName;
        this.syncedAt = LocalDateTime.now();
    }
}
