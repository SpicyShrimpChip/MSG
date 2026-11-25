package com.msg.backend.global.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * 서비스 전체 하루 AI 모델 호출 수 (사용자와 연결하지 않는다).
 * 키(날짜)를 직접 넣는 엔티티라 Persistable이 없으면 save가 merge가 되어, 동시에 다른 요청이 만들고 올린 줄의
 * 수를 0으로 덮어쓴다. start()로 만든 객체는 새것(persist)으로 다뤄 이미 있으면 기본 키 충돌이 나게 한다.
 */
@Entity
@Table(name = "ai_daily_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDailyUsage implements Persistable<LocalDate> {

	@Id
	@Column(name = "usage_date")
	private LocalDate usageDate;

	@Column(name = "call_count", nullable = false)
	private int callCount;

	@Transient
	@Getter(AccessLevel.NONE)
	private boolean fresh;

	static AiDailyUsage start(LocalDate usageDate) {
		AiDailyUsage usage = new AiDailyUsage();
		usage.usageDate = usageDate;
		usage.callCount = 0;
		usage.fresh = true;
		return usage;
	}

	@Override
	public LocalDate getId() {
		return usageDate;
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
}
