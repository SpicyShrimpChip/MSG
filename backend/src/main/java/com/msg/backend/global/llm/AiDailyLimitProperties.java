package com.msg.backend.global.llm;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 서비스 전체 하루 AI 호출 상한 (app.ai-daily-limit). 사용자별 한도와 따로, 계정을 여러 개 만들어도 넘을 수 없는 비용 상한이다.
 * 다시 부르기(재시도)도 한 번으로 센다.
 *
 * @param calls 하루(zone 기준 날짜)에 부를 수 있는 모델 호출 수. 0 이하면 상한 없음
 * @param zone  하루를 나누는 시간대
 */
@ConfigurationProperties(prefix = "app.ai-daily-limit")
public record AiDailyLimitProperties(int calls, ZoneId zone) {

	public boolean enabled() {
		return calls > 0;
	}
}
