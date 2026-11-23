package com.msg.backend.global.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access Token 설정 (app.jwt).
 *
 * @param secret               Base64로 인코딩한 HMAC 키 (32바이트 이상). 환경변수 JWT_SECRET으로만 넣는다
 * @param allowEphemeralSecret secret이 비었을 때 임시 키를 만들어도 되는지 (H2 기본 프로필·테스트만 true)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, boolean allowEphemeralSecret, String issuer, Duration accessTokenTtl) {
}
