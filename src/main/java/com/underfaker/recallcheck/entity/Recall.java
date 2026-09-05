package com.underfaker.recallcheck.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;

/**
 * recall — 국내리콜 API 캐시.
 *
 * PK 는 국표원 Open API 의 recallUid 를 그대로 사용한다(자동 생성 아님).
 * ID 를 직접 할당하면 JPA 가 신규 여부를 판단하지 못해 save() 마다 SELECT 가 선행되므로
 * Persistable 을 구현해 isNew() 를 명시한다.
 *
 * 주의: recall_model_name 과 cert_num 은 단일 값이 아니라 콤마로 구분된 목록이다.
 *       매칭 시 split 해서 개별 비교할 것. 통째로 비교하면 절대 일치하지 않는다.
 */
@Getter
@Entity
@Table(name = "recall")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recall implements Persistable<Long> {

    /** recallUid — 리콜 아이디 */
    @Id
    @Column(name = "recall_uid")
    private Long recallUid;

    /** recallProductName — 제품명 */
    @Column(name = "recall_product_name", length = 255)
    private String recallProductName;

    /** recallBrandName — 브랜드명 */
    @Column(name = "recall_brand_name", length = 255)
    private String recallBrandName;

    /** recallModelName — 모델명 목록 (콤마 구분) */
    @Column(name = "recall_model_name", length = 1000)
    private String recallModelName;

    /** recallModelCnt — 리콜 모델 개수 */
    @Column(name = "recall_model_cnt")
    private Integer recallModelCnt;

    /** barcodeNum — 바코드 */
    @Column(name = "barcode_num", length = 64)
    private String barcodeNum;

    /** certNum — 인증번호 목록 (콤마 구분) */
    @Column(name = "cert_num", length = 255)
    private String certNum;

    /** categoryName — 제품분류명 */
    @Column(name = "category_name", length = 255)
    private String categoryName;

    /** recallTypeName — 리콜종류 (자발적리콜 / 권고에따른리콜 / 명령에따른리콜) */
    @Column(name = "recall_type_name", length = 100)
    private String recallTypeName;

    /** recallMeans — 리콜방법 */
    @Column(name = "recall_means", length = 255)
    private String recallMeans;

    /** recallCmpnyName — 리콜 사업자명 */
    @Column(name = "recall_cmpny_name", length = 255)
    private String recallCmpnyName;

    /** makerName — 제조사명 */
    @Column(name = "maker_name", length = 255)
    private String makerName;

    /** makingCntryName — 제조 국가명 */
    @Column(name = "making_cntry_name", length = 255)
    private String makingCntryName;

    /** publishDate — 공표일 (yyyyMMdd 문자열 그대로 보관) */
    @Column(name = "publish_date", columnDefinition = "CHAR(8)")
    private String publishDate;

    /** harmDscr — 제품 결함 */
    @Column(name = "harm_dscr", columnDefinition = "TEXT")
    private String harmDscr;

    /** accidentCaseDscr — 위해 정보 */
    @Column(name = "accident_case_dscr", columnDefinition = "TEXT")
    private String accidentCaseDscr;

    /** publishActionDscr — 소비자 행동요령 */
    @Column(name = "publish_action_dscr", columnDefinition = "TEXT")
    private String publishActionDscr;

    /** 마지막 동기화 시각 */
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

    @Builder
    public Recall(Long recallUid, String recallProductName, String recallBrandName,
                  String recallModelName, Integer recallModelCnt, String barcodeNum, String certNum,
                  String categoryName, String recallTypeName, String recallMeans, String recallCmpnyName,
                  String makerName, String makingCntryName, String publishDate,
                  String harmDscr, String accidentCaseDscr, String publishActionDscr) {
        this.recallUid = recallUid;
        this.recallProductName = recallProductName;
        this.recallBrandName = recallBrandName;
        this.recallModelName = recallModelName;
        this.recallModelCnt = recallModelCnt;
        this.barcodeNum = barcodeNum;
        this.certNum = certNum;
        this.categoryName = categoryName;
        this.recallTypeName = recallTypeName;
        this.recallMeans = recallMeans;
        this.recallCmpnyName = recallCmpnyName;
        this.makerName = makerName;
        this.makingCntryName = makingCntryName;
        this.publishDate = publishDate;
        this.harmDscr = harmDscr;
        this.accidentCaseDscr = accidentCaseDscr;
        this.publishActionDscr = publishActionDscr;
        this.syncedAt = LocalDateTime.now();
    }

    /** 동기화 시 기존 행 갱신 */
    public void syncFrom(Recall fresh) {
        this.recallProductName = fresh.recallProductName;
        this.recallBrandName = fresh.recallBrandName;
        this.recallModelName = fresh.recallModelName;
        this.recallModelCnt = fresh.recallModelCnt;
        this.barcodeNum = fresh.barcodeNum;
        this.certNum = fresh.certNum;
        this.categoryName = fresh.categoryName;
        this.recallTypeName = fresh.recallTypeName;
        this.recallMeans = fresh.recallMeans;
        this.recallCmpnyName = fresh.recallCmpnyName;
        this.makerName = fresh.makerName;
        this.makingCntryName = fresh.makingCntryName;
        this.publishDate = fresh.publishDate;
        this.harmDscr = fresh.harmDscr;
        this.accidentCaseDscr = fresh.accidentCaseDscr;
        this.publishActionDscr = fresh.publishActionDscr;
        this.syncedAt = LocalDateTime.now();
    }
}
