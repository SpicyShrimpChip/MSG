package com.ailms.backend.domain.study.application;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 개념 학습 상태 판정 (코드 규칙, AI를 부르지 않는다). 학습 기록(학습 완료)과 확인 퀴즈 채점 결과는 따로 저장하고, 화면에 보여 줄 상태만 여기서 합친다.
 *
 * <ul>
 * <li>확인 퀴즈 결과(가장 최근 제출)가 마지막 학습 완료보다 나중이면 그 결과로 정한다:
 * 채점 대기·확인 필요(AI 판단 불일치 포함) 답이 있거나 채점이 끝난 문제가 {@code minGraded}개 미만 → 추가 확인 필요,
 * 득점률 ≥ {@code minRate} → 이해 확인, 아니면 → 복습 필요.</li>
 * <li>그렇지 않고 학습 완료 기록이 있으면 → 학습 완료(확인 퀴즈를 아직 안 풂, 또는 복습 뒤 다시 풀 차례).</li>
 * <li>둘 다 없으면 → 학습 전.</li>
 * </ul>
 * 채점이 끝나지 않은 답은 틀린 답으로 세지 않는다. 학습 완료만으로 이해 확인이 되지 않는다.
 */
public final class ConceptStudyRules {

	private ConceptStudyRules() {
	}

	public enum Status {
		NOT_STUDIED, STUDIED, UNDERSTOOD, REVIEW_NEEDED, NEEDS_CHECK;

		public String apiValue() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** 확인 퀴즈 결과 하나 (그 개념의 문제만 센 값). gradedCount는 채점이 끝난 문제 수, waitingCount는 대기·확인 필요 수 */
	public record CheckResult(LocalDateTime submittedAt, int gradedCount, int score, int maxScore, int waitingCount) {

		public Integer rate() {
			return gradedCount == 0 || maxScore == 0 ? null : Math.round(score * 100f / maxScore);
		}
	}

	/** 확인 퀴즈 결과 하나의 판정 (이해 확인 / 복습 필요 / 추가 확인 필요) */
	public static Status judge(CheckResult result, double minRate, int minGraded) {
		if (result.waitingCount() > 0 || result.gradedCount() < minGraded || result.maxScore() == 0) {
			return Status.NEEDS_CHECK;
		}
		return (double) result.score() / result.maxScore() >= minRate ? Status.UNDERSTOOD : Status.REVIEW_NEEDED;
	}

	/** 학습 기록(마지막 학습 완료 시각, 없으면 null)과 가장 최근 확인 퀴즈 결과(없으면 null)를 합친 상태 */
	public static Status resolve(LocalDateTime lastStudiedAt, CheckResult latest, double minRate, int minGraded) {
		if (latest != null && (lastStudiedAt == null || !latest.submittedAt().isBefore(lastStudiedAt))) {
			return judge(latest, minRate, minGraded);
		}
		return lastStudiedAt != null ? Status.STUDIED : Status.NOT_STUDIED;
	}

	/** 상태를 설명하는 문장 (코드가 센 사실) */
	public static String reason(Status status, CheckResult latest, double minRate, int minGraded, int studyCount) {
		int minPercent = (int) Math.round(minRate * 100);
		return switch (status) {
			case NOT_STUDIED -> "아직 이 개념의 학습 완료 기록이 없어요.";
			case STUDIED -> latest == null ? "학습을 완료했어요. 확인 퀴즈로 이해했는지 확인해 보세요."
					: "다시 공부했어요(학습 " + studyCount + "번째). 확인 퀴즈를 다시 풀어 보세요.";
			case UNDERSTOOD -> "확인 퀴즈 " + latest.score() + "/" + latest.maxScore() + "점(" + latest.rate()
					+ "%)으로 기준(" + minPercent + "%) 이상이에요.";
			case REVIEW_NEEDED -> "확인 퀴즈 " + latest.score() + "/" + latest.maxScore() + "점(" + latest.rate()
					+ "%)으로 기준(" + minPercent + "%)보다 낮아요. 틀린 문제를 확인하고 다시 공부해 보세요.";
			case NEEDS_CHECK -> latest.waitingCount() > 0
					? "채점을 기다리거나 AI 판단이 엇갈려 확인이 필요한 답이 " + latest.waitingCount() + "개 있어 아직 판단하지 않았어요."
					: "채점이 끝난 문제가 " + latest.gradedCount() + "개라 " + minGraded + "개보다 적어 판단하기 일러요.";
		};
	}
}
