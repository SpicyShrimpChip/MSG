package com.ailms.backend.domain.material.dto;

import com.ailms.backend.domain.material.entity.Material;
import io.swagger.v3.oas.annotations.media.Schema;

// 자료 올리기 응답 (frontend/src/types/material.ts의 Material)
public record MaterialUploadResponse(
		Long id,
		@Schema(description = "과목에 연결하지 않았으면 null") Long courseId,
		String title) {

	public static MaterialUploadResponse from(Material material) {
		return new MaterialUploadResponse(material.getId(), material.getCourseId(), material.getTitle());
	}
}
