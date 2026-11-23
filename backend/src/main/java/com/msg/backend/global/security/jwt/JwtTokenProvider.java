package com.msg.backend.global.security.jwt;

import com.msg.backend.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Access Token(JWT, HMAC-SHA) 발급·검증. 토큰에는 사용자 id(sub)와 로그인 세션 id(sid)만 담는다 (개인정보를 넣지 않는다).
 */
@Slf4j
@Component
public class JwtTokenProvider {

	private static final String SESSION_CLAIM = "sid";
	private static final int MIN_KEY_BYTES = 32;

	private final JwtProperties properties;
	private final Clock clock;
	private final SecretKey key;

	public JwtTokenProvider(JwtProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
		this.key = createKey(properties);
	}

	public String createAccessToken(Long userId, String sessionId) {
		Instant now = clock.instant();
		return Jwts.builder()
				.issuer(properties.issuer())
				.subject(String.valueOf(userId))
				.claim(SESSION_CLAIM, sessionId)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(properties.accessTokenTtl())))
				.signWith(key)
				.compact();
	}

	// 서명·발급자·만료가 모두 맞으면 주체를 돌려준다. 하나라도 틀리면 빈 값 (이유는 응답에 노출하지 않는다)
	public Optional<AuthUser> parse(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.requireIssuer(properties.issuer())
					.clock(() -> Date.from(clock.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			String sessionId = claims.get(SESSION_CLAIM, String.class);
			if (sessionId == null) {
				return Optional.empty();
			}
			return Optional.of(new AuthUser(Long.valueOf(claims.getSubject()), sessionId));
		} catch (JwtException | IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static SecretKey createKey(JwtProperties properties) {
		if (!StringUtils.hasText(properties.secret())) {
			if (!properties.allowEphemeralSecret()) {
				throw new IllegalStateException("JWT_SECRET 환경변수가 없습니다. backend/.env.example을 참고해 설정하세요.");
			}
			log.warn("JWT_SECRET이 없어 임시 키를 만들었습니다. 서버를 다시 띄우면 기존 로그인이 풀립니다 (H2 기본 프로필 전용).");
			return Jwts.SIG.HS256.key().build();
		}
		byte[] bytes;
		try {
			bytes = Decoders.BASE64.decode(properties.secret());
		} catch (RuntimeException e) {
			throw new IllegalStateException("JWT_SECRET은 Base64 문자열이어야 합니다.");
		}
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException("JWT_SECRET은 32바이트 이상이어야 합니다 (예: openssl rand -base64 48).");
		}
		return Keys.hmacShaKeyFor(bytes);
	}
}
