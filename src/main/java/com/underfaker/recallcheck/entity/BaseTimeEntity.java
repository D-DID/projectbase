package com.underfaker.recallcheck.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/** created_at·updated_at 공통 상속 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 10/31 — 값은 그대로인데 '마지막 확인 시각'만 갱신할 때(같은 입력 재확인). 필드를 바꿔야 Hibernate 가
     * dirty 로 보고 UPDATE 를 내보내고, 그때 @LastModifiedDate 가 다시 현재 시각을 넣는다.
     */
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
