package com.msg.backend.domain.auth.application;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.auth.entity.RefreshToken;
import com.msg.backend.domain.user.entity.Role;
import com.msg.backend.global.security.SessionValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh Token 발급·회전·폐기.
 * - 원문은 256비트 난수라 추측할 수 없으므로 저장은 SHA-256 해시로 충분하다 (BCrypt는 매 요청 비용만 크다).
 * - 갱신할 때마다 새 토큰을 주고 이전 토큰은 폐기한다. 폐기된 토큰이 다시 오면 탈취로 보고 그 세션 전체를 폐기한다.
 * - 예외(#211): 탭 여러 개가 같은 쿠키로 거의 동시에 갱신하면 늦게 온 쪽은 방금 폐기된 토큰을 보낸다.
 *   회전으로 폐기된 지 유예 시간(app.auth.refresh-reuse-grace) 안이면 재사용으로 보지 않고 같은 세션에서 한 번 더 회전한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService implements SessionValidator {

	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int TOKEN_BYTES = 32;

	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthProperties authProperties;
	private final Clock clock;

	public record IssuedToken(String rawToken, Long userId, String sessionId) {
	}

	@Transactional
	public IssuedToken startSession(Long userId) {
		return issue(userId, UUID.randomUUID().toString(), now());
	}

	/**
	 * 토큰을 회전한다. 쓸 수 없는 토큰이면 빈 값을 돌려준다.
	 * 재사용 감지로 세션을 폐기한 결과가 커밋되도록 예외 대신 빈 값으로 알린다.
	 */
	@Transactional
	public Optional<IssuedToken> rotate(String rawToken) {
		Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken));
		if (found.isEmpty()) {
			return Optional.empty();
		}
		RefreshToken token = found.get();
		LocalDateTime now = now();
		if (token.isRevoked()) {
			if (isRotatedWithinGrace(token, now)) {
				// 먼저 회전한 쪽이 받은 새 토큰은 폐기하지 않는다. 브라우저 쿠키에는 두 응답 중 늦게 도착한 쪽이 남는데,
				// 어느 쪽이 남을지 알 수 없어서 둘 다 살려 둔다 (둘 다 같은 세션이라 로그아웃하면 함께 폐기된다)
				log.info("방금 회전된 Refresh Token으로 동시 갱신: userId={}, 같은 세션에서 다시 회전", token.getUserId());
				return Optional.of(issue(token.getUserId(), token.getSessionId(), now));
			}
			// 토큰 값은 로그에 남기지 않는다
			log.warn("폐기된 Refresh Token 재사용 감지: userId={}, 세션 전체 폐기", token.getUserId());
			refreshTokenRepository.revokeSession(token.getSessionId(), now);
			return Optional.empty();
		}
		if (token.isExpired(now)) {
			return Optional.empty();
		}
		token.revoke(now);
		return Optional.of(issue(token.getUserId(), token.getSessionId(), now));
	}

	/**
	 * 폐기된 토큰이 "회전으로" 폐기된 지 유예 시간이 안 지났는지 본다.
	 * 폐기 사유 칸은 따로 두지 않는다. 회전 말고 토큰을 폐기하는 경우(로그아웃, 재사용 감지)는 모두 세션 전체를 폐기하고,
	 * 탈퇴는 행을 지운다. 끝난 세션에는 새 토큰이 생기지 않으므로 "같은 세션에 아직 살아 있는 토큰이 있다"면
	 * 이 토큰은 회전으로 폐기된 것이다. 그래서 로그아웃한 세션의 토큰은 유예 시간 안이어도 받지 않는다.
	 */
	private boolean isRotatedWithinGrace(RefreshToken token, LocalDateTime now) {
		Duration grace = authProperties.refreshReuseGrace();
		if (grace.isZero() || token.isExpired(now) || !token.getRevokedAt().plus(grace).isAfter(now)) {
			return false;
		}
		return refreshTokenRepository.existsBySessionIdAndRevokedAtIsNullAndExpiresAtAfter(token.getSessionId(), now);
	}

	// 로그아웃: 이 토큰이 속한 세션의 토큰을 모두 폐기한다 (같은 세션의 Access Token도 함께 막힌다)
	@Transactional
	public void revokeSession(String rawToken) {
		refreshTokenRepository.findByTokenHash(hash(rawToken))
				.ifPresent(token -> refreshTokenRepository.revokeSession(token.getSessionId(), now()));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Role> findActiveRole(Long userId, String sessionId) {
		return refreshTokenRepository.findActiveRoles(userId, sessionId, now()).stream().findFirst();
	}

	private IssuedToken issue(Long userId, String sessionId, LocalDateTime now) {
		byte[] bytes = new byte[TOKEN_BYTES];
		RANDOM.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		LocalDateTime expiresAt = now.plus(authProperties.refreshTokenTtl());
		refreshTokenRepository.save(new RefreshToken(userId, sessionId, hash(rawToken), expiresAt, now));
		return new IssuedToken(rawToken, userId, sessionId);
	}

	private LocalDateTime now() {
		return LocalDateTime.now(clock);
	}

	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
