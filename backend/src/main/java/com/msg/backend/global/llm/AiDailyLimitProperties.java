package com.msg.backend.global.llm;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 하루 AI 호출 상한 (app.ai-daily-limit). 다시 부르기(재시도)도 한 번으로 센다. 날짜는 zone 기준으로 나눈다.
 * calls는 서비스 전체 상한(계정을 여러 개 만들어도 넘을 수 없는 비용 상한), userCalls는 사용자 한 명의 몫이다.
 * 기능별 사용자 한도(분석 횟수·퀴즈 만들기 수 등)와 따로, 모든 기능의 모델 호출을 합쳐 센다.
 *
 * @param calls     서비스 전체가 하루에 부를 수 있는 모델 호출 수. 0 이하면 상한 없음
 * @param userCalls 사용자 한 명이 하루에 부를 수 있는 모델 호출 수. 0 이하면 상한 없음
 * @param zone      하루를 나누는 시간대
 */
@ConfigurationProperties(prefix = "app.ai-daily-limit")
public record AiDailyLimitProperties(int calls, int userCalls, ZoneId zone) {

	public boolean enabled() {
		return calls > 0;
	}

	public boolean userEnabled() {
		return userCalls > 0;
	}
}
