package com.msg.backend.domain.material.dao;

import com.msg.backend.domain.material.entity.Material;
import com.msg.backend.global.error.BusinessException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;

// 모든 조회에 소유자(userId) 조건을 함께 건다. 남의 자료는 없는 것과 같게 다룬다
public interface MaterialRepository extends JpaRepository<Material, Long> {

	String NOT_FOUND_MESSAGE = "자료를 찾을 수 없어요.";

	// 최신 자료가 위로: 올린 시각 내림차순, 같으면 id 큰 순
	List<Material> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	Optional<Material> findByIdAndUserId(Long id, Long userId);

	// 내 자료. 없거나 남의 자료면 404
	default Material getOwned(Long id, Long userId) {
		return findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}

	List<Material> findAllByUserIdAndCourseId(Long userId, Long courseId);

	List<Material> findAllByUserId(Long userId);
}
