package com.msg.backend.global.security.jwt;

import com.msg.backend.domain.auth.application.SocialProfile;
import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.global.security.AuthUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Access Token(JWT, HMAC-SHA) 발급·검증. 토큰에는 사용자 id(sub)와 로그인 세션 id(sid)만 담는다 (개인정보를 넣지 않는다).
 * 소셜 가입 마무리 표(티켓)도 같은 키로 서명한다(aud=social-signup, 10분, HttpOnly 쿠키로만 오가고 화면 스크립트는 읽지 못한다).
 */
@Slf4j
@Component
public class JwtTokenProvider {

	private static final String SESSION_CLAIM = "sid";
	// 소셜 가입 마무리 표(티켓). Access Token과 같은 키로 서명하되 aud로 구분하고 sid가 없어 Access Token으로 쓸 수 없다
	private static final String SIGNUP_AUDIENCE = "social-signup";
	private static final Duration SIGNUP_TICKET_TTL = Duration.ofMinutes(10);
	private static final String PROVIDER_CLAIM = "prv";
	private static final String PROVIDER_USER_CLAIM = "pid";
	private static final String EMAIL_CLAIM = "email";
	private static final String NICKNAME_CLAIM = "nick";
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

	/**
	 * 카카오로 처음 온 사용자의 가입 마무리 표. DB에 쓰지 않고 이 서명 토큰(쿠키)에만 프로필을 담아 10분 동안 둔다.
	 * 확인된 이메일이 있는 프로필만 받는다.
	 */
	public String createSocialSignupTicket(SocialProfile profile) {
		if (profile.email() == null || !profile.emailVerified()) {
			throw new IllegalArgumentException("확인된 이메일이 있는 프로필만 가입 마무리 표로 만든다");
		}
		Instant now = clock.instant();
		var builder = Jwts.builder()
				.issuer(properties.issuer())
				.audience().add(SIGNUP_AUDIENCE).and()
				.claim(PROVIDER_CLAIM, profile.provider().name())
				.claim(PROVIDER_USER_CLAIM, profile.providerUserId())
				.claim(EMAIL_CLAIM, profile.email())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(SIGNUP_TICKET_TTL)));
		if (profile.nickname() != null) {
			builder.claim(NICKNAME_CLAIM, profile.nickname());
		}
		return builder.signWith(key).compact();
	}

	// 서명·발급자·용도(aud)·만료가 모두 맞으면 프로필을 돌려준다. Access Token은 aud가 달라 읽지 못한다
	public Optional<SocialProfile> parseSocialSignupTicket(String token) {
		if (!StringUtils.hasText(token)) {
			return Optional.empty();
		}
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.requireIssuer(properties.issuer())
					.requireAudience(SIGNUP_AUDIENCE)
					.clock(() -> Date.from(clock.instant()))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			String providerUserId = claims.get(PROVIDER_USER_CLAIM, String.class);
			String email = claims.get(EMAIL_CLAIM, String.class);
			if (!StringUtils.hasText(providerUserId) || !StringUtils.hasText(email)) {
				return Optional.empty();
			}
			SocialProvider provider = SocialProvider.valueOf(claims.get(PROVIDER_CLAIM, String.class));
			return Optional.of(new SocialProfile(provider, providerUserId, email, true,
					claims.get(NICKNAME_CLAIM, String.class)));
		} catch (JwtException | IllegalArgumentException | NullPointerException e) {
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
