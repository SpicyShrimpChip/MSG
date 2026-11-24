package com.msg.backend.domain.material.dao;

import com.msg.backend.domain.material.entity.MaterialConcept;
import com.msg.backend.global.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;

public interface MaterialConceptRepository extends JpaRepository<MaterialConcept, Long> {

	String NOT_FOUND_MESSAGE = "이 자료에서 그 개념을 찾을 수 없어요. 핵심 개념을 먼저 뽑아 주세요.";

	List<MaterialConcept> findAllByMaterialIdAndUserIdOrderBySortOrderAsc(Long materialId, Long userId);

	// 검색용: 내 자료 전체의 개념
	List<MaterialConcept> findAllByUserIdOrderByMaterialIdAscSortOrderAsc(Long userId);

	// 커리큘럼용: 고른 자료(과목의 자료)의 개념만. 자료 id 순 → 뽑은 순서
	List<MaterialConcept> findAllByUserIdAndMaterialIdInOrderByMaterialIdAscSortOrderAsc(Long userId,
			Collection<Long> materialIds);

	// 자료의 개념 하나. 자료마다 개념 키는 하나다 (uk_material_concept)
	Optional<MaterialConcept> findByMaterialIdAndUserIdAndConceptKey(Long materialId, Long userId, String conceptKey);

	// 내 자료에서 뽑아 둔 개념. 없거나 남의 자료면 404
	default MaterialConcept getOwned(Long materialId, Long userId, String conceptKey) {
		return findByMaterialIdAndUserIdAndConceptKey(materialId, userId, conceptKey)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from MaterialConcept c where c.materialId = :materialId and c.userId = :userId")
	void deleteAllOfMaterial(Long materialId, Long userId);
}
