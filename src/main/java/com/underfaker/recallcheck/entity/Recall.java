package com.underfaker.recallcheck.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * recall — 국내리콜 API 캐시.
 *
 * PK 는 국표원 Open API 의 recallUid 를 그대로 사용한다(자동 생성 아님).
 * ID 직접 할당 시 JPA 가 신규 여부를 판단하지 못해 save() 마다 SELECT 가 선행되므로
 * Persistable 을 구현해 isNew() 를 명시한다.
 *
 * TODO 필드는 실제 DDL 및 Open API 인터페이스 설계서 v2.0 과 대조해 확정할 것.
 *      명세 불일치 확인 항목: recallCmpnDivName, recallurl/recallUrl, accidentCaseDscr
 */
@Getter
@Entity
@Table(name = "recall")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recall implements Persistable<Long> {

    @Id
    @Column(name = "recall_uid")
    private Long recallUid;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "maker_name", length = 255)
    private String makerName;

    @Column(name = "cert_num", length = 64)
    private String certNum;

    /** 리콜 종류 (recallCmpnDivName) */
    @Column(name = "recall_type", length = 100)
    private String recallType;

    /** 제품 결함 / 리콜 사유 */
    @Column(name = "defect_content", length = 2000)
    private String defectContent;

    /** 소비자 행동요령 */
    @Column(name = "consumer_action", length = 2000)
    private String consumerAction;

    /** 공표일 */
    @Column(name = "announced_at")
    private LocalDate announcedAt;

    /** 공표문 원문 링크 (recallUrl) */
    @Column(name = "recall_url", length = 1000)
    private String recallUrl;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Transient
    private boolean isNew = true;

    @Override
    public Long getId() {
        return recallUid;
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
}
