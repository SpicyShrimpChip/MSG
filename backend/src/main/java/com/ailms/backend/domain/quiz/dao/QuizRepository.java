package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.Quiz;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 퀴즈는 없는 것과 같게 다룬다
public interface QuizRepository extends JpaRepository<Quiz, Long> {

	List<Quiz> findAllByUserIdOrderByIdDesc(Long userId);

	// 사전·사후평가
	Optional<Quiz> findFirstByUserIdAndMaterialIdAndAssessmentKindOrderByIdDesc(Long userId, Long materialId,
			com.ailms.backend.domain.quiz.entity.AssessmentKind kind);

	List<Quiz> findAllByUserIdAndCourseIdAndAssessmentKindOrderByIdDesc(Long userId, Long courseId,
			com.ailms.backend.domain.quiz.entity.AssessmentKind kind);

	List<Quiz> findAllByUserIdAndMaterialIdAndAssessmentKindOrderByIdDesc(Long userId, Long materialId,
			com.ailms.backend.domain.quiz.entity.AssessmentKind kind);

	List<Quiz> findAllByUserIdAndPreAssessmentIdOrderByIdDesc(Long userId, Long preAssessmentId);

	// 과목의 내 퀴즈 (최근 것부터). AI 다음 개념 제안을 모을 때 쓴다
	List<Quiz> findAllByUserIdAndCourseIdOrderByIdDesc(Long userId, Long courseId);

	Optional<Quiz> findByIdAndUserId(Long id, Long userId);

	// 개념 확인 퀴즈 (최신 순)
	List<Quiz> findAllByUserIdAndCourseIdAndCheckConceptKeyIsNotNullOrderByIdDesc(Long userId, Long courseId);

	List<Quiz> findAllByUserIdAndMaterialIdAndCheckConceptKeyIsNotNullOrderByIdDesc(Long userId, Long materialId);

	long countByUserIdAndMaterialId(Long userId, Long materialId);

	// 내 자료별 퀴즈 수 (자료 목록용, 한 번에 센다). 자료 없이 만든 퀴즈는 세지 않는다
	@Query("select q.materialId as materialId, count(q) as quizCount from Quiz q"
			+ " where q.userId = :userId and q.materialId is not null group by q.materialId")
	List<MaterialQuizCount> countByMaterialOfUser(Long userId);

	interface MaterialQuizCount {
		Long getMaterialId();

		long getQuizCount();
	}
}
