package com.underfaker.recallcheck.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * certification — KC인증 API 캐시.
 * PK 는 인증번호(certNum)를 그대로 사용한다.
 * TODO 필드는 실제 DDL 및 certificationList/certificationDetail 응답과 대조해 확정할 것.
 */
@Getter
@Entity
@Table(name = "certification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification {

    @Id
    @Column(name = "cert_num", length = 64)
    private String certNum;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "maker_name", length = 255)
    private String makerName;

    @Column(name = "certified_at")
    private LocalDate certifiedAt;
}
