package com.ailms.backend.domain.quiz.api;

import com.ailms.backend.domain.quiz.application.QuizGenerationService;
import com.ailms.backend.domain.quiz.application.QuizService;
import com.ailms.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.ailms.backend.domain.quiz.dto.GenerationStatusResponse;
import com.ailms.backend.domain.quiz.dto.QuizCreateRequest;
import com.ailms.backend.domain.quiz.dto.QuizResponse;
import com.ailms.backend.domain.quiz.dto.QuizResultResponse;
import com.ailms.backend.domain.quiz.dto.QuizSubmitRequest;
import com.ailms.backend.domain.quiz.dto.QuizSummaryResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 퀴즈만 다룬다. 남의 퀴즈 id로 요청하면 404
@Tag(name = "퀴즈")
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

	private final QuizService quizService;
	private final QuizGenerationService generationService;

	@Operation(summary = "내 퀴즈 목록", description = "최근에 만든 퀴즈부터. lastCorrectCount는 가장 최근 제출 기준이고 안 풀었으면 null.")
	@GetMapping
	public ApiResponse<List<QuizSummaryResponse>> getQuizzes(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.success(quizService.getQuizzes(authUser.userId()));
	}

	@Operation(summary = "퀴즈 풀기 화면", description = "문제와 보기만 돌려준다. 정답 번호와 해설은 제출 뒤 결과에만 있다.")
	@GetMapping("/{id}")
	public ApiResponse<QuizResponse> getQuiz(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
		return ApiResponse.success(quizService.getQuiz(authUser.userId(), id));
	}

	@Operation(summary = "퀴즈 제출·채점", description = "서버가 채점해서 결과를 저장하고 돌려준다. 여러 번 낼 수 있다. 객관식은 choiceIndex, 주관식·서술형은 text. "
			+ "주관식은 허용 답 일치 후 필요하면 AI, 서술형은 AI가 기준별 수준을 고르고 점수는 서버가 계산한다. AI 채점을 못 하면 pending. "
			+ "AI로 보낼 답에 개인정보 패턴이 있으면 422(data.questionIds). "
			+ "빠진 문제는 틀림(-1)으로 치고, 이 퀴즈에 없는 문제·같은 문제를 두 번 보낸 답·범위 밖 보기 번호는 400.")
	@PostMapping("/{id}/submissions")
	public ResponseEntity<ApiResponse<QuizResultResponse>> submit(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long id, @Valid @RequestBody QuizSubmitRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(quizService.submit(authUser.userId(), id, request)));
	}

	@Operation(summary = "가장 최근 채점 결과", description = "아직 안 풀었으면 data가 null.")
	@GetMapping("/{id}/submissions/latest")
	public ApiResponse<QuizResultResponse> getLatestResult(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long id) {
		return ApiResponse.success(quizService.getLatestResult(authUser.userId(), id));
	}

	@Operation(summary = "AI로 퀴즈 만들기", description = "학습 모드(exam_prep·self_development·work)·문제 유형·수·난이도·개념으로 "
			+ "설정된 AI 모델 하나가 문제를 만들고, 서버가 구조·문항 수·정답·채점 기준을 검증한 뒤 저장한다. 모델을 기다리므로 오래 걸릴 수 있다. "
			+ "외부로는 과목 이름·개념·시험 범위·직무 분야만 보내고 대화·자료 본문은 보내지 않는다. "
			+ "같은 requestKey로 다시 보내면 이미 만든 퀴즈를 200으로 돌려준다. "
			+ "AI 퀴즈 처리 미동의 403, 남의 과목·자료 404, 잘못된 값 400, 진행 중·실패한 키 409, 24시간 한도 429, "
			+ "모델 응답 실패·형식 오류 502(저장하지 않음), 전체 시간 예산 초과 504(저장하지 않음), 설정된 모델 없음 503. "
			+ "24시간 실제 모델 요청 수 상한도 429.")
	@PostMapping
	public ResponseEntity<ApiResponse<QuizResponse>> create(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody QuizCreateRequest request) {
		QuizGenerationService.Created created = generationService.create(authUser.userId(), request);
		return ResponseEntity.status(created.reused() ? HttpStatus.OK : HttpStatus.CREATED)
				.body(ApiResponse.success(created.quiz()));
	}

	@Operation(summary = "채점 대기 답 다시 채점", description = "AI 채점을 기다리는(pending) 답만 다시 채점하고 결과를 돌려준다. 처음 채점과 같은 24시간 한도를 쓴다. "
			+ "같은 제출을 채점하는 중 409, 한도 초과 429(답·결과는 그대로), AI 퀴즈 처리 미동의 403, 모델 없음 503, 남의 퀴즈·제출 404.")
	@PostMapping("/{id}/submissions/{submissionId}/grading")
	public ApiResponse<QuizResultResponse> regrade(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
			@PathVariable Long submissionId) {
		return ApiResponse.success(quizService.regrade(authUser.userId(), id, submissionId));
	}

	@Operation(summary = "과목의 개념별 퀴즈 결과", description = "퀴즈마다 가장 최근 제출로 개념별 문항 수·점수를 센다(코드 집계, 숙련도 점수 아님). "
			+ "이미 알아요를 남긴 개념은 그 뒤에 푼 문제로 확인했는지(confirmation)를 함께 준다. 남의 과목 404.")
	@GetMapping("/concept-stats")
	public ApiResponse<List<ConceptQuizStatsResponse>> conceptStats(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam Long courseId) {
		return ApiResponse.success(quizService.conceptStats(authUser.userId(), courseId));
	}

	@Operation(summary = "퀴즈 만들기 요청 상태", description = "만들기를 보낸 뒤 화면을 새로 고치거나 다른 화면에 다녀왔을 때 "
			+ "같은 requestKey의 결과를 찾는다. running이면 아직 만드는 중(deadlineAt까지), succeeded면 quizId로 이동, "
			+ "failed·timed_out이면 message를 보여 주고 새 키로 다시 만든다. 남의 요청·없는 키는 404.")
	@GetMapping("/generations/{requestKey}")
	public ApiResponse<GenerationStatusResponse> generationStatus(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String requestKey) {
		return ApiResponse.success(generationService.status(authUser.userId(), requestKey));
	}
}
