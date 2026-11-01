package com.ailms.backend.global.storage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileDeletionTaskRepository extends JpaRepository<FileDeletionTask, Long> {

	// 다시 지울 때 한 번에 가져오는 양 (오래된 것부터)
	List<FileDeletionTask> findTop100ByOrderByIdAsc();
}
