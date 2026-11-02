package com.ailms.backend.domain.quiz.api;

import com.ailms.backend.domain.quiz.application.QuizService;
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
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 퀴즈만 다룬다. 남의 퀴즈 id로 요청하면 404
@Tag(name = "퀴즈")
@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
public class QuizController {

	private final QuizService quizService;

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

	@Operation(summary = "퀴즈 제출·채점", description = "서버가 채점해서 결과를 저장하고 돌려준다. 여러 번 낼 수 있다. "
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
}
