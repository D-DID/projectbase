package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.FileDiv;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * recall_file — 리콜 사진 (recall 1:N).
 * recallDetail.json 의 recallFiles 배열에 대응한다.
 */
@Getter
@Entity
@Table(name = "recall_file")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecallFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recall_file_id")
    private Long id;

    @Column(name = "recall_uid", nullable = false)
    private Long recallUid;

    /**
     * fileDiv — 전체사진 / 부분사진.
     * DB ENUM 값이 한글이라 @Enumerated 로는 매핑되지 않는다. FileDiv 의 컨버터가 처리한다.
     */
    @Column(name = "file_div", length = 10)
    private FileDiv fileDiv;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Builder
    public RecallFile(Long recallUid, FileDiv fileDiv, String imageUrl) {
        this.recallUid = recallUid;
        this.fileDiv = fileDiv;
        this.imageUrl = imageUrl;
    }
}
