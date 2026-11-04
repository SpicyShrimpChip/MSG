package com.ailms.backend.domain.quiz.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 제출 한 번의 문제별 답과 채점 결과. 객관식은 choiceIndex(안 고르면 -1), 주관식·서술형은 textAnswer.
 * score는 서버 코드가 정한다. AI 피드백(ai*)은 점수와 따로 두는 설명이다.
 */
@Entity
@Table(name = "quiz_answer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizAnswer {

	public static final int TEXT_MAX_LENGTH = 5000;
	public static final int FEEDBACK_MAX_LENGTH = 1000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long submissionId;

	@Column(nullable = false)
	private Long questionId;

	@Column(nullable = false)
	private int choiceIndex;

	@Column(length = TEXT_MAX_LENGTH)
	private String textAnswer;

	// 만점을 받았는지 (V6부터 쓰던 값)
	@Column(nullable = false)
	private boolean correct;

	@Column(nullable = false)
	private int score;

	@Column(nullable = false)
	private int maxScore;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private GradingStatus gradingStatus = GradingStatus.GRADED;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private GradingMethod gradingMethod = GradingMethod.CODE;

	@Column(length = 30)
	private String gradingNote;

	@Column(nullable = false)
	private int aiCalledCount;

	@Column(nullable = false)
	private int aiJudgedCount;

	@Column(length = FEEDBACK_MAX_LENGTH)
	private String aiStrengths;

	@Column(length = FEEDBACK_MAX_LENGTH)
	private String aiMissing;

	@Column(length = FEEDBACK_MAX_LENGTH)
	private String aiImprovements;

	@Column(length = FEEDBACK_MAX_LENGTH)
	private String aiPractical;

	@ElementCollection
	@CollectionTable(name = "quiz_answer_rubric_score", joinColumns = @JoinColumn(name = "answer_id"))
	@OrderColumn(name = "sort_order")
	private List<RubricScore> rubricScores = new ArrayList<>();

	// 객관식 (V6와 같은 형태)
	public static QuizAnswer of(Long submissionId, Long questionId, int choiceIndex, boolean correct) {
		QuizAnswer answer = new QuizAnswer();
		answer.submissionId = submissionId;
		answer.questionId = questionId;
		answer.choiceIndex = choiceIndex;
		answer.correct = correct;
		answer.score = correct ? 1 : 0;
		answer.maxScore = 1;
		return answer;
	}

	// 채점 결과 하나를 그대로 담는다 (QuizService가 GradedAnswer에서 만든다)
	public static QuizAnswer from(Long submissionId, Long questionId, int choiceIndex, String textAnswer,
			GradeOutcome outcome) {
		QuizAnswer answer = new QuizAnswer();
		answer.submissionId = submissionId;
		answer.questionId = questionId;
		answer.choiceIndex = choiceIndex;
		answer.textAnswer = textAnswer;
		answer.apply(outcome);
		return answer;
	}

	// 다시 채점한 결과로 바꾼다
	public void apply(GradeOutcome outcome) {
		this.correct = outcome.status() == GradingStatus.GRADED && outcome.score() == outcome.maxScore();
		this.score = outcome.status() == GradingStatus.GRADED ? outcome.score() : 0;
		this.maxScore = outcome.maxScore();
		this.gradingStatus = outcome.status();
		this.gradingMethod = outcome.method();
		this.gradingNote = outcome.note();
		this.aiCalledCount = outcome.aiCalledCount();
		this.aiJudgedCount = outcome.aiJudgedCount();
		this.aiStrengths = outcome.strengths();
		this.aiMissing = outcome.missing();
		this.aiImprovements = outcome.improvements();
		this.aiPractical = outcome.practical();
		this.rubricScores.clear();
		this.rubricScores.addAll(outcome.rubricScores());
	}

	/**
	 * 채점 결과 (엔티티에 담기 전 값). score는 GRADED일 때만 점수에 들어간다.
	 */
	public record GradeOutcome(GradingStatus status, GradingMethod method, String note, int score, int maxScore,
			int aiCalledCount, int aiJudgedCount, String strengths, String missing, String improvements,
			String practical, List<RubricScore> rubricScores) {

		public static GradeOutcome code(boolean correct) {
			return new GradeOutcome(GradingStatus.GRADED, GradingMethod.CODE, null, correct ? 1 : 0, 1, 0, 0, null,
					null, null, null, List.of());
		}

		public static GradeOutcome matched(boolean correct) {
			return new GradeOutcome(GradingStatus.GRADED, GradingMethod.MATCH, null, correct ? 1 : 0, 1, 0, 0, null,
					null, null, null, List.of());
		}

		public static GradeOutcome pending(String note, int maxScore, int aiCalledCount) {
			return new GradeOutcome(GradingStatus.PENDING, GradingMethod.AI, note, 0, maxScore, aiCalledCount, 0, null,
					null, null, null, List.of());
		}
	}
}
