package com.underfaker.recallcheck.config;

import com.underfaker.recallcheck.service.matching.MatchProfile;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가중치·임계값 바인딩. 코드에 하드코딩하지 않고 application.properties 에서 주입해
 * 재컴파일 없이 튜닝할 수 있게 한다.
 *
 * ── 9/20 프로파일 분기 ──
 * 리콜 데이터가 성격이 다른 두 덩어리(품번 있는 공산품 / 품번 없는 유아·잡화)로 갈려서
 * 가중치와 임계값을 프로파일별로 나눴다. 자세한 근거는 MatchProfile 주석 참조.
 *
 * 기존 matching.weight.* / matching.threshold.* 는 IDENTIFIED 의 값으로 그대로 살아 있다.
 * 프로퍼티를 안 고쳐도 지금 설정이 깨지지 않게 하려는 것이다.
 * 유아·잡화용은 matching.unidentified.weight.* / matching.unidentified.threshold.* 로 받는다.
 *
 * 값은 전부 잠정치다. 라벨링 데이터셋으로 튜닝한 뒤 프로퍼티만 고친다.
 */
@ConfigurationProperties(prefix = "matching")
public class MatchingProperties {

    /** 품번·인증번호로 식별되는 공산품용 (IDENTIFIED) */
    private Weight weight = Weight.identifiedDefaults();
    private Threshold threshold = Threshold.identifiedDefaults();

    /** 품번이 없는 유아·잡화용 (UNIDENTIFIED) */
    private Profile unidentified = new Profile(
            Weight.unidentifiedDefaults(), Threshold.unidentifiedDefaults());

    public static class Weight {
        private double modelName;
        private double certNum;
        private double productName;
        private double makerName;
        private double brandName;

        /**
         * 9/24 추가 — Google Vision Web Detection 판독 결과와 공표문의 텍스트 유사도.
         *
         * 2단계 "이미지 유사도" 항목을 이 이름으로 받는다. 노션에 적힌 "Google Vision/Lens
         * 이미지 유사도"는 그대로는 만들 수 없다 — Vision API 에는 이미지 대 이미지 유사도
         * 기능이 없고 Lens 는 공개 API 가 없다. 대신 Web Detection 으로 역이미지 검색을 걸어
         * bestGuessLabel·webEntities 라는 <b>텍스트</b>를 받아, 기존 SimilarityCalculator 로
         * 공표문과 대조한다. 그래서 항목 이름이 imageSimilarity 가 아니라 imageLabel 이다.
         */
        private double imageLabel;

        /**
         * 품번이 신뢰할 만한 경우. 모델명 일치가 거의 확정 신호다.
         * 상품명(productName)은 리콜 쪽이 품목 분류명이라 비중을 낮게 둔다.
         *
         * imageLabel 을 0.05 로 둔 이유: 품번이 있으면 이미 강한 단서가 있어서
         * 역이미지 검색 라벨이 판정을 뒤집을 만한 근거가 못 된다. 보조 표시용에 가깝다.
         */
        static Weight identifiedDefaults() {
            Weight w = new Weight();
            w.modelName = 0.50;
            w.certNum = 0.25;
            w.productName = 0.10;
            w.makerName = 0.10;
            w.brandName = 0.05;
            w.imageLabel = 0.05;
            return w;
        }

        /**
         * 품번이 없는 경우. 상품명과 브랜드 말고는 단서가 없다.
         * modelName 을 0 으로 두지 않고 조금 남긴 이유: 리콜 쪽 모델명 칸에 상품명이 들어 있어
         * 입력 모델명이 우연히 걸릴 때 그게 유의미한 신호이기 때문이다.
         * certNum 은 대개 "-" 라 비교 자체가 안 일어나지만, 있으면 강한 단서라 비중을 남겨 둔다.
         *
         * imageLabel 을 0.15 로 둔 이유: 품번이 없는 쪽은 상품명·브랜드 말고 단서가 없어서
         * 역이미지 검색 라벨이 실제로 정보를 보탠다. IDENTIFIED 의 세 배로 둔다.
         * 잠정치다 — 라벨링 데이터셋으로 재야 한다.
         */
        static Weight unidentifiedDefaults() {
            Weight w = new Weight();
            w.modelName = 0.05;
            w.certNum = 0.20;
            w.productName = 0.45;
            w.makerName = 0.10;
            w.brandName = 0.20;
            w.imageLabel = 0.15;
            return w;
        }

