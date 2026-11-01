package com.ailms.backend.domain.analysis.dao;

import com.ailms.backend.domain.analysis.entity.AnalysisRun;
import com.ailms.backend.domain.analysis.entity.AnalysisRunStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 분석은 없는 것과 같게 다룬다
public interface AnalysisRunRepository extends JpaRepository<AnalysisRun, Long> {

	Optional<AnalysisRun> findByIdAndUserId(Long id, Long userId);

	Optional<AnalysisRun> findFirstByUserIdAndCourseIdAndStatusInOrderByIdDesc(Long userId, Long courseId,
			Collection<AnalysisRunStatus> statuses);

	long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

	boolean existsByUserIdAndStatusAndCreatedAtAfter(Long userId, AnalysisRunStatus status, LocalDateTime after);

	@Query("select r.id from AnalysisRun r where r.userId = :userId")
	List<Long> findIdsByUserId(Long userId);

	@Query("select distinct c.runId from AnalysisRunConversation c where c.conversationId = :conversationId")
	List<Long> findIdsUsingConversation(Long conversationId);

	// 모델 결과·판단·근거·비교·사용한 대화 기록은 외래 키(ON DELETE CASCADE)로 DB가 함께 지운다.
	// 피드백의 analysis_run_id는 NULL이 된다
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from AnalysisRun r where r.id in :ids")
	int deleteAllByIdIn(Collection<Long> ids);
}
