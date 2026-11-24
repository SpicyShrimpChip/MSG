package com.msg.backend.domain.auth.entity;

import com.msg.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자와 소셜 계정(카카오 회원번호)의 연결 한 건. 제공사의 이메일·닉네임은 저장하지 않는다.
 * 사용자를 지우면 DB의 ON DELETE CASCADE로 함께 지워진다.
 */
@Entity
@Table(name = "user_social_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SocialProvider provider;

	@Column(nullable = false, length = 64)
	private String providerUserId;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	public static UserSocialAccount link(User user, SocialProvider provider, String providerUserId,
			LocalDateTime now) {
		UserSocialAccount account = new UserSocialAccount();
		account.userId = user.getId();
		account.provider = provider;
		account.providerUserId = providerUserId;
		account.createdAt = now;
		return account;
	}
}