        public double getModelName() { return modelName; }
        public void setModelName(double modelName) { this.modelName = modelName; }
        public double getCertNum() { return certNum; }
        public void setCertNum(double certNum) { this.certNum = certNum; }
        public double getProductName() { return productName; }
        public void setProductName(double productName) { this.productName = productName; }
        public double getMakerName() { return makerName; }
        public void setMakerName(double makerName) { this.makerName = makerName; }
        public double getBrandName() { return brandName; }
        public void setBrandName(double brandName) { this.brandName = brandName; }
        public double getImageLabel() { return imageLabel; }
        public void setImageLabel(double imageLabel) { this.imageLabel = imageLabel; }

        /** 항목명으로 가중치를 고른다. 모르는 항목은 최소값으로 떨어뜨린다. */
        public double of(String field) {
            return switch (field) {
                case "modelName" -> modelName;
                case "certNum" -> certNum;
                case "productName" -> productName;
                case "makerName" -> makerName;
                case "brandName" -> brandName;
                case "imageLabel" -> imageLabel;
                default -> 0.05;
            };
        }
    }

    public static class Threshold {
        /** 이 값 이상이면 MATCH */
        private double match;
        /** 이 값 이상 match 미만이면 PARTIAL */
        private double partial;

        static Threshold identifiedDefaults() {
            Threshold t = new Threshold();
            t.match = 0.85;
            t.partial = 0.60;
            return t;
        }

        /**
         * 품번이 없으면 상품명 표기 차이가 그대로 점수 차이로 나타난다.
         * 판매글 상품명과 공표문 상품명이 완전히 같기를 기대할 수 없어 조금 낮춘다.
         * 다만 너무 낮추면 오탐이 늘어난다 — 이 값이 튜닝의 핵심이다.
         */
        static Threshold unidentifiedDefaults() {
            Threshold t = new Threshold();
            t.match = 0.80;
            t.partial = 0.55;
            return t;
        }

        public double getMatch() { return match; }
        public void setMatch(double match) { this.match = match; }
        public double getPartial() { return partial; }
        public void setPartial(double partial) { this.partial = partial; }
    }

    /** 프로파일 하나의 설정 묶음 */
    public static class Profile {
        private Weight weight;
        private Threshold threshold;

        public Profile() {
        }

        Profile(Weight weight, Threshold threshold) {
            this.weight = weight;
            this.threshold = threshold;
        }

        public Weight getWeight() { return weight; }
        public void setWeight(Weight weight) { this.weight = weight; }
        public Threshold getThreshold() { return threshold; }
        public void setThreshold(Threshold threshold) { this.threshold = threshold; }
    }

    /** 프로파일에 맞는 가중치 */
    public Weight weightFor(MatchProfile profile) {
        return profile == MatchProfile.UNIDENTIFIED ? unidentified.getWeight() : weight;
    }

    /** 프로파일에 맞는 임계값 */
    public Threshold thresholdFor(MatchProfile profile) {
        return profile == MatchProfile.UNIDENTIFIED ? unidentified.getThreshold() : threshold;
    }

    public Weight getWeight() { return weight; }
    public void setWeight(Weight weight) { this.weight = weight; }
    public Threshold getThreshold() { return threshold; }
    public void setThreshold(Threshold threshold) { this.threshold = threshold; }
    public Profile getUnidentified() { return unidentified; }
    public void setUnidentified(Profile unidentified) { this.unidentified = unidentified; }
}
