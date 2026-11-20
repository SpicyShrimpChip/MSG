package com.ailms.backend.domain.analysis.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 학습 분석 설정 (app.analysis). 외부 모델에 보내는 양과 호출 횟수를 줄이는 값들이다.
 *
 * @param maxInputChars   한 번 분석에 모델로 보내는 대화 글자 수 상한 (최근 대화부터 담고, 넘치는 대화는 최근 메시지만 담는다)
 * @param maxMessageChars 메시지 하나에서 보내는 글자 수 상한 (넘으면 뒤를 자른다)
 * @param contextWindow   키워드가 들어간 메시지 앞뒤로 함께 보낼 메시지 수 (나머지 메시지는 보내지 않는다)
 * @param maxOutputTokens 모델 답변 길이 상한
 * @param dailyRunLimit   사용자 한 명이 24시간 안에 시작할 수 있는 분석 수 (분석 한 번에 모델 최대 3번 호출)
 * @param runningTimeout  이보다 오래 RUNNING인 분석은 끝나지 않은 것으로 보고 새 분석을 막지 않는다
 * @param deadline        분석 한 번에서 모델을 기다리는 전체 시간. 프론트 timeout(180초)보다 짧게 둬서, 화면이 포기하기 전에
 *                        늦은 모델은 시간 초과로 두고 나머지 결과를 저장해 돌려준다
 */
@ConfigurationProperties(prefix = "app.analysis")
public record AnalysisProperties(int maxInputChars, int maxMessageChars, int contextWindow, int maxOutputTokens,
		int dailyRunLimit, Duration runningTimeout, Duration deadline) {
}
