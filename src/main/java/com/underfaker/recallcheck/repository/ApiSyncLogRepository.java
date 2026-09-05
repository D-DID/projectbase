package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.ApiSyncLog;
import com.underfaker.recallcheck.entity.enums.ApiType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** api_sync_log — 동기화 실행 이력 */
public interface ApiSyncLogRepository extends JpaRepository<ApiSyncLog, Long> {

    Page<ApiSyncLog> findAllByOrderByStartedAtDesc(Pageable pageable);

    /** 마지막 성공 동기화 시각 확인용 */
    Optional<ApiSyncLog> findFirstByApiTypeOrderByStartedAtDesc(ApiType apiType);
}
