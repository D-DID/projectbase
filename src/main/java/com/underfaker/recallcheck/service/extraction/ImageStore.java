package com.underfaker.recallcheck.service.extraction;

import com.underfaker.recallcheck.exception.BusinessException;
import com.underfaker.recallcheck.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.UUID;

/**
 * 10/31 — FR-004 사진 업로드 검증의 원본 사진 저장소. app.upload.dir(기본 ./uploads — .gitignore 대상) 아래
 * yyyyMM/무작위이름.확장자 로 저장하고, verification.image_path 에 남길 상대 경로를 돌려준다.
 *
 * 사용자가 준 파일 이름은 쓰지 않는다(경로 조작 방지). 형식은 첫 바이트로 판단한다(Content-Type 은 믿지 않는다).
 */
@Slf4j
@Component
public class ImageStore {

    /** Vision 이 받는 형식 중 브라우저에서 흔한 것만 */
    public enum Kind { JPEG, PNG, GIF, WEBP }

    /** 한 장 최대 크기 — Vision 요청 한도(20MB)보다 작게, 휴대폰 사진(2~6MB)은 들어가게 */
    public static final long MAX_BYTES = 8L * 1024 * 1024;

    private final Path root;

    public ImageStore(@Value("${app.upload.dir:./uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
    }

    /** 첫 바이트로 형식을 본다. 지원하지 않으면 null */
    public static Kind kindOf(byte[] b) {
        if (b == null || b.length < 12) {
            return null;
        }
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Kind.JPEG;
        }
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return Kind.PNG;
        }
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return Kind.GIF;
        }
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B'
                && b[11] == 'P') {
            return Kind.WEBP;
        }
        return null;
    }

    /**
     * 검사 후 저장한다.
     * @return root 기준 상대 경로(예: 202610/3f2c….jpg)
     * @throws BusinessException V002 — 빈 파일·크기 초과·지원하지 않는 형식, C002 — 디스크 쓰기 실패
     */
    public String save(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE, "사진 파일이 비어 있습니다.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE,
                    "사진이 너무 큽니다(최대 " + (MAX_BYTES / 1024 / 1024) + "MB).");
        }
        Kind kind = kindOf(bytes);
        if (kind == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE, "JPG·PNG·GIF·WEBP 사진만 올릴 수 있습니다.");
        }
        String ext = switch (kind) {
            case JPEG -> "jpg";
            case PNG -> "png";
            case GIF -> "gif";
            case WEBP -> "webp";
        };
        String month = YearMonth.now().toString().replace("-", "");
        String relative = month + "/" + UUID.randomUUID() + "." + ext;
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("[ImageStore] 저장 실패 {}: {}", target, e.toString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "사진을 저장하지 못했습니다.");
        }
        return relative;
    }
}
