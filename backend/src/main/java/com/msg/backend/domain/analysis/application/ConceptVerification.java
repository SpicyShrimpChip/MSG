package com.msg.backend.domain.analysis.application;

import com.msg.backend.domain.analysis.entity.ConsensusOutcome;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import com.msg.backend.global.common.VerificationStatus;
import java.util.ArrayList;
import java.util.List;

/**
 * 대화 분석 개념 하나의 검증 상태 (코드 규칙, AI를 부르지 않는다). AI 판단(추정)과 실제 퀴즈 결과(평가)를 따로 보고 함께 비교한다.
 * 모델 의견이 같다는 것은 정확도가 아니므로, "검증 완료"는 실제 확인 퀴즈 결과가 AI 판단과 같은 방향일 때만 준다.
 *
 * <ul>
 * <li>판단한 모델 없음 → 검증 실패</li>
 * <li>모델 의견이 모두 다름 → 추가 확인 필요</li>
 * <li>일치한 상태가 근거 부족 → 근거 부족 (근거 부족은 이해 부족이 아니다)</li>
 * <li>퀴즈 근거가 충분함(채점된 문제 minGraded개 이상, 채점 대기 없음): 이해 ↔ 득점률 ≥ minRate, 이해 부족 ↔ 득점률 &lt; minRate면 같은 방향.
 * 같은 방향이고 2개 이상 모델이 판단했고 부른 모델이 모두 판단했으면 검증 완료, 모델 하나뿐이거나 일부 실패면 일부 검증.
 * 반대 방향이면 추가 확인 필요. 부분 이해는 방향을 비교하지 않는다.</li>
 * <li>퀴즈 근거가 없으면 일부 검증 (AI 추정만 있음)</li>
 * <li>오개념 후보가 있고 검증 완료가 아니면 추가 확인 필요</li>
 * </ul>
 * 검증 완료가 아니면 확인 퀴즈를 권한다.
 */
public final class ConceptVerification {

	private ConceptVerification() {
	}

	/** 퀴즈 결과 (그 개념의 문제만 센 값). 없으면 null */
	public record Quiz(int gradedCount, int score, int maxScore, int pendingCount) {
	}

	public record Input(ConsensusOutcome outcome, UnderstandingStatus status, int judgedCount, int agreeCount,
			int calledCount, int misconceptionCount, Quiz quiz) {
	}

	/**
	 * @param quizComparison consistent(같은 방향) / contradicts(반대) / not_compared(부분 이해 등 비교하지 않음) / no_quiz(퀴즈 근거 부족)
	 */
	public record Result(VerificationStatus status, List<String> reasons, String holdReason, String quizComparison,
			boolean recommendCheckQuiz) {
	}

