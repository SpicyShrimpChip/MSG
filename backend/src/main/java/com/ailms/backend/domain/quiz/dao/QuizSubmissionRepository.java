package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

	Optional<QuizSubmission> findFirstByQuizIdAndUserIdOrderByIdDesc(Long quizId, Long userId);

	// 퀴즈마다 가장 최근 제출 하나
	@Query("select s from QuizSubmission s where s.id in ("
			+ "select max(l.id) from QuizSubmission l where l.userId = :userId and l.quizId in :quizIds group by l.quizId)")
	List<QuizSubmission> findLatestByQuizIds(Long userId, Collection<Long> quizIds);

	// 24시간 동안 AI 채점을 부른 제출 수 (채점 호출 한도). 답에 AI를 부른 기록(ai_called_count)이 있는 제출만 센다
	@Query("select count(distinct s.id) from QuizSubmission s, QuizAnswer a where a.submissionId = s.id"
			+ " and s.userId = :userId and s.submittedAt > :after and a.aiCalledCount > 0")
	long countAiGradedSince(Long userId, java.time.LocalDateTime after);

	// 과목의 퀴즈마다 가장 최근 제출 (개념별 집계)
	@Query("select s from QuizSubmission s where s.id in (select max(l.id) from QuizSubmission l, Quiz q"
			+ " where l.quizId = q.id and q.userId = :userId and q.courseId = :courseId and l.userId = :userId"
			+ " group by l.quizId)")
	List<QuizSubmission> findLatestInCourse(Long userId, Long courseId);

	// 과목의 일반 퀴즈(사전·사후평가 제외)마다 가장 최근 제출 (커리큘럼의 개념별 확인 퀴즈 단계)
	@Query("select s from QuizSubmission s where s.id in (select max(l.id) from QuizSubmission l, Quiz q"
			+ " where l.quizId = q.id and q.userId = :userId and q.courseId = :courseId and l.userId = :userId"
			+ " and q.assessmentKind is null group by l.quizId)")
	List<QuizSubmission> findLatestPracticeInCourse(Long userId, Long courseId);
}
