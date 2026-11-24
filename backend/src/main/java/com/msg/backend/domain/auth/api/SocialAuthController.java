package com.msg.backend.domain.auth.api;

import com.msg.backend.domain.auth.application.AuthService.LoginResult;
import com.msg.backend.domain.auth.application.FrontendProperties;
import com.msg.backend.domain.auth.application.KakaoClient;
import com.msg.backend.domain.auth.application.KakaoProperties;
import com.msg.backend.domain.auth.application.SignupAttemptLimiter;
import com.msg.backend.domain.auth.application.SocialError;
import com.msg.backend.domain.auth.application.SocialLoginException;
import com.msg.backend.domain.auth.application.SocialLoginService;
import com.msg.backend.domain.auth.application.SocialLoginService.LoggedIn;
import com.msg.backend.domain.auth.application.SocialLoginService.NeedsLink;
import com.msg.backend.domain.auth.application.SocialLoginService.NeedsSignup;
import com.msg.backend.domain.auth.application.SocialLoginService.SocialLoginOutcome;
import com.msg.backend.domain.auth.application.SocialProfile;
import com.msg.backend.domain.auth.dto.LoginResponse;
import com.msg.backend.domain.auth.dto.PendingSocialSignupResponse;
import com.msg.backend.domain.auth.dto.SocialLinkRequest;
import com.msg.backend.domain.auth.dto.SocialSignupRequest;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.global.common.ApiResponse;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.security.jwt.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 소셜 로그인(카카오). 브라우저가 이 주소들로 직접 이동하고(axios 아님), 끝나면 화면 주소(app.frontend.base-url)로 돌려보낸다.
 * 흐름: /api/auth/kakao/start → 카카오 로그인·동의 → /api/auth/kakao/callback → 화면.
 */
