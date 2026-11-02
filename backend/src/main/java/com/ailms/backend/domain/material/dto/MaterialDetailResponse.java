package com.ailms.backend.domain.material.dto;

import com.ailms.backend.domain.material.entity.Material;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자료 상세 (frontend/src/types/material.ts의 MaterialDetail). ai로 시작하는 필드는 AI가 쓴 글이다.
 * AI 분석 전이라 aiSummary는 null, concepts는 빈 목록이다. 분석 기능이 붙으면 같은 필드를 채운다.
 */
public record MaterialDetailResponse(
		Long id,
		@Schema(description = "과목에 연결하지 않았으면 null") Long courseId,
		String title,
		String fileName,
		LocalDateTime createdAt,
		@Schema(allowableValues = {"uploaded", "processing", "ready", "failed"}) String status,
		int quizCount,
		@Schema(description = "AI가 쓴 요약. 분석 전이면 null") String aiSummary,
		@Schema(description = "자료에서 뽑은 핵심 개념. 분석 전이면 빈 목록") List<ConceptResponse> concepts) {

	public static MaterialDetailResponse from(Material material, int quizCount) {
		MaterialSummaryResponse summary = MaterialSummaryResponse.from(material, quizCount);
		return new MaterialDetailResponse(summary.id(), summary.courseId(), summary.title(), summary.fileName(),
				summary.createdAt(), summary.status(), summary.quizCount(), null, List.of());
	}

	// conceptId는 학습 대시보드의 개념 id와 같은 값을 쓴다 (개념 추출 기능에서 채운다)
	public record ConceptResponse(String conceptId, String name, String aiDescription) {
	}
}
