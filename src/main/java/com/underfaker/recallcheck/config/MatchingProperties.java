package com.underfaker.recallcheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가중치·임계값 바인딩. 코드에 하드코딩하지 않고 application.properties 에서 주입해
 * 재컴파일 없이 튜닝할 수 있게 한다.
 */
@ConfigurationProperties(prefix = "matching")
public class MatchingProperties {

    /** 항목별 가중치 (합 1.0 기준) */
    private Weight weight = new Weight();
    /** 판정 임계값 */
    private Threshold threshold = new Threshold();

    public static class Weight {
        private double modelName = 0.50;
        private double certNum = 0.25;
        private double productName = 0.15;
        private double makerName = 0.10;

        public double getModelName() { return modelName; }
        public void setModelName(double modelName) { this.modelName = modelName; }
        public double getCertNum() { return certNum; }
        public void setCertNum(double certNum) { this.certNum = certNum; }
        public double getProductName() { return productName; }
        public void setProductName(double productName) { this.productName = productName; }
        public double getMakerName() { return makerName; }
        public void setMakerName(double makerName) { this.makerName = makerName; }
    }

    public static class Threshold {
        /** 이 값 이상이면 MATCH */
        private double match = 0.85;
        /** 이 값 이상 match 미만이면 PARTIAL */
        private double partial = 0.60;

        public double getMatch() { return match; }
        public void setMatch(double match) { this.match = match; }
        public double getPartial() { return partial; }
        public void setPartial(double partial) { this.partial = partial; }
    }

    public Weight getWeight() { return weight; }
    public void setWeight(Weight weight) { this.weight = weight; }
    public Threshold getThreshold() { return threshold; }
    public void setThreshold(Threshold threshold) { this.threshold = threshold; }
}
