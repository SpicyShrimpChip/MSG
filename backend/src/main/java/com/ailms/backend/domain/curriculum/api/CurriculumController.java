package com.ailms.backend.domain.curriculum.api;

import com.ailms.backend.domain.curriculum.application.CurriculumService;
import com.ailms.backend.domain.curriculum.dto.CurriculumResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 과목만. 남의 과목은 404
@Tag(name = "맞춤 커리큘럼")
@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

	private final CurriculumService curriculumService;

	@Operation(summary = "과목의 맞춤 학습 순서", description = "과목 키워드(개념)마다 퀴즈 결과·대화 분석 다수 의견·내 피드백을 "
			+ "나란히 보고 코드 규칙으로 순서와 이유를 정한다(AI를 부르지 않는다). 채점이 끝나지 않은 답은 근거에서 뺀다. "
			+ "aiSuggestions는 AI가 퀴즈를 만들 때 제안한 개념이라 실제 결과와 구분한다. 남의 과목 404.")
	@GetMapping("/courses/{courseId}")
	public ApiResponse<CurriculumResponse> get(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long courseId) {
		return ApiResponse.success(curriculumService.get(authUser.userId(), courseId));
	}
}
