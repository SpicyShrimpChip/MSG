package com.msg.backend.domain.auth.api;

import com.msg.backend.domain.auth.application.FrontendProperties;
import com.msg.backend.domain.auth.application.KakaoClient;
import com.msg.backend.domain.auth.application.KakaoProperties;
import com.msg.backend.domain.auth.application.SocialError;
import com.msg.backend.domain.auth.application.SocialLoginException;
import com.msg.backend.domain.auth.application.SocialLoginService;
import com.msg.backend.domain.auth.application.SocialLoginService.LoggedIn;
import com.msg.backend.domain.auth.application.SocialLoginService.NeedsSignup;
import com.msg.backend.domain.auth.application.SocialLoginService.SocialLoginOutcome;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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

	private final KakaoProperties kakaoProperties;
	private final FrontendProperties frontendProperties;
	private final OAuthStateCookies stateCookies;
	private final SocialSignupTicketCookies signupTicketCookies;
	private final RefreshTokenCookies refreshTokenCookies;
	private final KakaoClient kakaoClient;
	private final SocialLoginService socialLoginService;

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
			SocialLoginOutcome outcome = socialLoginService.handle(kakaoClient.fetchProfile(code));
			String encodedReturnTo = URLEncoder.encode(returnTo.get(), StandardCharsets.UTF_8);
			if (outcome instanceof LoggedIn loggedIn) {
				refreshTokenCookies.write(response, loggedIn.refreshToken());
				signupTicketCookies.clear(response);
				redirect(response, frontendProperties.baseUrl() + "/auth/social/done?returnTo=" + encodedReturnTo);
			} else if (outcome instanceof NeedsSignup needsSignup) {
				signupTicketCookies.write(response, needsSignup.ticket());
				redirect(response, frontendProperties.baseUrl() + "/signup/social?returnTo=" + encodedReturnTo);
			}
		} catch (SocialLoginException e) {
			redirect(response, loginWithError(e.error().code()));
		} catch (RuntimeException e) {
			log.error("카카오 로그인 콜백 처리 실패", e);
			redirect(response, loginWithError(SocialError.FAILED.code()));
		}
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
