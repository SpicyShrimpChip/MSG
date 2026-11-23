package com.msg.backend.global.llm;

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

// 사용자별 24시간 AI 이용 한 번 (V16 user_ai_usage). 과목·제출과 연결하지 않아 그것들을 지워도 줄지 않는다
@Entity
@Table(name = "user_ai_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAiUsageRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserAiUsage.Kind kind;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	static UserAiUsageRecord of(Long userId, UserAiUsage.Kind kind, LocalDateTime createdAt) {
		UserAiUsageRecord record = new UserAiUsageRecord();
		record.userId = userId;
		record.kind = kind;
		record.createdAt = createdAt;
		return record;
	}
}
