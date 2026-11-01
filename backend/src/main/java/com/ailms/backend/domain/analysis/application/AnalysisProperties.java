package com.ailms.backend.domain.analysis.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 학습 분석 설정 (app.analysis). 외부 모델에 보내는 양과 호출 횟수를 줄이는 값들이다.
 *
 * @param maxInputChars   한 번 분석에 모델로 보내는 대화 글자 수 상한 (넘으면 오래된 대화부터 뺀다)
 * @param maxMessageChars 메시지 하나에서 보내는 글자 수 상한 (넘으면 뒤를 자른다)
 * @param contextWindow   키워드가 들어간 메시지 앞뒤로 함께 보낼 메시지 수 (나머지 메시지는 보내지 않는다)
 * @param maxOutputTokens 모델 답변 길이 상한
 * @param dailyRunLimit   사용자 한 명이 24시간 안에 시작할 수 있는 분석 수 (분석 한 번에 모델 최대 3번 호출)
 * @param runningTimeout  이보다 오래 RUNNING인 분석은 끝나지 않은 것으로 보고 새 분석을 막지 않는다
 */
@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(int maxInputChars, int maxMessageChars, int contextWindow, int maxOutputTokens,
		int dailyRunLimit, Duration runningTimeout) {
}
