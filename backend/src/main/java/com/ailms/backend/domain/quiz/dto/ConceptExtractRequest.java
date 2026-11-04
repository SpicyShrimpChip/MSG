package com.ailms.backend.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

// 자료 핵심 개념 뽑기. 자료 본문(발췌)을 외부 AI로 보내므로 이번 요청의 확인이 필요하다
public record ConceptExtractRequest(
		@Schema(description = "자료 본문을 외부 AI로 보내는 데 대한 이번 요청의 확인 (필수)") Boolean materialContentConsent) {
}
