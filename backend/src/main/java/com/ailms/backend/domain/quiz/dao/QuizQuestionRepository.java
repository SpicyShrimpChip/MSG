package com.ailms.backend.domain.quiz.dao;

import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

	// 보기·개념을 한 번에 가져온다 (문제마다 조회가 따로 나가지 않게)
	@EntityGraph(attributePaths = {"choices", "concepts"})
	List<QuizQuestion> findAllByQuizIdOrderBySortOrderAsc(Long quizId);

	// 목록 화면의 문제 수. [quizId, count]
	@Query("select q.quizId, count(q) from QuizQuestion q where q.quizId in :quizIds group by q.quizId")
	List<Object[]> countByQuizIds(Collection<Long> quizIds);
}
