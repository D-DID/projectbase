package com.underfaker.recallcheck.repository;

import com.underfaker.recallcheck.entity.RecallFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** recall_file — 리콜 사진, recall 1:N */
public interface RecallFileRepository extends JpaRepository<RecallFile, Long> {

    List<RecallFile> findByRecallUid(Long recallUid);
}
