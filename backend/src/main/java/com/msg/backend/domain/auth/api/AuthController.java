package com.msg.backend.domain.auth.api;

import com.msg.backend.domain.auth.application.AuthService;
import com.msg.backend.domain.auth.application.AuthService.LoginResult;
import com.msg.backend.domain.auth.application.AuthService.RefreshResult;
import com.msg.backend.domain.auth.application.SignupBotCheck;
import com.msg.backend.domain.auth.dto.AccessTokenResponse;
import com.msg.backend.domain.auth.dto.LoginRequest;
import com.msg.backend.domain.auth.dto.LoginResponse;
import com.msg.backend.domain.auth.dto.SignupRequest;
import com.msg.backend.domain.user.dto.UserResponse;
import com.msg.backend.global.common.ApiResponse;
import com.msg.backend.global.error.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	// 쿠키를 쓰는 요청(로그인·갱신·로그아웃)은 이 헤더가 있어야 받는다.
	// 다른 사이트의 폼 전송은 이 헤더를 붙일 수 없고, 스크립트로 붙이면 CORS 사전 요청에서 막힌다 (CSRF 방어)
	static final String REQUESTED_WITH = "X-Requested-With";
	private static final String REQUESTED_WITH_VALUE = "XMLHttpRequest";

	private final AuthService authService;
	private final SignupBotCheck signupBotCheck;
	private final RefreshTokenCookies refreshTokenCookies;

	@Operation(summary = "회원가입", description = "이메일 중복이면 409. 비밀번호 규칙: 8자 이상, 영문·숫자·특수문자 포함.")
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
		// 외부 확인은 DB 트랜잭션 밖에서 먼저 한다
		signupBotCheck.verify(request.turnstileToken());
		return ApiResponse.success(authService.signup(request));
	}

	@Operation(summary = "로그인", description = "Access Token은 본문으로, Refresh Token은 HttpOnly 쿠키로 준다. "
			+ "실패가 반복되면 429.")
	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(
			@Parameter(description = "XMLHttpRequest", required = true) @RequestHeader(value = REQUESTED_WITH, required = false) String requestedWith,
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		requireXmlHttpRequest(requestedWith);
		// TODO(미결): 배포 구성이 정해지면 프록시 뒤의 실제 IP를 쓰도록 server.forward-headers-strategy를 설정한다
		LoginResult result = authService.login(request, httpRequest.getRemoteAddr());
		refreshTokenCookies.write(httpResponse, result.refreshToken());
		return ApiResponse.success(new LoginResponse(result.accessToken(), result.user()));
	}

	@Operation(summary = "Access Token 갱신", description = "Refresh Token 쿠키를 확인하고 새 Access Token과 새 Refresh Token(쿠키)을 준다. "
			+ "이미 쓴 Refresh Token을 다시 보내면 그 세션 전체가 폐기된다. "
			+ "다만 회전된 지 유예 시간(app.auth.refresh-reuse-grace, 기본 10초) 안이고 세션이 살아 있으면 탭 여러 개의 동시 갱신으로 보고 다시 발급한다.")
	@PostMapping("/refresh")
	public ApiResponse<AccessTokenResponse> refresh(
			@Parameter(description = "XMLHttpRequest", required = true) @RequestHeader(value = REQUESTED_WITH, required = false) String requestedWith,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		requireXmlHttpRequest(requestedWith);
		try {
			RefreshResult result = authService.refresh(refreshTokenCookies.read(httpRequest));
			refreshTokenCookies.write(httpResponse, result.refreshToken());
			return ApiResponse.success(new AccessTokenResponse(result.accessToken()));
		} catch (BusinessException e) {
			// 쓸 수 없는 쿠키는 지워서 다음 요청부터 보내지 않게 한다
			refreshTokenCookies.clear(httpResponse);
			throw e;
		}
	}

	@Operation(summary = "로그아웃", description = "현재 세션의 Refresh Token을 폐기하고 쿠키를 지운다. 같은 세션의 Access Token도 바로 쓸 수 없게 된다.")
	@PostMapping("/logout")
	public ApiResponse<Void> logout(
			@Parameter(description = "XMLHttpRequest", required = true) @RequestHeader(value = REQUESTED_WITH, required = false) String requestedWith,
			HttpServletRequest httpRequest,
			HttpServletResponse httpResponse) {
		requireXmlHttpRequest(requestedWith);
		authService.logout(refreshTokenCookies.read(httpRequest));
		refreshTokenCookies.clear(httpResponse);
		return ApiResponse.success("로그아웃했어요.", null);
	}

	static void requireXmlHttpRequest(String requestedWith) {
		if (!REQUESTED_WITH_VALUE.equals(requestedWith)) {
			throw new BusinessException(HttpStatus.FORBIDDEN, "허용되지 않은 요청이에요. 페이지를 새로고침한 뒤 다시 시도해 주세요.");
		}
	}
}
