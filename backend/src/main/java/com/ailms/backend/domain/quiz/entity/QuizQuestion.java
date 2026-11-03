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
 * 문제 하나. 유형마다 정답 정보가 다르다.
 * <ul>
 * <li>객관식: correctChoiceIndex(보기 순서, 0부터)</li>
 * <li>주관식: acceptedAnswers(허용 답 여러 개)</li>
 * <li>서술형: rubric(채점 기준·배점)과 modelAnswer(참고 모범 답안)</li>
 * </ul>
 * 정답 정보는 제출 뒤 결과에만 내보낸다. 정오와 점수는 AI가 아니라 QuizGrader·채점 코드가 정한다.
 * aiExplanation·modelAnswer는 AI가 쓴 글이다.
 */
@Entity
@Table(name = "quiz_question")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizQuestion {

	public static final int CONTENT_MAX_LENGTH = 1000;
	public static final int CHOICE_MAX_LENGTH = 500;
	public static final int EXPLANATION_MAX_LENGTH = 2000;
	public static final int SCENARIO_MAX_LENGTH = 2000;
	public static final int WORK_ROLE_MAX_LENGTH = 200;
	public static final int ANSWER_GUIDE_MAX_LENGTH = 500;
	public static final int MODEL_ANSWER_MAX_LENGTH = 4000;
	public static final int ACCEPTED_ANSWER_MAX_LENGTH = 100;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long quizId;

	@Column(nullable = false)
	private int sortOrder;

	@Enumerated(EnumType.STRING)
	@Column(name = "question_type", nullable = false, length = 20)
	private QuestionType type = QuestionType.MULTIPLE_CHOICE;

	@Column(nullable = false, length = CONTENT_MAX_LENGTH)
	private String content;

	@ElementCollection
	@CollectionTable(name = "quiz_choice", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "sort_order")
	@Column(name = "content", nullable = false, length = CHOICE_MAX_LENGTH)
	private List<String> choices = new ArrayList<>();

	// 객관식만 있다
	private Integer correctChoiceIndex;

	@Column(length = EXPLANATION_MAX_LENGTH)
	private String aiExplanation;

	// 업무 활용 문제의 상황·사용자 역할
	@Column(length = SCENARIO_MAX_LENGTH)
	private String scenario;

	@Column(length = WORK_ROLE_MAX_LENGTH)
	private String workRole;

	// 주관식·서술형 답변 작성 안내
	@Column(length = ANSWER_GUIDE_MAX_LENGTH)
	private String answerGuide;

	@Column(length = MODEL_ANSWER_MAX_LENGTH)
	private String modelAnswer;

	@ElementCollection
	@CollectionTable(name = "quiz_question_accepted_answer", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "sort_order")
	@Column(name = "content", nullable = false, length = ACCEPTED_ANSWER_MAX_LENGTH)
	private List<String> acceptedAnswers = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "quiz_question_rubric", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "sort_order")
	private List<QuizRubricItem> rubric = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "quiz_question_concept", joinColumns = @JoinColumn(name = "question_id"))
	@OrderColumn(name = "sort_order")
	private List<QuizConcept> concepts = new ArrayList<>();

	// 객관식 문제 (V6부터 쓰던 형태)
	public static QuizQuestion create(Long quizId, int sortOrder, String content, List<String> choices,
			int correctChoiceIndex, String aiExplanation, List<QuizConcept> concepts) {
		if (correctChoiceIndex < 0 || correctChoiceIndex >= choices.size()) {
			throw new IllegalArgumentException("정답 번호가 보기 범위를 벗어났어요.");
		}
		QuizQuestion question = base(quizId, sortOrder, QuestionType.MULTIPLE_CHOICE, content, aiExplanation,
				concepts);
		question.choices.addAll(choices);
		question.correctChoiceIndex = correctChoiceIndex;
		return question;
	}

	public static QuizQuestion shortAnswer(Long quizId, int sortOrder, String content, List<String> acceptedAnswers,
			String answerGuide, String aiExplanation, List<QuizConcept> concepts) {
		if (acceptedAnswers.isEmpty()) {
			throw new IllegalArgumentException("주관식에는 허용 답이 하나 이상 있어야 해요.");
		}
		QuizQuestion question = base(quizId, sortOrder, QuestionType.SHORT_ANSWER, content, aiExplanation, concepts);
		question.acceptedAnswers.addAll(acceptedAnswers);
		question.answerGuide = answerGuide;
		return question;
	}

	public static QuizQuestion essay(Long quizId, int sortOrder, String content, List<QuizRubricItem> rubric,
			String modelAnswer, String answerGuide, String aiExplanation, List<QuizConcept> concepts) {
		if (rubric.isEmpty()) {
			throw new IllegalArgumentException("서술형에는 채점 기준이 하나 이상 있어야 해요.");
		}
		QuizQuestion question = base(quizId, sortOrder, QuestionType.ESSAY, content, aiExplanation, concepts);
		question.rubric.addAll(rubric);
		question.modelAnswer = modelAnswer;
		question.answerGuide = answerGuide;
		return question;
	}

	// 업무 활용 문제의 상황·역할 (어느 유형에나 붙을 수 있다)
	public QuizQuestion withWorkContext(String scenario, String workRole) {
		this.scenario = scenario;
		this.workRole = workRole;
		return this;
	}

	// 객관식·주관식 1점, 서술형은 배점 합
	public int maxScore() {
		if (type == QuestionType.ESSAY) {
			return rubric.stream().mapToInt(QuizRubricItem::getPoints).sum();
		}
		return 1;
	}

	private static QuizQuestion base(Long quizId, int sortOrder, QuestionType type, String content,
			String aiExplanation, List<QuizConcept> concepts) {
		QuizQuestion question = new QuizQuestion();
		question.quizId = quizId;
		question.sortOrder = sortOrder;
		question.type = type;
		question.content = content;
		question.aiExplanation = aiExplanation;
		question.concepts.addAll(concepts);
		return question;
	}
}
