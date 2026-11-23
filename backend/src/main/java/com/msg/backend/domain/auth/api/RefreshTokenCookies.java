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
 * Refresh Token 쿠키 읽기·쓰기. HttpOnly(스크립트에서 못 읽음), SameSite, Path(/api/auth), Secure(운영)를 설정값으로 붙인다.
 */
@Component
public class RefreshTokenCookies {

	private final AuthProperties.RefreshCookie cookie;
	private final Duration maxAge;

	public RefreshTokenCookies(AuthProperties authProperties) {
		this.cookie = authProperties.refreshCookie();
		this.maxAge = authProperties.refreshTokenTtl();
	}

	public String read(HttpServletRequest request) {
		Cookie found = WebUtils.getCookie(request, cookie.name());
		return found == null ? null : found.getValue();
	}

	public void write(HttpServletResponse response, String refreshToken) {
		add(response, refreshToken, maxAge);
	}

	public void clear(HttpServletResponse response) {
		add(response, "", Duration.ZERO);
	}

	private void add(HttpServletResponse response, String value, Duration age) {
		ResponseCookie responseCookie = ResponseCookie.from(cookie.name(), value)
				.httpOnly(true)
				.secure(cookie.secure())
				.sameSite(cookie.sameSite())
				.path(cookie.path())
				.maxAge(age)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, responseCookie.toString());
	}
}
