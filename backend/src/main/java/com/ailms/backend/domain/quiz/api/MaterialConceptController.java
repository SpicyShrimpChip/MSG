package com.ailms.backend.domain.quiz.api;

import com.ailms.backend.domain.quiz.application.MaterialConceptService;
import com.ailms.backend.domain.quiz.dto.ConceptExtractRequest;
import com.ailms.backend.domain.quiz.dto.ConceptStudyNoteRequest;
import com.ailms.backend.domain.quiz.dto.MaterialConceptResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 학습자료 핵심 개념. 로그인한 본인 자료만, 남의 자료는 404
@Tag(name = "사전·사후평가")
@RestController
@RequestMapping("/api/materials/{materialId}/concepts")
@RequiredArgsConstructor
public class MaterialConceptController {

	private final MaterialConceptService conceptService;

	@Operation(summary = "자료에서 뽑은 핵심 개념", description = "저장된 개념과 근거(쪽·슬라이드, 문장). 아직 안 뽑았으면 빈 목록.")
	@GetMapping
	public ApiResponse<List<MaterialConceptResponse>> list(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId) {
		return ApiResponse.success(conceptService.list(authUser.userId(), materialId));
	}

	@Operation(summary = "자료 핵심 개념 뽑기", description = "자료 본문(TXT·MD·PPTX·텍스트 PDF, 발췌)을 외부 AI로 보내 핵심 개념을 고르고, "
			+ "서버가 개념 이름과 근거 문장이 본문에 실제로 있는지 확인한 것만 저장한다(이전 개념은 바뀐다). "
			+ "자료 본문 전송 기능이 꺼져 있거나 AI 퀴즈 처리 동의가 없으면 403, 확인이 없으면 400, 읽을 수 없는 자료"
			+ "(암호·스캔 PDF·손상)·개인정보가 있는 본문 422(data.reason), 모델 없음 503, 한도 429, 확인된 개념이 없으면 502.")
	@PostMapping
	public ApiResponse<List<MaterialConceptResponse>> extract(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId, @RequestBody ConceptExtractRequest request) {
		return ApiResponse.success(conceptService.extract(authUser.userId(), materialId,
				Boolean.TRUE.equals(request.materialContentConsent())));
	}

	@Operation(summary = "핵심 개념 하나의 학습용 설명 만들기", description = "저장된 개념 하나에 대해 자세한 설명·자료와의 연결·중요한 이유·예시를 "
			+ "AI가 자료 본문 발췌를 근거로 쓴다(studyNote). 자료와의 연결 문장(contextQuote)은 서버가 본문에 그대로 있는지 확인한 경우에만 "
			+ "남긴다. 개념 이름·근거는 바꾸지 않는다(사전·사후평가 개념 체계 유지). 다시 만들면 이전 설명을 바꾼다. 보내기 조건과 오류는 "
			+ "핵심 개념 뽑기와 같고, 없는 개념은 404, 기다리는 동안 개념을 다시 뽑았으면 409, 형식이 맞지 않으면 502.")
	@PostMapping("/study-notes")
	public ApiResponse<MaterialConceptResponse> explain(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId, @Valid @RequestBody ConceptStudyNoteRequest request) {
		return ApiResponse.success(conceptService.explain(authUser.userId(), materialId, request.conceptId(),
				Boolean.TRUE.equals(request.materialContentConsent())));
	}
}
