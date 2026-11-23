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
