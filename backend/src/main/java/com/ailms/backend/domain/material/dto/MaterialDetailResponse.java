package com.ailms.backend.domain.material.dto;

import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialConcept;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 자료 상세 (frontend/src/types/material.ts의 MaterialDetail). ai로 시작하는 필드는 AI가 쓴 글이다.
 * aiSummary는 아직 만들지 않아 null이다. concepts는 "핵심 개념 뽑기"(POST /api/materials/{id}/concepts)로 뽑아
 * 서버가 근거를 확인한 개념이고, 안 뽑았으면 빈 목록이다.
 */
public record MaterialDetailResponse(
		Long id,
		@Schema(description = "과목에 연결하지 않았으면 null") Long courseId,
		String title,
		String fileName,
		LocalDateTime createdAt,
		@Schema(allowableValues = {"uploaded", "processing", "ready", "failed"}) String status,
		int quizCount,
		@Schema(description = "AI가 쓴 요약. 아직 만들지 않아 null") String aiSummary,
		@Schema(description = "자료에서 뽑은 핵심 개념. 안 뽑았으면 빈 목록") List<ConceptResponse> concepts) {

	public static MaterialDetailResponse from(Material material, int quizCount) {
		return from(material, quizCount, List.of());
	}

	public static MaterialDetailResponse from(Material material, int quizCount, List<MaterialConcept> concepts) {
		MaterialSummaryResponse summary = MaterialSummaryResponse.from(material, quizCount);
		return new MaterialDetailResponse(summary.id(), summary.courseId(), summary.title(), summary.fileName(),
				summary.createdAt(), summary.status(), summary.quizCount(), null,
				concepts.stream().map(ConceptResponse::from).toList());
	}

	// conceptId는 퀴즈·학습 분석의 개념 키와 같은 규칙(소문자). 근거 위치·문장은 서버가 본문에서 확인한 값이다
	public record ConceptResponse(
			String conceptId,
			String name,
			String aiDescription,
			@Schema(description = "근거 위치 (쪽·슬라이드). 없으면 null") String evidenceLocation,
			@Schema(description = "근거 문장 (자료 원문)") String evidenceQuote,
			@Schema(description = "과목 키워드와 같은 개념인지") boolean courseKeyword,
			@Schema(description = "학습용 자세한 설명 (AI). 아직 만들지 않았으면 null") ConceptStudyNoteResponse studyNote) {

		static ConceptResponse from(MaterialConcept concept) {
			return new ConceptResponse(concept.getConceptKey(), concept.getName(), concept.getDescription(),
					concept.getEvidenceLocation(), concept.getEvidenceQuote(), concept.isCourseKeyword(),
					ConceptStudyNoteResponse.from(concept));
		}
	}
}
