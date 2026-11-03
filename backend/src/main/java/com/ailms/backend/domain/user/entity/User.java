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

	// AI 퀴즈 처리 동의(선택, V8). 퀴즈 만들기·주관식·서술형 AI 채점 때 과목 이름·개념·시험 범위·직무·내 답을 외부 AI로 보낸다.
	// 대화 분석 동의와 목적·항목이 달라 따로 받는다. 기존 회원은 false (자동 동의하지 않는다)
	@Column(nullable = false)
	private boolean aiQuizConsent;

	private LocalDateTime aiQuizConsentedAt;

	// 주된 학습 목적. 퀴즈를 만들 때 기본 모드로 쓴다 (V7, 기존 회원은 UNDECIDED)
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private LearningPurpose learningPurpose = LearningPurpose.UNDECIDED;

	// 관심 직무 분야. 고르지 않았으면 null (업무 활용 퀴즈는 일반 실무 상황으로 만든다)
	@Enumerated(EnumType.STRING)
	@Column(length = 40)
	private JobField jobField;

	// jobField가 OTHER일 때만 있는 짧은 분야 이름
	@Column(length = JobField.OTHER_MAX_LENGTH)
	private String jobFieldOther;

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

	/**
	 * 학습 목적·관심 직무를 바꾼다. 값은 서비스에서 검증한 것만 받는다.
	 * 기타가 아니면 직접 쓴 분야 이름은 지운다.
	 */
	public void changeLearningProfile(LearningPurpose purpose, JobField jobField, String jobFieldOther,
			LocalDateTime now) {
		this.learningPurpose = purpose;
		this.jobField = jobField;
		this.jobFieldOther = jobField == JobField.OTHER ? jobFieldOther : null;
		this.updatedAt = now;
	}

	// AI 퀴즈 처리 동의를 바꾼다. 동의 시각은 마지막으로 동의한 시각이고, 철회하면 비운다
	public void changeAiQuizConsent(boolean consent, LocalDateTime now) {
		if (aiQuizConsent == consent) {
			return;
		}
		aiQuizConsent = consent;
		aiQuizConsentedAt = consent ? now : null;
		updatedAt = now;
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
