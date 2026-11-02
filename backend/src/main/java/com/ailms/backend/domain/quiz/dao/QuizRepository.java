package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.Quiz;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 퀴즈는 없는 것과 같게 다룬다
public interface QuizRepository extends JpaRepository<Quiz, Long> {

	List<Quiz> findAllByUserIdOrderByIdDesc(Long userId);

	Optional<Quiz> findByIdAndUserId(Long id, Long userId);

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
