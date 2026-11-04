package com.ailms.backend.domain.material.dto;

import com.ailms.backend.domain.material.entity.MaterialConcept;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 자료 핵심 개념의 학습용 설명 (frontend/src/types/material.ts의 ConceptStudyNote, V11).
 * contextQuote·contextLocation만 서버가 자료 본문에서 확인한 값이고, 나머지 글은 AI가 자료 발췌를 근거로 쓴 것이다.
 */
public record ConceptStudyNoteResponse(
		@Schema(description = "자세한 설명 (AI)") String explanation,
		@Schema(description = "자료 안에서 어떤 내용과 이어지는지 (AI)") String materialContext,
		@Schema(description = "그 연결을 보여 주는 자료 원문 문장. 서버가 본문에서 확인하지 못하면 null") String contextQuote,
		@Schema(description = "contextQuote의 위치 (p. 3, 슬라이드 2). 모르면 null") String contextLocation,
		@Schema(description = "왜 중요한지 (AI)") String importance,
		@Schema(description = "이해를 돕는 짧은 예시 (AI). 없으면 null") String example,
		@Schema(description = "설명을 쓴 모델 (gpt / claude / gemini)") String generatedBy,
		LocalDateTime createdAt) {

	// 아직 설명을 만들지 않았으면 null
	public static ConceptStudyNoteResponse from(MaterialConcept concept) {
		return !concept.hasStudyNote() ? null
				: new ConceptStudyNoteResponse(concept.getExplanation(), concept.getMaterialContext(),
						concept.getContextQuote(), concept.getContextLocation(), concept.getImportance(),
						concept.getExample(), concept.getNoteProvider(), concept.getNoteCreatedAt());
	}
}
