package com.msg.backend.global.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

// 사용자별 하루 AI 모델 호출 수 (V4 user_ai_call_daily). users와 외래 키로 잇지 않는다(이유는 마이그레이션 주석)
// 키를 직접 넣는 엔티티라 start()로 만든 객체는 새것(persist)으로 다룬다. 이유는 AiDailyUsage 주석
@Entity
@Table(name = "user_ai_call_daily")
@IdClass(UserAiCallDaily.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAiCallDaily implements Persistable<UserAiCallDaily.Key> {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Id
	@Column(name = "usage_date")
	private LocalDate usageDate;

	@Column(name = "call_count", nullable = false)
	private int callCount;

	@Transient
	@Getter(AccessLevel.NONE)
	private boolean fresh;

	static UserAiCallDaily start(Long userId, LocalDate usageDate) {
		UserAiCallDaily usage = new UserAiCallDaily();
		usage.userId = userId;
		usage.usageDate = usageDate;
		usage.callCount = 0;
		usage.fresh = true;
		return usage;
	}

	@Override
	public Key getId() {
		return new Key(userId, usageDate);
	}

	@Override
	public boolean isNew() {
		return fresh;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		fresh = false;
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
