package com.ailms.backend.domain.course.dao;

import com.ailms.backend.domain.course.entity.Course;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 과목은 없는 것과 같게 다룬다
public interface CourseRepository extends JpaRepository<Course, Long> {

	// 키워드를 한 번에 가져온다 (과목마다 키워드 조회가 따로 나가지 않게)
	@EntityGraph(attributePaths = "keywords")
	List<Course> findAllByUserIdOrderByIdAsc(Long userId);

	Optional<Course> findByIdAndUserId(Long id, Long userId);

	boolean existsByIdAndUserId(Long id, Long userId);

	boolean existsByUserIdAndSemesterAndNameKey(Long userId, String semester, String nameKey);

	boolean existsByUserIdAndSemesterAndNameKeyAndIdNot(Long userId, String semester, String nameKey, Long id);
}
