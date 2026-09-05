package com.underfaker.recallcheck.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

/**
 * FR-004 이미지 업로드 검증 요청.
 * multipart/form-data 로 받으므로 record 대신 클래스 + 세터가 필요하다.
 */
public class ImageVerifyRequest {

    @NotNull
    private MultipartFile image;

    public MultipartFile getImage() {
        return image;
    }

    public void setImage(MultipartFile image) {
        this.image = image;
    }
}
