package com.msg.backend.domain.quiz.dto;

import com.msg.backend.domain.quiz.application.GradingNotes;
import com.msg.backend.domain.quiz.application.BloomPlan;
import com.msg.backend.domain.quiz.application.QuizAssessor;
import com.msg.backend.domain.quiz.entity.BloomLevel;
import com.msg.backend.domain.quiz.entity.QuestionType;
import com.msg.backend.domain.quiz.entity.Quiz;
import com.msg.backend.domain.quiz.entity.QuizAnswer;
import com.msg.backend.domain.quiz.entity.QuizQuestion;
import com.msg.backend.domain.quiz.entity.QuizRubricItem;
import com.msg.backend.domain.quiz.entity.QuizSubmission;
import com.msg.backend.domain.quiz.entity.RubricScore;
import com.msg.backend.global.common.ApiEnumValues;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 채점 결과 (frontend/src/types/quiz.ts의 QuizResult). V6 필드(submissionId~results의 correctChoiceIndex·aiExplanation)는 그대로 두고 더했다.
 * 점수·채점 상태·결과 판단(assessment)은 서버 코드가 정한다. ai로 시작하는 필드와 suggestions만 AI가 쓴 글이다.
 */
public record QuizResultResponse(
		Long submissionId,
		LocalDateTime submittedAt,
		@Schema(description = "만점을 받은 문제 수") int correctCount,
		int totalCount,
		@Schema(description = "채점이 끝난 답의 점수 합 (서버 계산)") int score,
		@Schema(description = "채점이 끝난 답의 만점 합") int maxScore,
		@Schema(description = "AI 채점을 기다리는 답 수") int pendingCount,
		@Schema(description = "AI 판단이 엇갈려 확인이 필요한 답 수") int reviewCount,
		@Schema(description = "exam_prep / self_development / work. V6 퀴즈는 null") String mode,
		List<Result> results,
		Assessment assessment,
		@Schema(description = "AI가 제안한 다음 학습 개념 (자기개발). 실제 결과가 아니라 제안") List<Suggestion> suggestions,
		@Schema(description = "출제 설계와 자료 근거 요약 (인지 수준·자료 범위·근거 검증 개수, 코드가 센 값)") LearningDesign learningDesign) {

	@Schema(description = "출제 설계. grounding은 문제별 근거 검증 상태를 센 값이고, 원문 인용이 있다는 것만 확인한 것이지 정답·해설 전체가 사실이라는 뜻이 아니다")
	public record LearningDesign(
			List<String> bloomTargets,
			@Schema(description = "문제를 만든 지시문 버전. 이 기능 전 퀴즈는 null") String promptVersion,
			boolean materialContentUsed,
			@Schema(description = "AI에 보낸 자료 위치") String materialScope,
			@Schema(description = "요청한 개념 중 자료에서 찾지 못한 개념") List<String> missingConcepts,
			Grounding grounding) {
	}

	public record Grounding(int verified, int partial, int failed, int insufficient) {
	}

	@Schema(description = "문제의 자료 근거 (자료 본문으로 만든 문제만). 위치는 서버가 원문에서 찾은 값")
	public record Source(
			@Schema(description = "verified: 원문과 일치 / partial: 일부 일치·위치 고침 / failed: 원문에서 찾지 못함 / insufficient: 인용 없음") String check,
			@Schema(description = "원문에서 확인한 인용문 (failed·insufficient면 null)") String quote,
			@Schema(description = "인용 위치 (예: p. 12, 슬라이드 3, 문단 4)") String location,
			@Schema(description = "검증 메모") String note,
			@Schema(description = "정답 표현이 근거 부분에 나오는지 (참고 신호, 객관식·주관식만). 사실성 검증이 아니다") Boolean answerInSource) {
	}

	public record Result(
			Long questionId,
			@Schema(description = "multiple_choice / short_answer / essay") String type,
			@Schema(description = "객관식: 고른 보기 순서(0부터), 안 골랐으면 -1. 다른 유형은 -1") int choiceIndex,
			@Schema(description = "주관식·서술형 답. 안 썼으면 null") String textAnswer,
			@Schema(description = "만점을 받았는지 (채점이 끝난 답만 true가 될 수 있다)") boolean correct,
			@Schema(description = "객관식 정답 번호. 다른 유형은 null") Integer correctChoiceIndex,
			@Schema(description = "이 문제가 다루는 개념. conceptId는 학습 분석의 개념 키와 같은 값") List<Concept> concepts,
			@Schema(description = "AI가 쓴 해설. 없으면 null") String aiExplanation,
			int score,
			int maxScore,
			@Schema(description = "graded / needs_review / pending") String gradingStatus,
			@Schema(description = "code / match / ai") String gradingMethod,
			@Schema(description = "채점 대기·확인 필요 이유 (사용자용 문구). 없으면 null") String gradingMessage,
			@Schema(description = "판단을 부른 AI 모델 수") int aiCalledCount,
			@Schema(description = "판단을 돌려준 AI 모델 수") int aiJudgedCount,
			@Schema(description = "주관식 허용 답 (제출 뒤에만)") List<String> acceptedAnswers,
			@Schema(description = "참고 모범 답안 (AI, 제출 뒤에만)") String aiModelAnswer,
			@Schema(description = "서술형 채점 기준별 결과") List<RubricResult> rubric,
			@Schema(description = "AI 피드백. 없으면 null") AiFeedback aiFeedback,
			@Schema(description = "평가하려는 인지 수준 (AI가 지정, 서버는 형식만 확인). 없으면 null") String bloomLevel,
			@Schema(description = "출제 목표 (AI). 없으면 null") String aiObjective,
			@Schema(description = "자료 근거. 자료 없이 만든 문제는 null") Source source) {
	}

	public record Concept(String conceptId, String name) {
	}

	public record RubricResult(
			String criterion,
			int points,
			@Schema(description = "얻은 점수 (코드 계산). 다수 의견이 없으면 null") Integer earned,
			@Schema(description = "met / partial / not_met. 다수 의견이 없으면 null") String level,
			int agreeCount,
			int judgedCount,
			@Schema(description = "다수 의견을 낸 모델의 근거 (AI)") String aiReason) {
	}

	public record AiFeedback(String strengths, String missing, String improvements, String practical) {
	}

	public record Assessment(
			@Schema(description = "ready: 다음 단계 준비 / not_ready: 보완 필요 / needs_check: 추가 확인 필요 (코드 규칙)") String status,
			@Schema(description = "판단 근거 (코드가 센 숫자)") List<String> reasons,
			List<TypeStat> byType,
			List<ConceptStat> byConcept) {
	}

	public record TypeStat(String type, int questionCount, int gradedCount, int score, int maxScore) {
	}

	public record ConceptStat(String conceptId, String name, int questionCount, int gradedCount,
			@Schema(description = "채점이 끝나고 만점을 받은 문제 수") int correctCount, int score,
			int maxScore, int pendingCount, int reviewCount, @Schema(description = "득점률이 기준보다 낮은 보완 개념") boolean weak) {
	}

	public record Suggestion(@Schema(description = "next / prerequisite / related") String kind, String concept,
			String aiReason) {
	}

	public static QuizResultResponse of(Quiz quiz, QuizSubmission submission, List<QuizQuestion> questions,
			Map<Long, QuizAnswer> answers, QuizAssessor.Result assessment) {
		List<Result> results = new ArrayList<>();
		for (QuizQuestion question : questions) {
			QuizAnswer answer = answers.get(question.getId());
			results.add(answer == null ? unanswered(question) : result(question, answer));
		}
		return new QuizResultResponse(submission.getId(), submission.getSubmittedAt(), submission.getCorrectCount(),
				submission.getTotalCount(), submission.getScore(), submission.getMaxScore(),
				submission.getPendingCount(), submission.getReviewCount(), ApiEnumValues.of(quiz.getMode()), results,
				new Assessment(assessment.status().apiValue(), assessment.reasons(),
						assessment.byType().stream().map(t -> new TypeStat(t.type().apiValue(), t.questionCount(),
								t.gradedCount(), t.score(), t.maxScore())).toList(),
						assessment.byConcept().stream().map(c -> new ConceptStat(c.conceptKey(), c.name(),
								c.questionCount(), c.gradedCount(), c.correctCount(), c.score(), c.maxScore(), c.pendingCount(),
								c.reviewCount(), c.weak())).toList()),
				quiz.getSuggestions().stream()
						.map(s -> new Suggestion(s.getKind().apiValue(), s.getConcept(), s.getReason()))
						.toList(),
				learningDesign(quiz, questions));
	}

	private static LearningDesign learningDesign(Quiz quiz, List<QuizQuestion> questions) {
		int[] counts = new int[4];
		for (QuizQuestion question : questions) {
			if (question.getSourceCheck() == null) {
				continue;
			}
			switch (question.getSourceCheck()) {
				case VERIFIED -> counts[0]++;
				case PARTIAL, NEEDS_CHECK -> counts[1]++;
				case FAILED -> counts[2]++;
				case INSUFFICIENT -> counts[3]++;
			}
		}
		List<String> missing = quiz.getMaterialMissingConcepts() == null ? List.of()
				: java.util.Arrays.stream(quiz.getMaterialMissingConcepts().split(",\\s*")).filter(v -> !v.isBlank())
						.toList();
		return new LearningDesign(
				BloomPlan.split(quiz.getBloomTargets()).stream().map(BloomLevel::apiValue).toList(),
				quiz.getPromptVersion(), quiz.isMaterialContentUsed(), quiz.getMaterialScope(), missing,
				new Grounding(counts[0], counts[1], counts[2], counts[3]));
	}

	private static Source source(QuizQuestion question) {
		if (question.getSourceCheck() == null) {
			return null;
		}
		return new Source(question.getSourceCheck().apiValue(), question.getSourceQuote(),
				question.getSourceLocation(), question.getSourceNote(), question.getAnswerInSource());
	}

	private static Result result(QuizQuestion question, QuizAnswer answer) {
		boolean hasFeedback = answer.getAiStrengths() != null || answer.getAiMissing() != null
				|| answer.getAiImprovements() != null || answer.getAiPractical() != null;
		return new Result(question.getId(), question.getType().apiValue(), answer.getChoiceIndex(),
				answer.getTextAnswer(), answer.isCorrect(), question.getCorrectChoiceIndex(), concepts(question),
				question.getAiExplanation(), answer.getScore(), answer.getMaxScore(),
				answer.getGradingStatus().apiValue(), answer.getGradingMethod().apiValue(),
				GradingNotes.message(answer.getGradingNote()), answer.getAiCalledCount(), answer.getAiJudgedCount(),
				List.copyOf(question.getAcceptedAnswers()), question.getModelAnswer(),
				rubric(question, answer.getRubricScores()),
				hasFeedback ? new AiFeedback(answer.getAiStrengths(), answer.getAiMissing(), answer.getAiImprovements(),
						answer.getAiPractical()) : null,
				ApiEnumValues.of(question.getBloomLevel()), question.getLearningObjective(), source(question));
	}

	// 제출 기록에 답 행이 없는 문제 (V6 제출 뒤 문제가 바뀐 경우 등): 답하지 않은 것으로 보여 준다
	private static Result unanswered(QuizQuestion question) {
		return new Result(question.getId(), question.getType().apiValue(), -1, null, false,
				question.getCorrectChoiceIndex(), concepts(question), question.getAiExplanation(), 0,
				question.maxScore(), "graded", question.getType() == QuestionType.MULTIPLE_CHOICE ? "code" : "match",
				null, 0, 0, List.copyOf(question.getAcceptedAnswers()), question.getModelAnswer(),
				rubric(question, List.of()), null, ApiEnumValues.of(question.getBloomLevel()),
				question.getLearningObjective(), source(question));
	}

	private static List<Concept> concepts(QuizQuestion question) {
		return question.getConcepts().stream().map(c -> new Concept(c.getConceptKey(), c.getConceptName())).toList();
	}

	private static List<RubricResult> rubric(QuizQuestion question, List<RubricScore> scores) {
		List<QuizRubricItem> items = question.getRubric();
		List<RubricResult> result = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			RubricScore score = i < scores.size() ? scores.get(i) : null;
			result.add(new RubricResult(items.get(i).getCriterion(), items.get(i).getPoints(),
					score == null || score.getLevel() == null ? null : score.getPoints(),
					score == null ? null : ApiEnumValues.of(score.getLevel()),
					score == null ? 0 : score.getAgreeCount(), score == null ? 0 : score.getJudgedCount(),
					score == null ? null : score.getReason()));
		}
		return result;
	}
}
