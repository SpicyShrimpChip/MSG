package com.ailms.backend.domain.quiz.application;

import java.util.Map;

/**
 * 채점 대기·확인 필요 이유 코드 (quiz_answer.grading_note)와 화면에 보일 문구.
 */
public final class GradingNotes {

	/** 연결된 AI 모델이 없음 */
	public static final String NOT_CONFIGURED = "NOT_CONFIGURED";
	/** AI 퀴즈 처리 동의가 없어 AI로 보내지 않음 */
	public static final String NO_CONSENT = "NO_CONSENT";
	/** 24시간 AI 채점 한도를 다 씀 */
	public static final String LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
	/** 모델이 응답하지 않았거나 형식이 맞지 않음 */
	public static final String MODEL_FAILED = "MODEL_FAILED";
	/** 모델 판단의 과반 의견이 없음 */
	public static final String DISAGREEMENT = "DISAGREEMENT";
	/** 모델 과반이 확신하지 못함 (주관식) */
	public static final String UNSURE = "UNSURE";
	/** 판단을 돌려준 모델이 하나뿐 */
	public static final String SINGLE_MODEL = "SINGLE_MODEL";
	/** AI가 채점하고 있음 (실행 마감이 지나도 남아 있으면 다시 채점할 수 있다) */
	public static final String GRADING = "GRADING";
	/** 24시간 AI 모델 요청 수 상한에 닿음 */
	public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
	/** 답을 쓰지 않음 */
	public static final String NO_ANSWER = "NO_ANSWER";

	private static final Map<String, String> MESSAGES = Map.ofEntries(
			Map.entry(NOT_CONFIGURED, "연결된 AI 모델이 없어 채점을 기다리고 있어요."),
			Map.entry(NO_CONSENT, "AI 퀴즈 처리에 동의하지 않아 AI 채점을 하지 않았어요. 마이페이지에서 동의하면 다시 채점할 수 있어요."),
			Map.entry(LIMIT_EXCEEDED, "오늘 AI 채점 횟수를 다 써서 채점을 기다리고 있어요. 나중에 다시 채점해 주세요."),
			Map.entry(MODEL_FAILED, "AI 모델의 응답을 받지 못해 채점을 기다리고 있어요. 다시 채점해 주세요."),
			Map.entry(DISAGREEMENT, "AI 모델들의 판단이 엇갈려 점수에 넣지 않았어요. 직접 확인해 주세요."),
			Map.entry(UNSURE, "AI 모델이 맞는지 확신하지 못해 점수에 넣지 않았어요. 직접 확인해 주세요."),
			Map.entry(SINGLE_MODEL, "AI 모델 하나만 판단한 결과예요. 다른 문제로 한 번 더 확인해 보세요."),
			Map.entry(NO_ANSWER, "답을 쓰지 않았어요."),
			Map.entry(GRADING, "AI가 채점하고 있어요. 잠시 뒤 결과를 다시 확인해 주세요."),
			Map.entry(BUDGET_EXCEEDED, "오늘 AI 호출 한도를 다 써서 채점을 기다리고 있어요. 내일 다시 채점해 주세요."));

	private GradingNotes() {
	}

	public static String message(String note) {
		return note == null ? null : MESSAGES.get(note);
	}
}
