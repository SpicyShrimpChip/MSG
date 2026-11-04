package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 자료 핵심 개념 하나의 학습용 설명 만들기. 자료 본문(발췌)을 외부 AI로 보내므로 이번 요청의 확인이 필요하다
public record ConceptStudyNoteRequest(
		@Schema(description = "설명을 만들 개념의 conceptId (GET /api/materials/{id}/concepts의 값)")
		@NotBlank @Size(max = 40) String conceptId,
		@Schema(description = "자료 본문을 외부 AI로 보내는 데 대한 이번 요청의 확인 (필수)") Boolean materialContentConsent) {
}
