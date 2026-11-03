package com.ailms.backend.domain.quiz.api;

import com.ailms.backend.domain.quiz.application.AssessmentService;
import com.ailms.backend.domain.quiz.application.QuizGenerationService;
import com.ailms.backend.domain.quiz.dto.AssessmentComparisonResponse;
import com.ailms.backend.domain.quiz.dto.AssessmentCreateRequest;
import com.ailms.backend.domain.quiz.dto.QuizAiStatusResponse;
import com.ailms.backend.domain.quiz.dto.QuizResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 학습자료 기반 사전·사후평가. 로그인한 본인 자료·과목만, 남의 것은 404. 풀기·제출·결과는 /api/quizzes/{id}를 그대로 쓴다
@Tag(name = "사전·사후평가")
@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {

	private final AssessmentService assessmentService;

	@Operation(summary = "사전·사후평가 만들기", description = "학습자료(과목에 연결된 것)로 평가를 만든다. 퀴즈 만들기와 같은 동의(AI 퀴즈 처리)·"
			+ "한도·시간 예산·검증을 쓴다. 사후평가는 같은 자료의 최신 사전평가와 개념·유형·문항 수·난이도·모드를 서버가 맞추고, "
			+ "사전평가가 없으면 409. useMaterialContent면 자료 본문(TXT·MD·PPTX, 12,000자까지)을 보내고, 못 쓰면 "
			+ "materialContentNote에 이유를 준다(PDF는 NOT_SUPPORTED). 같은 requestKey면 200으로 같은 평가.")
	@PostMapping
	public ResponseEntity<ApiResponse<QuizResponse>> create(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody AssessmentCreateRequest request) {
		QuizGenerationService.Created created = assessmentService.create(authUser.userId(), request);
		return ResponseEntity.status(created.reused() ? HttpStatus.OK : HttpStatus.CREATED)
				.body(ApiResponse.success(created.quiz()));
	}

	@Operation(summary = "사전평가 vs 사후평가 비교", description = "courseId(대시보드) 또는 materialId(자료별) 중 하나. "
			+ "최신 사전평가(제출한 것 우선)와 그 사전평가에 이어 만든 최신 사후평가를 각각 가장 최근 제출로 비교한다. "
			+ "채점이 끝난 답만 점수에 넣는다. 평가가 없으면 pre·post가 null.")
	@GetMapping("/comparison")
	public ApiResponse<AssessmentComparisonResponse> compare(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) Long courseId, @RequestParam(required = false) Long materialId) {
		return ApiResponse.success(assessmentService.compare(authUser.userId(), courseId, materialId));
	}

	@Operation(summary = "퀴즈·평가에 쓸 AI 상태", description = "문제 만들기는 설정 순서(gpt → claude → gemini)의 첫 모델 하나, "
			+ "AI 채점은 설정된 모델 모두. 하나도 없으면 available=false(만들기 503). 키 값은 내보내지 않는다.")
	@GetMapping("/ai-status")
	public ApiResponse<QuizAiStatusResponse> aiStatus() {
		return ApiResponse.success(assessmentService.aiStatus());
	}
}
