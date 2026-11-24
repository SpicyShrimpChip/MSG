package com.msg.backend.domain.material.api;

import com.msg.backend.domain.material.application.MaterialSummaryService;
import com.msg.backend.domain.material.dto.StudySummaryRequest;
import com.msg.backend.domain.material.dto.StudySummaryResponse;
import com.msg.backend.global.common.ApiResponse;
import com.msg.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 학습자료 요약본. 로그인한 본인 자료만, 남의 자료는 404
@Tag(name = "학습자료")
@RestController
@RequestMapping("/api/materials/{materialId}/summary")
@RequiredArgsConstructor
public class MaterialSummaryController {

	private final MaterialSummaryService summaryService;

	@Operation(summary = "자료 요약본 보기", description = "학습 목적(purpose)별로 저장한 요약. 없으면 status=none. "
			+ "요약을 만든 뒤 파일이나 요약 지시문이 바뀌었으면 stale=true와 이유. 모델을 부르지 않는다.")
	@GetMapping
	public ApiResponse<StudySummaryResponse> get(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId, @RequestParam(defaultValue = "exam_prep") String purpose) {
		return ApiResponse.success(summaryService.get(authUser.userId(), materialId, purpose));
	}

	@Operation(summary = "자료 요약본 만들기", description = "자료 본문 중 서버가 고른 부분(12,000자까지)을 외부 AI로 보내 학습 목적에 맞는 요약을 만들고, "
			+ "항목마다 원문 인용을 서버가 확인한다(검증 완료·일부·실패·근거 부족, 자료에 없는 AI 보충 설명은 따로 표시). "
			+ "같은 파일·같은 목적의 요약이 있으면 모델을 부르지 않고 그대로 돌려준다(reused=true, regenerate로 새로 만들기). "
			+ "자료 본문 전송 기능이 꺼져 있거나 AI 퀴즈 처리 동의가 없으면 403, 확인이 없으면 400, 읽을 수 없는 자료·개인정보가 있는 본문 422, "
			+ "모델 없음 503, 한도 429, 진행 중 409, 확인된 항목이 없으면 502, 시간 초과 504.")
	@PostMapping
	public ApiResponse<StudySummaryResponse> generate(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId, @Valid @RequestBody StudySummaryRequest request) {
		return ApiResponse.success(summaryService.generate(authUser.userId(), materialId, request.purpose(),
				Boolean.TRUE.equals(request.materialContentConsent()), Boolean.TRUE.equals(request.regenerate())));
	}
}
