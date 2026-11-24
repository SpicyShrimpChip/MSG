package com.msg.backend.domain.auth.api;

import com.msg.backend.domain.auth.application.AuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

/**
 * 가입 마무리 표(티켓, JwtTokenProvider.createSocialSignupTicket) 쿠키. 카카오 콜백이 심고, 가입 마무리 화면의 pending·signup이 읽는다.
 * HttpOnly라 화면 스크립트는 읽지 못하고, Path=/api/auth/social이라 그 주소에만 실린다. 가입을 마치면 지운다.
 * SameSite=Lax: 카카오에서 돌아오는 리다이렉트 응답에서 심기 때문에 Strict 쿠키와 같은 제약을 피한다(쓰는 요청은 같은 사이트의 fetch).
 */
@Component
public class SocialSignupTicketCookies {

	static final String NAME = "msg_social_signup";
	private static final String PATH = "/api/auth/social";
	private static final Duration MAX_AGE = Duration.ofMinutes(10);

	private final boolean secure;

	public SocialSignupTicketCookies(AuthProperties authProperties) {
		this.secure = authProperties.refreshCookie().secure();
	}

	public String read(HttpServletRequest request) {
		Cookie cookie = WebUtils.getCookie(request, NAME);
		return cookie == null ? null : cookie.getValue();
	}

	public void write(HttpServletResponse response, String ticket) {
		add(response, ticket, MAX_AGE);
	}

	public void clear(HttpServletResponse response) {
		add(response, "", Duration.ZERO);
	}

	private void add(HttpServletResponse response, String value, Duration age) {
		ResponseCookie cookie = ResponseCookie.from(NAME, value)
				.httpOnly(true)
				.secure(secure)
				.sameSite("Lax")
				.path(PATH)
				.maxAge(age)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}
}
