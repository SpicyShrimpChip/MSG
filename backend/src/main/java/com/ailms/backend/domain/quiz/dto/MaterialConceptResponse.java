package com.ailms.backend.domain.quiz.dto;

import com.ailms.backend.domain.material.dto.ConceptStudyNoteResponse;
import com.ailms.backend.domain.material.entity.MaterialConcept;
import io.swagger.v3.oas.annotations.media.Schema;

// 자료에서 뽑은 핵심 개념 하나 (frontend/src/types/material.ts의 MaterialConcept와 같은 이름의 필드 + 근거 + 학습용 설명)
public record MaterialConceptResponse(
		@Schema(description = "개념 키 (소문자, 퀴즈 개념 키와 같은 규칙)") String conceptId,
		String name,
		@Schema(description = "AI가 쓴 짧은 설명") String aiDescription,
		@Schema(description = "근거 위치 (서버가 본문에서 찾은 쪽·슬라이드). 위치 표시가 없는 글이면 null") String evidenceLocation,
		@Schema(description = "근거 문장 (서버가 실제 본문에 있는지 확인함)") String evidenceQuote,
		@Schema(description = "과목 키워드와 같은 개념인지 (false면 자료에서 새로 찾은 개념)") boolean courseKeyword,
		@Schema(description = "학습용 자세한 설명 (AI가 자료 발췌를 근거로 씀). 아직 만들지 않았으면 null")
		ConceptStudyNoteResponse studyNote) {

	public static MaterialConceptResponse from(MaterialConcept concept) {
		return new MaterialConceptResponse(concept.getConceptKey(), concept.getName(), concept.getDescription(),
				concept.getEvidenceLocation(), concept.getEvidenceQuote(), concept.isCourseKeyword(),
				ConceptStudyNoteResponse.from(concept));
	}
}
