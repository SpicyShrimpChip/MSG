package com.msg.backend.domain.auth.api;

import com.msg.backend.domain.auth.application.FrontendProperties;
import com.msg.backend.domain.auth.application.KakaoProperties;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@Tag(name = "소셜 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SocialAuthController {

	private final KakaoProperties kakaoProperties;
	private final FrontendProperties frontendProperties;
	private final OAuthStateCookies stateCookies;

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
