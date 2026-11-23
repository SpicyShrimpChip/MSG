package com.msg.backend.global.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 서비스 전체 하루 AI 모델 호출 수 (사용자와 연결하지 않는다)
@Entity
@Table(name = "ai_daily_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiDailyUsage {

	@Id
	@Column(name = "usage_date")
	private LocalDate usageDate;

	@Column(name = "call_count", nullable = false)
	private int callCount;

	static AiDailyUsage start(LocalDate usageDate) {
		AiDailyUsage usage = new AiDailyUsage();
		usage.usageDate = usageDate;
		usage.callCount = 0;
		return usage;
	}
}