@Slf4j
@Tag(name = "소셜 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SocialAuthController {

	static final String TICKET_EXPIRED_MESSAGE = "가입 정보가 만료됐어요. 카카오 로그인을 다시 해 주세요.";

	private final KakaoProperties kakaoProperties;
	private final FrontendProperties frontendProperties;
	private final OAuthStateCookies stateCookies;
	private final SocialSignupTicketCookies signupTicketCookies;
	private final SignupAttemptLimiter signupAttemptLimiter;
	private final RefreshTokenCookies refreshTokenCookies;
	private final KakaoClient kakaoClient;
	private final SocialLoginService socialLoginService;
	private final JwtTokenProvider tokenProvider;

	@Operation(summary = "쓸 수 있는 소셜 로그인", description = "서버에 카카오 키가 있으면 [\"kakao\"], 없으면 []. 화면은 목록에 있는 버튼만 보여 준다.")
	@GetMapping("/social/providers")
	public ApiResponse<List<String>> providers() {
		return ApiResponse.success(kakaoProperties.enabled() ? List.of(SocialProvider.KAKAO.apiValue()) : List.of());
	}

	@Operation(summary = "카카오 로그인 시작", description = "브라우저가 직접 이동하는 주소. 카카오 인가 화면으로 302 이동하고 state 확인 쿠키를 심는다. "
			+ "카카오 키가 없으면 /login?socialError=unavailable 로 보낸다.")
	@GetMapping("/kakao/start")
	public void kakaoStart(
			@Parameter(description = "로그인 뒤 돌아갈 화면 경로 (/로 시작하는 사이트 안 경로만, 아니면 /)") @RequestParam(required = false) String returnTo,
			HttpServletResponse response) {
		if (!kakaoProperties.enabled()) {
			redirect(response, loginWithError("unavailable"));
			return;
		}
		String state = stateCookies.issue(response, returnTo);
		String authorizeUrl = UriComponentsBuilder.fromUriString(kakaoProperties.authBaseUrl() + "/oauth/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", kakaoProperties.clientId())
				.queryParam("redirect_uri", kakaoProperties.redirectUri())
				.queryParam("state", state)
				.encode()
				.build()
				.toUriString();
		redirect(response, authorizeUrl);
	}

	@Operation(summary = "카카오 로그인 콜백", description = "카카오가 브라우저를 돌려보내는 주소(Redirect URI). 결과에 따라 화면으로 302 이동한다: "
			+ "로그인되면 Refresh 쿠키를 심고 /auth/social/done?returnTo=, 처음 온 사용자는 가입 마무리 쿠키를 심고 /signup/social?returnTo=, "
			+ "같은 이메일의 비밀번호 계정이 있으면 같은 쿠키를 심고 /auth/social/link?returnTo=(비밀번호 확인 뒤 연결), "
			+ "실패하면 /login?socialError=코드(state, cancelled, email_required, email_unverified, rejoin_restricted, link_conflict, failed, unavailable).")
	@GetMapping("/kakao/callback")
	public void kakaoCallback(
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state,
			@RequestParam(required = false) String error,
			HttpServletRequest request,
			HttpServletResponse response) {
		// state 쿠키는 결과와 상관없이 여기서 지운다. 맞지 않으면 카카오를 부르지 않는다(로그인 CSRF 방지)
		Optional<String> returnTo = stateCookies.consume(request, response, state);
		if (returnTo.isEmpty()) {
			redirect(response, loginWithError(SocialError.STATE.code()));
			return;
		}
		if (!kakaoProperties.enabled()) {
			redirect(response, loginWithError(SocialError.UNAVAILABLE.code()));
			return;
		}
		if (error != null) {
			SocialError reason = "access_denied".equals(error) ? SocialError.CANCELLED : SocialError.FAILED;
			redirect(response, loginWithError(reason.code()));
			return;
		}
		if (code == null || code.isBlank()) {
			redirect(response, loginWithError(SocialError.FAILED.code()));
			return;
		}
		try {
			// 카카오 호출은 DB 트랜잭션 밖에서 먼저 한다
			SocialLoginOutcome outcome = handleOrUnlink(kakaoClient.fetchProfile(code));
			String encodedReturnTo = URLEncoder.encode(returnTo.get(), StandardCharsets.UTF_8);
			if (outcome instanceof LoggedIn loggedIn) {
				refreshTokenCookies.write(response, loggedIn.refreshToken());
				signupTicketCookies.clear(response);
				redirect(response, frontendProperties.baseUrl() + "/auth/social/done?returnTo=" + encodedReturnTo);
			} else if (outcome instanceof NeedsSignup needsSignup) {
				signupTicketCookies.write(response, needsSignup.ticket());
				redirect(response, frontendProperties.baseUrl() + "/signup/social?returnTo=" + encodedReturnTo);
			} else if (outcome instanceof NeedsLink needsLink) {
				signupTicketCookies.write(response, needsLink.ticket());
				redirect(response, frontendProperties.baseUrl() + "/auth/social/link?returnTo=" + encodedReturnTo);
			}
		} catch (SocialLoginException e) {
			redirect(response, loginWithError(e.error().code()));
		} catch (DataAccessException e) {
			// DB 오류 메시지에는 넣으려던 값이 들어갈 수 있어 종류만 남긴다(GlobalExceptionHandler와 같음)
			log.error("카카오 로그인 콜백 처리 실패 ({})", e.getClass().getSimpleName());
			redirect(response, loginWithError(SocialError.FAILED.code()));
		} catch (RuntimeException e) {
			log.error("카카오 로그인 콜백 처리 실패", e);
			redirect(response, loginWithError(SocialError.FAILED.code()));
		}
	}

	@Operation(summary = "가입 마무리 정보", description = "카카오에서 처음 온 사용자의 가입 마무리 화면이 부른다. 가입 마무리 쿠키(10분)가 없거나 만료·위조면 401.")
	@GetMapping("/social/pending")
	public ApiResponse<PendingSocialSignupResponse> pending(HttpServletRequest request) {
		SocialProfile profile = readTicket(request);
		return ApiResponse.success(new PendingSocialSignupResponse(profile.provider().apiValue(), profile.email(),
				profile.nickname()));
	}

	@Operation(summary = "카카오 가입 마무리", description = "약관 동의를 받아 계정을 만들고 바로 로그인한다(응답은 로그인과 같음, Refresh는 쿠키). "
			+ "가입 마무리 쿠키가 없거나 만료면 401, 같은 이메일이 이미 있거나 이미 가입을 마쳤으면 409, 탈퇴 뒤 30일 안이면 409, "
			+ "같은 IP에서 가입 시도가 너무 많으면 429(이메일 가입과 함께 센다).")
	@PostMapping("/social/signup")
	public ApiResponse<LoginResponse> socialSignup(
			@Parameter(description = "XMLHttpRequest", required = true) @RequestHeader(value = AuthController.REQUESTED_WITH, required = false) String requestedWith,
			@Valid @RequestBody SocialSignupRequest body,
			HttpServletRequest request,
			HttpServletResponse response) {
		AuthController.requireXmlHttpRequest(requestedWith);
		SocialProfile profile = readTicket(request);
		signupAttemptLimiter.acquire(request.getRemoteAddr());
		LoginResult result;
		try {
			result = socialLoginService.completeSignup(profile, body);
		} catch (BusinessException e) {
			// 이미 가입했거나 막힌 표는 더 쓸 수 없으니 지운다
			if (e.getStatus() == HttpStatus.CONFLICT) {
				signupTicketCookies.clear(response);
			}
			throw e;
		}
		signupTicketCookies.clear(response);
		refreshTokenCookies.write(response, result.refreshToken());
		return ApiResponse.success(new LoginResponse(result.accessToken(), result.user()));
	}

	@Operation(summary = "카카오를 기존 계정에 연결", description = "같은 이메일의 비밀번호 계정이 있어 연결 확인 화면으로 온 경우. "
			+ "그 계정의 비밀번호를 확인하고 카카오를 연결한 뒤 로그인한다(응답은 로그인과 같음). 비밀번호가 틀리면 로그인과 같은 401·429, "
			+ "표(쿠키)가 없거나 만료면 401, 그새 다른 카카오가 연결됐거나 계정이 없으면 409.")
	@PostMapping("/social/link")
	public ApiResponse<LoginResponse> socialLink(
			@Parameter(description = "XMLHttpRequest", required = true) @RequestHeader(value = AuthController.REQUESTED_WITH, required = false) String requestedWith,
			@Valid @RequestBody SocialLinkRequest body,
			HttpServletRequest request,
			HttpServletResponse response) {
		AuthController.requireXmlHttpRequest(requestedWith);
		SocialProfile profile = readTicket(request);
		LoginResult result;
		try {
			result = socialLoginService.linkWithPassword(profile, body.password(), request.getRemoteAddr());
		} catch (BusinessException e) {
			// 비밀번호가 틀리면 다시 시도할 수 있게 표를 남기고, 더 쓸 수 없는 표(409)만 지운다
			if (e.getStatus() == HttpStatus.CONFLICT) {
				signupTicketCookies.clear(response);
			}
			throw e;
		}
		signupTicketCookies.clear(response);
		refreshTokenCookies.write(response, result.refreshToken());
		return ApiResponse.success(new LoginResponse(result.accessToken(), result.user()));
	}

	/**
	 * 카카오 동의 화면에서 동의하는 순간 카카오 쪽 연결이 생긴다. 그 뒤 여기서 가입을 거절하면(이메일 없음·확인 안 된 이메일·
	 * 같은 이메일 계정 충돌·재가입 제한) MSG 계정 없이 카카오 "연결된 서비스"에만 남으므로 연결을 끊는다.
	 * handle이 거절하는 경우는 모두 이 회원번호가 어떤 계정에도 연결되지 않았을 때다(연결돼 있으면 먼저 로그인한다).
	 * 끊기가 실패해도 안내는 그대로 두고 로그만 남긴다(KakaoClient가 남김). 가입 마무리 화면에서 그냥 나간 경우는
	 * 서버에 남는 것이 없어 끊지 않는다(다음 카카오 로그인에서 가입 마무리를 이어서 할 수 있다)
	 */
	private SocialLoginOutcome handleOrUnlink(SocialProfile profile) {
		try {
			return socialLoginService.handle(profile);
		} catch (SocialLoginException rejected) {
			try {
				kakaoClient.unlink(profile.providerUserId());
			} catch (SocialLoginException ignored) {
				// 거절 안내가 우선이다. 남은 연결은 사용자가 카카오 계정 설정에서 끊을 수 있다
			}
			throw rejected;
		}
	}

	private SocialProfile readTicket(HttpServletRequest request) {
		return tokenProvider.parseSocialSignupTicket(signupTicketCookies.read(request))
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, TICKET_EXPIRED_MESSAGE));
	}

	private String loginWithError(String code) {
		return frontendProperties.baseUrl() + "/login?socialError=" + code;
	}

	private static void redirect(HttpServletResponse response, String location) {
		response.setStatus(HttpStatus.FOUND.value());
		response.setHeader(HttpHeaders.LOCATION, location);
		response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
		response.setHeader("Referrer-Policy", "no-referrer");
	}
}
