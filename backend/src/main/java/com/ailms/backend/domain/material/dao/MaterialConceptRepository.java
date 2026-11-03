package com.ailms.backend.domain.material.dao;

import com.ailms.backend.domain.material.entity.MaterialConcept;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MaterialConceptRepository extends JpaRepository<MaterialConcept, Long> {

	List<MaterialConcept> findAllByMaterialIdAndUserIdOrderBySortOrderAsc(Long materialId, Long userId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from MaterialConcept c where c.materialId = :materialId and c.userId = :userId")
	void deleteAllOfMaterial(Long materialId, Long userId);
}
