package com.ailms.backend.domain.analysis.api;

import com.ailms.backend.domain.analysis.application.AnalysisQueryService;
import com.ailms.backend.domain.analysis.application.AnalysisService;
import com.ailms.backend.domain.analysis.application.FeedbackService;
import com.ailms.backend.domain.analysis.application.MasteryHistoryService;
import com.ailms.backend.domain.analysis.dto.AnalysisRunResponse;
import com.ailms.backend.domain.analysis.dto.FeedbackRequest;
import com.ailms.backend.domain.analysis.dto.FeedbackResponse;
import com.ailms.backend.domain.analysis.dto.ProviderStatusResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 과목·분석·피드백만 다룬다. 남의 과목·분석 id로 요청하면 404
@Tag(name = "학습 분석")
@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

	private final AnalysisService analysisService;
	private final AnalysisQueryService queryService;
	private final FeedbackService feedbackService;
	private final MasteryHistoryService masteryHistoryService;

	@Operation(summary = "AI 모델 연결 상태", description = "GPT·Claude·Gemini 각각 API 키와 모델 이름이 설정됐는지. 키 값은 내보내지 않는다.")
	@GetMapping("/providers")
	public ApiResponse<List<ProviderStatusResponse>> providers() {
		return ApiResponse.success(analysisService.providers());
	}

	@Operation(summary = "과목 대화 분석하기", description = "이 과목에 가져온 대화(가져오기 완료, 동의한 사용자)를 "
			+ "설정된 모델마다 같은 지시문으로 따로 분석하고 개념(과목 키워드)별로 의견을 비교한다. 모델을 기다리므로 오래 걸릴 수 있다. "
			+ "미동의 403, 남의 과목 404, 키워드·대화 없음 400, 이미 분석 중 409, 24시간 횟수 초과 429, 설정된 모델 없음 503.")
	@PostMapping("/courses/{courseId}/runs")
	public ResponseEntity<ApiResponse<AnalysisRunResponse>> run(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long courseId) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(masteryHistoryService.recordAndAttach(authUser.userId(),
						analysisService.run(authUser.userId(), courseId))));
	}

	@Operation(summary = "과목의 최근 분석 결과", description = "가장 최근에 끝난 분석. 없으면 404.")
	@GetMapping("/courses/{courseId}/runs/latest")
	public ApiResponse<AnalysisRunResponse> latest(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long courseId) {
		return ApiResponse.success(masteryHistoryService.recordAndAttach(authUser.userId(),
				queryService.getLatest(authUser.userId(), courseId)));
	}

	@Operation(summary = "분석 결과 하나")
	@GetMapping("/runs/{runId}")
	public ApiResponse<AnalysisRunResponse> getRun(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long runId) {
		return ApiResponse.success(masteryHistoryService.attachOnly(authUser.userId(),
				queryService.getRun(authUser.userId(), runId)));
	}

	@Operation(summary = "과목의 학습 피드백 목록")
	@GetMapping("/courses/{courseId}/feedback")
	public ApiResponse<List<FeedbackResponse>> getFeedback(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long courseId) {
		return ApiResponse.success(feedbackService.getFeedback(authUser.userId(), courseId));
	}

	@Operation(summary = "학습 피드백 남기기·바꾸기", description = "과목·개념마다 하나. 이미 있으면 바꾼다. "
			+ "AI 판단·숙련도는 바꾸지 않는다 (already_know → 확인 퀴즈 권함, want_to_study → 추천 순서 앞당김).")
	@PutMapping("/courses/{courseId}/feedback")
	public ApiResponse<FeedbackResponse> saveFeedback(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long courseId, @Valid @RequestBody FeedbackRequest request) {
		return ApiResponse.success(feedbackService.save(authUser.userId(), courseId, request));
	}

	@Operation(summary = "학습 피드백 취소")
	@DeleteMapping("/feedback/{id}")
	public ApiResponse<Void> deleteFeedback(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
		feedbackService.delete(authUser.userId(), id);
		return ApiResponse.success(null);
	}
}
