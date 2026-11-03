package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.global.llm.LlmProvider;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 퀴즈 만들기·AI 채점 설정 (app.quiz). 호출 횟수(=비용)·시간 예산과 결과 판단 기준을 코드 밖에서 바꿀 수 있게 둔다.
 *
 * <p>비용 상한은 세 겹이다: (1) 요청 수 — 24시간 퀴즈 만들기 dailyGenerationLimit번, AI 채점 실행(처음 채점·다시 채점)
 * dailyGradingLimit번, (2) 실제 모델 요청 수 — 24시간 dailyModelCallLimit번(재시도·실패 포함, quiz_ai_call 합계),
 * (3) 시간 — 만들기는 generationDeadline, 채점은 gradingDeadline 안에서만 모델을 부르고 기다린다.
 *
 * @param generationProviders       퀴즈를 만들 때 쓸 모델 순서. 설정된 첫 모델 하나만 부르고, 실패하면 남은 시간 안에서 다음 모델로 넘어간다
 * @param maxQuestionCount          한 번에 만들 수 있는 문제 수
 * @param mockExamMaxQuestionCount  모의시험으로 만들 수 있는 문제 수
 * @param dailyGenerationLimit      사용자 한 명이 24시간 동안 보낼 수 있는 퀴즈 만들기 요청 수
 * @param dailyGradingLimit         24시간 동안 시작할 수 있는 AI 채점 실행 수 (처음 채점·다시 채점 모두, 실행 한 번에 모델 최대 3개)
 * @param dailyModelCallLimit       24시간 동안 퀴즈 기능이 실제로 보낼 수 있는 모델 요청 수 (재시도 포함)
 * @param generationDeadline        퀴즈 만들기 한 번의 전체 시간 예산 (모든 모델·재시도 포함). 프론트 timeout보다 짧게 둔다
 * @param gradingDeadline           AI 채점 실행 한 번의 전체 시간 예산
 * @param runningTimeout            마감 시각이 없는 옛(V7) 만들기 기록을 끝난 것으로 볼 시간
 * @param generationMaxOutputTokens 퀴즈 만들기 응답 길이 상한
 * @param gradingMaxOutputTokens    AI 채점 응답 길이 상한
 * @param assessment                결과 판단 기준 (코드 규칙, 숙련도 점수가 아님)
 */
@ConfigurationProperties(prefix = "app.quiz")
public record QuizProperties(List<LlmProvider> generationProviders, int maxQuestionCount, int mockExamMaxQuestionCount,
		int dailyGenerationLimit, int dailyGradingLimit, int dailyModelCallLimit, Duration generationDeadline,
		Duration gradingDeadline, Duration runningTimeout, int generationMaxOutputTokens, int gradingMaxOutputTokens,
		Assessment assessment) {

	/**
	 * @param minGradedQuestions 채점이 끝난 문제가 이보다 적으면 "추가 확인 필요"
	 * @param readyRate          전체 득점률이 이 이상이고
	 * @param conceptMinRate     모든 개념의 득점률이 이 이상이면 "다음 단계 준비됨". 개념 득점률이 이보다 낮으면 보완 개념
	 */
	public record Assessment(int minGradedQuestions, double readyRate, double conceptMinRate) {
	}
}
