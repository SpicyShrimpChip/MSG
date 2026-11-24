package com.msg.backend.domain.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 사용자가 보는 화면 주소 (app.frontend). 소셜 로그인 콜백이 끝나면 이 주소의 화면으로 돌려보낸다.
 * 운영은 Vercel 주소(FRONTEND_BASE_URL), 로컬은 Vite 개발 서버.
 *
 * @param baseUrl 끝의 /는 뗀다
 */
@ConfigurationProperties(prefix = "app.frontend")
public record FrontendProperties(String baseUrl) {

	public FrontendProperties {
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "http://localhost:5173";
		}
		baseUrl = baseUrl.strip();
		while (baseUrl.endsWith("/")) {
			baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
		}
	}
}
