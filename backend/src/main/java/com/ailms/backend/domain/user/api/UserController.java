package com.ailms.backend.domain.user.api;

import com.ailms.backend.domain.user.application.UserService;
import com.ailms.backend.domain.user.dto.AiConsentRequest;
import com.ailms.backend.domain.user.dto.UserResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "내 정보", description = "로그인한 사용자 본인의 정보만 돌려준다.")
	@GetMapping("/me")
	public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.success(userService.getMe(authUser.userId()));
	}

	@Operation(summary = "AI 대화 분석 동의 바꾸기", description = "로그인한 본인의 동의만 바꾼다. "
			+ "동의하면 대화 가져오기를 쓸 수 있다. 철회하면 가져온 대화와 가져오기 기록을 함께 지우고(되돌릴 수 없음), "
			+ "다시 동의하기 전까지 대화 가져오기는 403이다. 바뀐 내 정보를 돌려준다.")
	@PatchMapping("/me/consents")
	public ApiResponse<UserResponse> updateConsents(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody AiConsentRequest request) {
		return ApiResponse.success(userService.changeAiAnalysisConsent(authUser.userId(), request.aiAnalysis()));
	}
}
