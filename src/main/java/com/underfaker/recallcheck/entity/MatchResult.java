package com.underfaker.recallcheck.entity;

import com.underfaker.recallcheck.entity.enums.Decision;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** match_result — 리콜 후보별 유사도 및 판정 근거 */
@Getter
@Entity
@Table(name = "match_result")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long id;

    @Column(name = "verification_id", nullable = false)
    private Long verificationId;

    /** recall 테이블 PK (국표원 API 원본 키) */
    @Column(name = "recall_uid", nullable = false)
    private Long recallUid;

    @Column(name = "similarity_score")
    private Double similarityScore;

    /** 일치한 항목 목록 (예: "modelName,certNum") */
    @Column(name = "matched_field", length = 255)
    private String matchedField;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private Decision decision;

    /** 판정 근거 설명 — 사용자에게 그대로 노출된다 (FR-014) */
    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @Builder
    public MatchResult(Long verificationId, Long recallUid, Double similarityScore,
                       String matchedField, Decision decision, String reason) {
        this.verificationId = verificationId;
        this.recallUid = recallUid;
        this.similarityScore = similarityScore;
        this.matchedField = matchedField;
        this.decision = decision;
        this.reason = reason;
    }
}
