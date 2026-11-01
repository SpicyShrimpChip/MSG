package com.ailms.backend.domain.material.dao;

import com.ailms.backend.domain.material.entity.Material;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 자료는 없는 것과 같게 다룬다
public interface MaterialRepository extends JpaRepository<Material, Long> {

	// 최신 자료가 위로: 올린 시각 내림차순, 같으면 id 큰 순
	List<Material> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	Optional<Material> findByIdAndUserId(Long id, Long userId);

	List<Material> findAllByUserIdAndCourseId(Long userId, Long courseId);
}
