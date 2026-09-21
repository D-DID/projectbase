package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.common.TextNormalizer;
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
 * ── 9/20 실데이터 적재로 드러난 것 (20260723 53건 + 20260916 2건) ──
 *
 * 이 테이블 컬럼명과 실제로 들어오는 값이 상당히 어긋난다. 매칭 로직을 짤 때 컬럼명을
 * 믿으면 안 된다. 실측은 다음과 같다.
 *
 *   recall_product_name  제품명이 아니라 <b>품목 분류명</b>이다.
 *                        예: "기타완구(완구)", "중의류(아섬)(아동용 섬유제품)",
 *                            "전지(전지(충전지만 해당))".
 *                        상품명으로 LIKE 를 걸면 영원히 안 맞는다.
 *
 *   recall_model_name    카테고리에 따라 들어오는 게 다르다.
 *                        전기·전자류는 품번이 깔끔하다 — "408PB", "VDT-E05W", "SG1250M".
 *                        어린이제품·섬유류는 품번이 아니라 <b>상품명</b>이 들어오고,
 *                        "(품번)", "(품명)", "(제품명)", "(온라인)", "(STYLE NO)" 같은
 *                        라벨이 앞에 붙는다. 55건 중 절반가량이 품번이 아니다.
 *                        즉 이 칸이 실질적인 상품명 컬럼 노릇을 한다.
 *
 *   maker_name           55건 전부 비어 있다. 제조사명은 recall_cmpny_name 에 있다.
 *                        maker_name 으로 제조사를 비교하면 비교 자체가 일어나지 않는다.
 *
 *   cert_num, barcode_num  값이 없으면 NULL 이 아니라 "-" 문자가 온다.
 *
 * 주의: recall_model_name 과 cert_num 은 단일 값이 아니라 콤마로 구분된 목록일 수 있다.
 *       (55건은 recall_model_cnt 가 전부 1이라 단일 값이었지만, 다건인 공표도 있다.)
 *       매칭 시 split 해서 개별 비교할 것.
 *
 * 9/14 수정 — 후보조회 정규화 버그: 원본 텍스트에 그대로 LIKE 를 걸어서 공백 표기 차이로
 * 후보 풀에 안 들어가던 문제. normalized_* 컬럼을 저장 시점에 채워 두고 조회도 그 컬럼
 * 기준으로 하도록 고쳤다.
 *
 * 9/20 수정 — 그 normalized_* 가 이번엔 <b>빈 문자열</b>을 만들어 반대쪽 사고를 냈다.
 * cert_num "-" 가 정규화를 거치면 "" 가 되고, 그 값으로 LIKE 를 걸면 '%%' 라 전건이
 * 후보로 끌려온다. TextNormalizer 가 이제 null 을 돌려주므로 컬럼에도 NULL 이 들어간다.
 * 모델명은 normalizeModelName() 으로 통일했다 — 저장은 normalize(), 매칭 비교는
 * normalizeModelName() 을 쓰던 탓에 저장값과 비교값의 규칙이 또 갈라져 있었다.
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

    /** recallProductName — <b>품목 분류명</b>. 컬럼명과 달리 상품명이 아니다(위 주석 참조). */
    @Column(name = "recall_product_name", length = 255)
    private String recallProductName;

    /** recallBrandName — 브랜드명. "아트박스", "넥스트유" 처럼 실제 브랜드가 들어온다. */
    @Column(name = "recall_brand_name", length = 255)
    private String recallBrandName;

    /** recallModelName — 품번 또는 상품명 (콤마 구분 목록일 수 있음) */
    @Column(name = "recall_model_name", length = 1000)
    private String recallModelName;

    /** recallModelCnt — 리콜 모델 개수 */
    @Column(name = "recall_model_cnt")
    private Integer recallModelCnt;

    /** barcodeNum — 바코드. 값이 없으면 "-" 가 온다. */
    @Column(name = "barcode_num", length = 64)
    private String barcodeNum;

    /** certNum — 인증번호 목록 (콤마 구분). 값이 없으면 "-" 가 온다. */
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

    /** recallCmpnyName — 리콜 사업자명. <b>실질적인 제조·수입사 컬럼</b>이다(maker_name 은 빈다). */
    @Column(name = "recall_cmpny_name", length = 255)
    private String recallCmpnyName;

    /** makerName — 제조사명. 실데이터 55건에서 전부 비어 있었다. */
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

    /** normalizedProductName — recallProductName 정규화 캐시. 값이 없으면 NULL. */
    @Column(name = "normalized_product_name", length = 255)
    private String normalizedProductName;

    /** normalizedModelName — recallModelName 정규화 캐시. 값이 없으면 NULL. */
    @Column(name = "normalized_model_name", length = 1000)
    private String normalizedModelName;

    /** normalizedCertNum — certNum 정규화 캐시. "-" 같은 플레이스홀더는 NULL 이 된다. */
    @Column(name = "normalized_cert_num", length = 255)
    private String normalizedCertNum;

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

    /**
     * 저장·수정 직전에 normalized_* 컬럼을 원본 필드로부터 다시 계산한다.
     * 후보조회(RecallRepository.findByNormalized*Containing)가 이 컬럼을 기준으로 검색하므로,
     * 원본 필드가 바뀌었는데 이걸 안 돌리면 9/14 버그가 재현된다.
     *
     * 9/20 — TextNormalizer 가 이제 빈 문자열 대신 null 을 돌려준다. 그 null 이 그대로
     * 컬럼에 들어가야 후보조회에서 "이 행은 이 항목으로 검색될 수 없다"가 성립한다.
     * 빈 문자열이 들어가면 LIKE '%%' 에 전건이 걸리는 사고가 다시 난다.
     *
     * 모델명은 normalizeModelName() 을 쓴다. 매칭 비교 쪽도 같은 메서드를 쓰므로
     * 저장값과 비교값의 규칙이 어긋나지 않는다.
     */
    @PrePersist
    @PreUpdate
    void normalizeFields() {
        this.normalizedProductName = TextNormalizer.normalize(this.recallProductName);
        this.normalizedModelName = TextNormalizer.normalizeModelName(this.recallModelName);
        this.normalizedCertNum = TextNormalizer.normalize(this.certNum);
    }

    /**
     * 제조·수입사명. maker_name 이 비어 있으면 recall_cmpny_name 으로 대신한다.
     * 실데이터에서는 사실상 항상 recall_cmpny_name 이 답이다.
     */
    public String resolveMakerName() {
        return (makerName == null || makerName.isBlank()) ? recallCmpnyName : makerName;
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

    /**
     * 동기화 시 기존 행 갱신.
     * syncedAt 이 매번 바뀌므로 Hibernate 가 항상 dirty 로 보고 UPDATE 를 내보낸다.
     * 덕분에 @PreUpdate → normalizeFields() 가 반드시 돌아서, 정규화 규칙을 바꾼 뒤
     * sync 를 다시 돌리는 것만으로 기존 행의 normalized_* 가 새 규칙으로 갱신된다.
     * (별도 백필 UPDATE 가 필요 없는 이유다.)
     */
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
