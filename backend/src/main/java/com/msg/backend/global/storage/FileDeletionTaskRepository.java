package com.msg.backend.global.storage;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FileDeletionTaskRepository extends JpaRepository<FileDeletionTask, Long> {

	// 다시 지울 작업을 마지막 시도가 오래된 것(시도한 적 없으면 만든 시각)부터 가져온다.
	// NULL 정렬 위치가 H2·PostgreSQL에서 다를 수 있어 nulls first 대신 coalesce를 쓴다
	@Query("select t from FileDeletionTask t order by coalesce(t.lastAttemptedAt, t.createdAt), t.id")
	List<FileDeletionTask> findRetryBatch(Pageable pageable);
}
