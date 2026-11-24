package com.msg.backend.global.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자별 하루 AI 모델 호출 수 (V4 user_ai_call_daily). users와 외래 키로 잇지 않는다(이유는 마이그레이션 주석)
@Entity
@Table(name = "user_ai_call_daily")
@IdClass(UserAiCallDaily.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAiCallDaily {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "usage_date")
	private LocalDate usageDate;

	@Column(name = "call_count", nullable = false)
	private int callCount;

	static UserAiCallDaily start(Long userId, LocalDate usageDate) {
		UserAiCallDaily usage = new UserAiCallDaily();
		usage.userId = userId;
		usage.usageDate = usageDate;
		usage.callCount = 0;
		return usage;
	}

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	@EqualsAndHashCode
	public static class Key implements Serializable {
		private Long userId;
		private LocalDate usageDate;
	}
}
