package com.underfaker.recallcheck.client;

/**
 * OCR 추상화 — 엔진 미정 상태로도 상위 로직을 진행할 수 있게 한다.
 * 엔진을 교체해도 ImageExtractor 는 바뀌지 않는다.
 */
public interface OcrClient {

    /**
     * @param imagePath 저장된 이미지 경로
     * @return 인식된 원문 텍스트. 인식 실패 시 빈 문자열을 반환하고 예외를 던지지 않는다.
     */
    String recognize(String imagePath);
}
