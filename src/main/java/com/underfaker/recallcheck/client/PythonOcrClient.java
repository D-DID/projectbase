package com.underfaker.recallcheck.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Python OCR 서버 HTTP 호출 구현체.
 *
 * OCR 모듈은 Python 으로 분리해 FastAPI 등으로 띄우고 HTTP 로 호출한다.
 * ProcessBuilder 로 스크립트를 직접 실행하면 동시 요청·타임아웃·에러 전파를
 * 전부 직접 처리해야 하므로 권장하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class PythonOcrClient implements OcrClient {

    @Qualifier("ocrRestClient")
    private final RestClient ocrRestClient;

    @Override
    public String recognize(String imagePath) {
        // TODO POST {ocr.base-url}/ocr  body: { "imagePath": ... }  응답: { "text": ... }
        throw new UnsupportedOperationException("TODO: PythonOcrClient.recognize");
    }
}
