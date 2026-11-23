package com.msg.backend.global.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 허용 출처 (app.cors.allowed-origins). 로컬은 http://localhost:5173, 운영은 환경변수로 받는다.
 * 개발 중(Vite 프록시)과 배포(Vercel rewrite)는 같은 출처라 보통은 쓰이지 않는다.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}
}
