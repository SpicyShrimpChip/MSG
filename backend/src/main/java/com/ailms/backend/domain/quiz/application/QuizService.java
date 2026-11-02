package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.quiz.application.QuizGrader.Graded;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dao.QuizAnswerRepository;
import com.ailms.backend.domain.quiz.dao.QuizSubmissionRepository;
import com.ailms.backend.domain.quiz.dto.QuizResponse;
import com.ailms.backend.domain.quiz.dto.QuizResultResponse;
import com.ailms.backend.domain.quiz.dto.QuizSubmitRequest;
import com.ailms.backend.domain.quiz.dto.QuizSummaryResponse;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import com.ailms.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 퀴즈 풀기와 채점. 사용자 id는 항상 토큰의 값만 받고, 남의 퀴즈는 없는 퀴즈와 같게 404로 응답한다.
 * 풀기 전 응답에는 정답이 없고, 채점은 QuizGrader가 한다. 같은 퀴즈를 여러 번 풀면 제출 기록이 쌓인다.
 * 퀴즈 만들기(AI 생성)는 아직 없다. TODO: 자료로 문제를 만드는 기능을 global/llm 클라이언트로 붙인다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

	static final String NOT_FOUND_MESSAGE = "퀴즈를 찾을 수 없어요.";

	private final QuizRepository quizRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizSubmissionRepository submissionRepository;
	private final QuizAnswerRepository answerRepository;
	private final Clock clock;

	// 최근에 만든 퀴즈부터
	public List<QuizSummaryResponse> getQuizzes(Long userId) {
		List<Quiz> quizzes = quizRepository.findAllByUserIdOrderByIdDesc(userId);
		if (quizzes.isEmpty()) {
			return List.of();
		}
		List<Long> ids = quizzes.stream().map(Quiz::getId).toList();
		Map<Long, Integer> questionCounts = new HashMap<>();
		for (Object[] row : questionRepository.countByQuizIds(ids)) {
			questionCounts.put((Long) row[0], ((Number) row[1]).intValue());
		}
		Map<Long, QuizSubmission> latest = submissionRepository.findLatestByQuizIds(userId, ids).stream()
				.collect(Collectors.toMap(QuizSubmission::getQuizId, Function.identity()));
		return quizzes.stream()
				.map(quiz -> QuizSummaryResponse.of(quiz, questionCounts.getOrDefault(quiz.getId(), 0),
						latest.get(quiz.getId())))
				.toList();
	}

	public QuizResponse getQuiz(Long userId, Long id) {
		Quiz quiz = find(userId, id);
		return QuizResponse.of(quiz, questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId()));
	}

	@Transactional
	public QuizResultResponse submit(Long userId, Long id, QuizSubmitRequest request) {
		Quiz quiz = find(userId, id);
		List<QuizQuestion> questions = questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId());
		List<Graded> graded = QuizGrader.grade(questions, request.answers());
		int correctCount = (int) graded.stream().filter(Graded::correct).count();
		QuizSubmission submission = submissionRepository.save(
				QuizSubmission.of(quiz.getId(), userId, correctCount, graded.size(), LocalDateTime.now(clock)));
		answerRepository.saveAll(graded.stream()
				.map(g -> QuizAnswer.of(submission.getId(), g.question().getId(), g.choiceIndex(),
						g.correct()))
				.toList());
		return QuizResultResponse.of(submission, graded);
	}

	// 아직 안 풀었으면 null
	public QuizResultResponse getLatestResult(Long userId, Long id) {
		Quiz quiz = find(userId, id);
		QuizSubmission submission = submissionRepository.findFirstByQuizIdAndUserIdOrderByIdDesc(quiz.getId(), userId)
				.orElse(null);
		if (submission == null) {
			return null;
		}
		Map<Long, QuizAnswer> answers = answerRepository.findAllBySubmissionId(submission.getId()).stream()
				.collect(Collectors.toMap(QuizAnswer::getQuestionId, Function.identity()));
		List<Graded> graded = questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId()).stream()
				.map(question -> {
					QuizAnswer answer = answers.get(question.getId());
					return answer == null
							? new Graded(question, QuizGrader.NO_ANSWER, false)
							: new Graded(question, answer.getChoiceIndex(), answer.isCorrect());
				})
				.toList();
		return QuizResultResponse.of(submission, graded);
	}

	private Quiz find(Long userId, Long id) {
		return quizRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
	}
}
