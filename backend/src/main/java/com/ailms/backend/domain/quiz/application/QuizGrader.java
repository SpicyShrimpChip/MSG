package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.quiz.dto.QuizAnswerRequest;
import com.ailms.backend.domain.quiz.entity.QuestionType;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.global.error.BusinessException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;

/**
 * 제출한 답을 검사하고 객관식을 채점한다. 객관식 정오는 저장된 정답 번호와 고른 번호가 같은지로만 정한다(AI는 관여하지 않는다).
 * 답하지 않은 문제는 -1(틀림)로 채운다. 이 퀴즈에 없는 문제, 같은 문제를 두 번 보낸 답, 범위 밖 보기 번호,
 * 객관식이 아닌 문제에 보기 번호를 보낸 답은 거절한다. 주관식·서술형 채점은 AnswerGradingService가 한다.
 */
public final class QuizGrader {

	public static final int NO_ANSWER = -1;
	static final String INVALID_ANSWER_MESSAGE = "제출한 답을 확인할 수 없어요. 퀴즈를 다시 열어 주세요.";

	private QuizGrader() {
	}

	// correct는 객관식만 의미가 있다 (주관식·서술형은 false)
	public record Graded(QuizQuestion question, int choiceIndex, boolean correct) {
	}

	// 문제 하나에 낸 답. 객관식이 아니면 choiceIndex는 -1, 답을 안 쓴 문제는 text가 null
	public record Submitted(QuizQuestion question, int choiceIndex, String text) {

		public boolean isBlank() {
			return question.getType() == QuestionType.MULTIPLE_CHOICE ? choiceIndex == NO_ANSWER
					: text == null || text.isBlank();
		}

		public boolean multipleChoiceCorrect() {
			return question.getType() == QuestionType.MULTIPLE_CHOICE
					&& Objects.equals(question.getCorrectChoiceIndex(), choiceIndex);
		}
	}

	// 문제 순서대로 돌려준다 (객관식 채점 포함)
	public static List<Graded> grade(List<QuizQuestion> questions, List<QuizAnswerRequest> answers) {
		return collect(questions, answers).stream()
				.map(s -> new Graded(s.question(), s.choiceIndex(), s.multipleChoiceCorrect()))
				.toList();
	}

	// 답을 검사해 문제 순서대로 모은다
	public static List<Submitted> collect(List<QuizQuestion> questions, List<QuizAnswerRequest> answers) {
		Map<Long, QuizQuestion> byId = new HashMap<>();
		for (QuizQuestion question : questions) {
			byId.put(question.getId(), question);
		}
		Map<Long, QuizAnswerRequest> chosen = new HashMap<>();
		for (QuizAnswerRequest answer : answers) {
			QuizQuestion question = byId.get(answer.questionId());
			if (question == null || chosen.putIfAbsent(answer.questionId(), answer) != null || !valid(question, answer)) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_ANSWER_MESSAGE);
			}
		}
		List<Submitted> submitted = new ArrayList<>();
		for (QuizQuestion question : questions) {
			QuizAnswerRequest answer = chosen.get(question.getId());
			if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
				int choiceIndex = answer == null || answer.choiceIndex() == null ? NO_ANSWER : answer.choiceIndex();
				submitted.add(new Submitted(question, choiceIndex, null));
			} else {
				String text = answer == null || answer.text() == null ? null : answer.text().strip();
				submitted.add(new Submitted(question, NO_ANSWER, text == null || text.isEmpty() ? null : text));
			}
		}
		return submitted;
	}

	private static boolean valid(QuizQuestion question, QuizAnswerRequest answer) {
		Integer choiceIndex = answer.choiceIndex();
		if (question.getType() != QuestionType.MULTIPLE_CHOICE) {
			return choiceIndex == null || choiceIndex == NO_ANSWER;
		}
		return choiceIndex == null || choiceIndex == NO_ANSWER
				|| (choiceIndex >= 0 && choiceIndex < question.getChoices().size());
	}
}
