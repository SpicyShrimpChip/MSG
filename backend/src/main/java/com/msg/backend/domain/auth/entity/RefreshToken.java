package com.msg.backend.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 로그인 세션의 Refresh Token 한 건. 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * 같은 로그인에서 회전하며 만들어진 토큰은 sessionId가 같다.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Column(nullable = false, length = 36)
	private String sessionId;

	@Column(nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	private LocalDateTime revokedAt;

	public RefreshToken(Long userId, String sessionId, String tokenHash, LocalDateTime expiresAt, LocalDateTime now) {
		this.userId = userId;
		this.sessionId = sessionId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = now;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(LocalDateTime now) {
		return !expiresAt.isAfter(now);
	}

	public void revoke(LocalDateTime now) {
		this.revokedAt = now;
	}
}
