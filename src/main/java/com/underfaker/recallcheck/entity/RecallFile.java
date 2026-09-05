package com.underfaker.recallcheck.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * recall_file — 리콜 사진 (recall 1:N).
 * TODO 컬럼명은 실제 DDL 과 대조해 확정할 것.
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

    @Column(name = "file_url", length = 1000)
    private String fileUrl;
}