	public static Result evaluate(Input input, int minGraded, double minRate) {
		List<String> reasons = new ArrayList<>();
		if (input.calledCount() > input.judgedCount() && input.judgedCount() > 0) {
			reasons.add("부른 모델 " + input.calledCount() + "개 중 " + input.judgedCount()
					+ "개만 이 개념을 판단했어요(나머지는 실패했거나 판단하지 않았어요).");
		}
		if (input.outcome() == ConsensusOutcome.NO_RESULT || input.judgedCount() == 0) {
			String hold = "이 개념을 판단한 모델이 없어요.";
			reasons.add(0, hold);
			return new Result(VerificationStatus.FAILED, reasons, hold, "no_quiz", true);
		}
		if (input.outcome() == ConsensusOutcome.DISAGREEMENT) {
			String hold = "판단한 모델 " + input.judgedCount() + "개의 의견이 서로 달라요.";
			reasons.add(0, hold);
			return new Result(VerificationStatus.NEEDS_CHECK, reasons, hold, quizNote(input, minGraded, minRate, reasons),
					true);
		}
		if (input.status() == UnderstandingStatus.INSUFFICIENT_EVIDENCE || input.status() == null) {
			String hold = "사용자 발언에 판단 근거가 부족해 AI가 판단을 보류했어요. 근거 부족은 이해 부족이 아니에요.";
			reasons.add(0, hold);
			return new Result(VerificationStatus.INSUFFICIENT, reasons, hold,
					quizNote(input, minGraded, minRate, reasons), true);
		}
		reasons.add(0, switch (input.outcome()) {
			case UNANIMOUS -> "판단한 모델 " + input.judgedCount() + "개가 같은 의견이에요(의견 일치는 정확도가 아니에요).";
			case MAJORITY -> "판단한 모델 " + input.judgedCount() + "개 중 " + input.agreeCount() + "개가 같은 의견(다수 의견)이에요.";
			default -> "모델 1개만 판단해 다른 모델과 비교하지 못했어요.";
		});
		String comparison = compare(input, minGraded, minRate);
		VerificationStatus status;
		String hold = null;
		switch (comparison) {
			case "consistent" -> {
				reasons.add("AI 판단과 확인 퀴즈 결과(" + rateText(input.quiz()) + ")가 같은 방향이에요.");
				boolean allModels = input.outcome() != ConsensusOutcome.SINGLE_MODEL
						&& input.judgedCount() == input.calledCount();
				status = allModels ? VerificationStatus.VERIFIED : VerificationStatus.PARTIAL;
			}
			case "contradicts" -> {
				hold = "AI 판단과 확인 퀴즈 결과(" + rateText(input.quiz()) + ")가 달라요. 실제 퀴즈 결과를 먼저 참고하세요.";
				reasons.add(hold);
				status = VerificationStatus.NEEDS_CHECK;
			}
			case "not_compared" -> {
				reasons.add("부분 이해는 퀴즈 결과와 방향을 비교하지 않아요. 퀴즈 결과는 따로 보세요.");
				status = VerificationStatus.PARTIAL;
			}
			default -> {
				reasons.add("AI 추정만 있고 확인 퀴즈로 확인하지 않았어요(채점된 문제 " + minGraded + "개 이상 필요).");
				status = VerificationStatus.PARTIAL;
			}
		}
		if (input.misconceptionCount() > 0 && status != VerificationStatus.VERIFIED) {
			String note = "오개념 후보: 모델 " + input.misconceptionCount() + "개가 잘못된 설명을 지적했어요. 확인 퀴즈로 확인해 보세요.";
			reasons.add(note);
			status = VerificationStatus.NEEDS_CHECK;
			hold = hold == null ? note : hold;
		}
		return new Result(status, reasons, hold, comparison, status != VerificationStatus.VERIFIED);
	}

	// 판단을 보류한 경우에도 퀴즈 결과는 따로 알려 준다 (AI 판단과 섞지 않음)
	private static String quizNote(Input input, int minGraded, double minRate, List<String> reasons) {
		if (enoughQuiz(input.quiz(), minGraded)) {
			reasons.add("확인 퀴즈 결과는 " + rateText(input.quiz()) + "예요(AI 판단과 따로 봐요).");
			return "not_compared";
		}
		return "no_quiz";
	}

	private static String compare(Input input, int minGraded, double minRate) {
		if (!enoughQuiz(input.quiz(), minGraded)) {
			return "no_quiz";
		}
		boolean quizUnderstood = (double) input.quiz().score() / input.quiz().maxScore() >= minRate;
		return switch (input.status()) {
			case UNDERSTOOD -> quizUnderstood ? "consistent" : "contradicts";
			case NOT_UNDERSTOOD -> quizUnderstood ? "contradicts" : "consistent";
			default -> "not_compared";
		};
	}

	private static boolean enoughQuiz(Quiz quiz, int minGraded) {
		return quiz != null && quiz.gradedCount() >= minGraded && quiz.pendingCount() == 0 && quiz.maxScore() > 0;
	}

	private static String rateText(Quiz quiz) {
		return Math.round(quiz.score() * 100f / quiz.maxScore()) + "%, 채점된 문제 " + quiz.gradedCount() + "개";
	}
}
