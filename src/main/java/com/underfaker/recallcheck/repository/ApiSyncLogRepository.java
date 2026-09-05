package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.ApiSyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** api_sync_log — 동기화 실행 이력 */
public interface ApiSyncLogRepository extends JpaRepository<ApiSyncLog, Long> {

    Page<ApiSyncLog> findAllByOrderByExecutedAtDesc(Pageable pageable);
}
