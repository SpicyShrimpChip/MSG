package com.ailms.backend.domain.user.entity;

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

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 254)
	private String email;

	// BCrypt 해시. 응답 DTO로 내보내지 않는다
	@Column(nullable = false, length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 30)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Role role;

	@Column(nullable = false)
	private boolean aiAnalysisConsent;

	private LocalDateTime aiAnalysisConsentedAt;

	@Column(nullable = false)
	private LocalDateTime termsAgreedAt;

	@Column(nullable = false)
	private LocalDateTime privacyAgreedAt;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@Column(nullable = false)
	private LocalDateTime updatedAt;

	// 가입 시점에 필수 동의(이용약관·개인정보)는 이미 확인된 상태로 만든다
	public static User signUp(String email, String passwordHash, String name, boolean aiAnalysisConsent,
			LocalDateTime now) {
		User user = new User();
		user.email = email;
		user.passwordHash = passwordHash;
		user.name = name;
		user.role = Role.USER;
		user.aiAnalysisConsent = aiAnalysisConsent;
		user.aiAnalysisConsentedAt = aiAnalysisConsent ? now : null;
		user.termsAgreedAt = now;
		user.privacyAgreedAt = now;
		user.createdAt = now;
		user.updatedAt = now;
		return user;
	}

	// AI 대화 분석 동의를 바꾼다. 동의 시각은 마지막으로 동의한 시각이고, 철회하면 비운다
	public void changeAiAnalysisConsent(boolean consent, LocalDateTime now) {
		if (aiAnalysisConsent == consent) {
			return;
		}
		aiAnalysisConsent = consent;
		aiAnalysisConsentedAt = consent ? now : null;
		updatedAt = now;
	}
}
