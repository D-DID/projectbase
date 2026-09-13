package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.RecallFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** recall_file — 리콜 사진, recall 1:N */
public interface RecallFileRepository extends JpaRepository<RecallFile, Long> {

    List<RecallFile> findByRecallUid(Long recallUid);
    /** 재동기화 시 기존 사진을 지우고 새로 적재하기 위함 (중복 방지) */
    void deleteByRecallUid(Long recallUid);
}
