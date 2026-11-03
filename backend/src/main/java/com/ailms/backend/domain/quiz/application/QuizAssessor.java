package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.quiz.entity.GradingStatus;
import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.QuizConcept;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 채점 결과를 유형별·개념별로 모으고 학습 상태를 코드 규칙으로 판단한다. AI는 관여하지 않는다.
 * 숙련도 점수(대화 분석 + 퀴즈 가중치)는 미결이라 만들지 않고, 득점률과 판단 근거만 낸다.
 * <ul>
 * <li>needs_check(추가 확인 필요): 채점이 끝난 문제가 minGradedQuestions보다 적거나, 채점 대기·확인 필요 답이 있거나,
 * AI 모델 하나만 채점한 서술형이 있으면. 이때는 준비됐다고 단정하지 않는다.</li>
 * <li>ready(다음 단계 준비): 전체 득점률 ≥ readyRate이고 모든 개념 득점률 ≥ conceptMinRate.</li>
 * <li>not_ready(보완 필요): 그 밖. 득점률이 conceptMinRate보다 낮은 개념을 보완 개념(weak)으로 표시한다.</li>
 * </ul>
 * 점수에는 채점이 끝난(GRADED) 답만 넣는다.
 */
public final class QuizAssessor {

	public enum Status {
		READY, NOT_READY, NEEDS_CHECK;

		public String apiValue() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	// 채점한 답 하나 (문제와 함께)
	public record Graded(QuizQuestion question, GradingStatus status, int score, int maxScore, String note) {
	}

	public record TypeStat(QuestionType type, int questionCount, int gradedCount, int score, int maxScore) {
	}

	// correctCount: 채점이 끝나고 만점을 받은 문제 수. 대기·확인 필요 답은 틀린 것으로 세지 않는다
	public record ConceptStat(String conceptKey, String name, int questionCount, int gradedCount, int correctCount,
			int score, int maxScore, int pendingCount, int reviewCount, boolean weak) {

		public Double rate() {
			return maxScore == 0 ? null : (double) score / maxScore;
		}
	}

	public record Result(Status status, List<String> reasons, int gradedCount, int score, int maxScore,
			List<TypeStat> byType, List<ConceptStat> byConcept) {
	}

	private QuizAssessor() {
	}

	public static Result assess(List<Graded> answers, QuizProperties.Assessment rule) {
		List<ConceptStat> concepts = byConcept(answers, rule.conceptMinRate());
		int graded = 0;
		int score = 0;
		int maxScore = 0;
		int pending = 0;
		int review = 0;
		boolean singleModelEssay = false;
		for (Graded answer : answers) {
			if (answer.status() == GradingStatus.GRADED) {
				graded++;
				score += answer.score();
				maxScore += answer.maxScore();
				singleModelEssay |= answer.question().getType() == QuestionType.ESSAY
						&& GradingNotes.SINGLE_MODEL.equals(answer.note());
			} else if (answer.status() == GradingStatus.PENDING) {
				pending++;
			} else {
				review++;
			}
		}
		List<String> weak = concepts.stream().filter(ConceptStat::weak).map(ConceptStat::name).toList();
		List<String> reasons = new ArrayList<>();
		boolean needsCheck = false;
		if (graded < rule.minGradedQuestions()) {
			needsCheck = true;
			reasons.add("채점이 끝난 문제가 " + graded + "개라 판단하기에 적어요 (" + rule.minGradedQuestions() + "개 이상 필요).");
		}
		if (pending > 0) {
			needsCheck = true;
			reasons.add("AI 채점을 기다리는 답이 " + pending + "개 있어요.");
		}
		if (review > 0) {
			needsCheck = true;
			reasons.add("AI 모델 판단이 엇갈리거나 확신하지 못한 답이 " + review + "개 있어요.");
		}
		if (singleModelEssay) {
			needsCheck = true;
			reasons.add("서술형을 AI 모델 하나만 채점했어요.");
		}
		double rate = maxScore == 0 ? 0 : (double) score / maxScore;
		if (maxScore > 0) {
			reasons.add("채점이 끝난 문제의 득점은 " + score + "/" + maxScore + "점(" + Math.round(rate * 100) + "%)이에요.");
		}
		if (!weak.isEmpty()) {
			reasons.add("득점률이 " + Math.round(rule.conceptMinRate() * 100) + "%보다 낮은 개념: " + String.join(", ", weak));
		}
		Status status = needsCheck ? Status.NEEDS_CHECK
				: rate >= rule.readyRate() && weak.isEmpty() ? Status.READY : Status.NOT_READY;
		return new Result(status, reasons, graded, score, maxScore, byType(answers), concepts);
	}

	public static List<TypeStat> byType(List<Graded> answers) {
		Map<QuestionType, int[]> stats = new EnumMap<>(QuestionType.class);
		for (Graded answer : answers) {
			int[] stat = stats.computeIfAbsent(answer.question().getType(), type -> new int[4]);
			stat[0]++;
			if (answer.status() == GradingStatus.GRADED) {
				stat[1]++;
				stat[2] += answer.score();
				stat[3] += answer.maxScore();
			}
		}
		List<TypeStat> result = new ArrayList<>();
		stats.forEach((type, s) -> result.add(new TypeStat(type, s[0], s[1], s[2], s[3])));
		return result;
	}

	// 문제의 개념마다 그 문제의 점수를 그대로 더한다 (개념이 둘인 문제는 두 개념에 모두 들어간다)
	public static List<ConceptStat> byConcept(List<Graded> answers, double conceptMinRate) {
		Map<String, String> names = new LinkedHashMap<>();
		Map<String, int[]> stats = new LinkedHashMap<>();
		for (Graded answer : answers) {
			for (QuizConcept concept : answer.question().getConcepts()) {
				names.putIfAbsent(concept.getConceptKey(), concept.getConceptName());
				int[] stat = stats.computeIfAbsent(concept.getConceptKey(), key -> new int[7]);
				stat[0]++;
				switch (answer.status()) {
					case GRADED -> {
						stat[1]++;
						stat[2] += answer.score();
						stat[3] += answer.maxScore();
						stat[6] += answer.score() == answer.maxScore() ? 1 : 0;
					}
					case PENDING -> stat[4]++;
					case NEEDS_REVIEW -> stat[5]++;
				}
			}
		}
		List<ConceptStat> result = new ArrayList<>();
		stats.forEach((key, s) -> result.add(new ConceptStat(key, names.get(key), s[0], s[1], s[6], s[2], s[3], s[4], s[5],
				s[3] > 0 && (double) s[2] / s[3] < conceptMinRate)));
		return result;
	}
}
