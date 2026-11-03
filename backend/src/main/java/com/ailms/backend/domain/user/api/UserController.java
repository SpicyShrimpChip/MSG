package com.ailms.backend.domain.user.api;

import com.ailms.backend.domain.user.application.UserService;
import com.ailms.backend.domain.auth.api.RefreshTokenCookies;
import com.ailms.backend.domain.user.application.WithdrawalService;
import com.ailms.backend.domain.user.dto.AiConsentRequest;
import com.ailms.backend.domain.user.dto.WithdrawRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.ailms.backend.domain.user.dto.LearningProfileRequest;
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
	private final WithdrawalService withdrawalService;
	private final RefreshTokenCookies refreshTokenCookies;

	@Operation(summary = "내 정보", description = "로그인한 사용자 본인의 정보만 돌려준다.")
	@GetMapping("/me")
	public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.success(userService.getMe(authUser.userId()));
	}

	@Operation(summary = "AI 대화 분석·AI 퀴즈 처리 동의 바꾸기", description = "로그인한 본인의 동의만, 보낸 항목만 바꾼다. "
			+ "aiQuiz: AI 퀴즈 처리 동의(퀴즈 만들기·주관식·서술형 AI 채점). 철회해도 만든 퀴즈·결과는 남는다. "
			+ "동의하면 대화 가져오기를 쓸 수 있다. 철회하면 가져온 대화와 가져오기 기록을 함께 지우고(되돌릴 수 없음), "
			+ "다시 동의하기 전까지 대화 가져오기는 403이다. 바뀐 내 정보를 돌려준다.")
	@PatchMapping("/me/consents")
	public ApiResponse<UserResponse> updateConsents(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody AiConsentRequest request) {
		return ApiResponse.success(userService.changeConsents(authUser.userId(), request.aiAnalysis(), request.aiQuiz()));
	}

	@Operation(summary = "회원 탈퇴", description = "로그인한 본인만. 지금 비밀번호를 다시 확인한 뒤 계정과 내 모든 학습 데이터"
			+ "(과목·자료와 원본 파일·가져온 대화·분석 결과·피드백·퀴즈와 채점 기록·AI 호출 기록)를 지운다. 되돌릴 수 없다. "
			+ "로그인 유지 쿠키도 지운다. 비밀번호가 틀리면 400(로그인과 같은 시도 제한 429), 마지막 관리자 409.")
	@DeleteMapping("/me")
	public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody WithdrawRequest request, HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		withdrawalService.withdraw(authUser.userId(), request.password(), httpRequest.getRemoteAddr());
		refreshTokenCookies.clear(httpResponse);
		return ApiResponse.success("탈퇴했어요. 그동안 이용해 주셔서 고마워요.", null);
	}

	@Operation(summary = "학습 목적·관심 직무 바꾸기", description = "로그인한 본인 것만 바꾼다. 셋을 한 번에 바꾸고 바뀐 내 정보를 돌려준다. "
			+ "직무가 other면 jobFieldOther(30자까지)가 필요하다. 모르는 값은 400.")
	@PatchMapping("/me/profile")
	public ApiResponse<UserResponse> updateProfile(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody LearningProfileRequest request) {
		return ApiResponse.success(userService.changeLearningProfile(authUser.userId(), request));
	}
}
