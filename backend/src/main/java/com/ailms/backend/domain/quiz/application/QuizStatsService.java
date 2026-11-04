package com.ailms.backend.domain.quiz.application;

import com.ailms.backend.domain.analysis.dao.ConceptFeedbackRepository;
import com.ailms.backend.domain.analysis.entity.ConceptFeedback;
import com.ailms.backend.domain.analysis.entity.FeedbackType;
import com.ailms.backend.domain.course.entity.Course;
import com.ailms.backend.domain.quiz.dao.QuizAnswerRepository;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizSubmissionRepository;
import com.ailms.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 과목의 개념별 퀴즈 결과를 모은다 (코드 집계). 퀴즈마다 가장 최근 제출만 쓴다.
 * "이미 알아요"를 남긴 개념은 그 뒤에 푼 문제로 확인했는지(confirmation)를 코드 규칙으로 정한다.
 * 피드백을 남겼다고 결과가 바뀌지 않는다. 숙련도 점수는 미결이라 만들지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizStatsService {

	// 확인 퀴즈로 "확인됨"이라고 말하려면 이만큼은 채점이 끝나야 한다
	static final int CONFIRM_MIN_GRADED = 2;

	private final QuizSubmissionRepository submissionRepository;
	private final QuizAnswerRepository answerRepository;
	private final QuizQuestionRepository questionRepository;
	private final ConceptFeedbackRepository feedbackRepository;
	private final QuizProperties properties;

	// 과목 키워드 순서 → 퀴즈에만 있는 개념 순서. 소유 확인은 부르는 쪽이 한다
	public List<ConceptQuizStatsResponse> byCourse(Long userId, Course course) {
		List<QuizSubmission> submissions = submissionRepository.findLatestInCourse(userId, course.getId());
		List<Answered> answered = answered(submissions);
		Map<String, ConceptFeedback> feedback = feedbackRepository
				.findAllByUserIdAndCourseIdOrderByIdAsc(userId, course.getId()).stream()
				.collect(Collectors.toMap(ConceptFeedback::getConceptKey, Function.identity(), (a, b) -> b));
		double minRate = properties.assessment().conceptMinRate();
		Map<String, QuizAssessor.ConceptStat> stats = QuizAssessor
				.byConcept(answered.stream().map(Answered::graded).toList(), minRate).stream()
				.collect(Collectors.toMap(QuizAssessor.ConceptStat::conceptKey, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));
		Map<String, String> names = new LinkedHashMap<>();
		course.getKeywords().forEach(keyword -> names.putIfAbsent(Course.nameKey(keyword), keyword));
		stats.values().forEach(stat -> names.putIfAbsent(stat.conceptKey(), stat.name()));
		List<ConceptQuizStatsResponse> result = new ArrayList<>();
		names.forEach((key, name) -> {
			QuizAssessor.ConceptStat stat = stats.get(key);
			ConceptFeedback mine = feedback.get(key);
			result.add(new ConceptQuizStatsResponse(key, name, stat == null ? 0 : stat.questionCount(),
					stat == null ? 0 : stat.gradedCount(), stat == null ? 0 : stat.correctCount(),
					stat == null ? 0 : stat.score(),
					stat == null ? 0 : stat.maxScore(), stat == null ? 0 : stat.pendingCount(),
					stat == null ? 0 : stat.reviewCount(), stat != null && stat.weak(),
					mine == null ? null : mine.getFeedback().apiValue(),
					mine == null || mine.getFeedback() != FeedbackType.ALREADY_KNOW ? null
							: confirmation(key, mine.getUpdatedAt(), answered, minRate),
					answered.stream()
							.filter(a -> a.graded().question().getConcepts().stream()
									.anyMatch(c -> c.getConceptKey().equals(key)))
							.map(Answered::submittedAt)
							.max(LocalDateTime::compareTo)
							.orElse(null)));
		});
		return result;
	}

	/**
	 * 일반 퀴즈(사전·사후평가 제외)의 개념별 결과. since가 있으면 그 뒤에 낸 제출만 센다(사전평가 뒤의 개념 확인 퀴즈).
	 * 퀴즈마다 가장 최근 제출만, 채점이 끝난 답만 점수에 넣는다. 소유 확인은 부르는 쪽이 한다.
	 */
	public Map<String, QuizAssessor.ConceptStat> practiceByConcept(Long userId, Long courseId, LocalDateTime since) {
		List<QuizSubmission> submissions = submissionRepository.findLatestPracticeInCourse(userId, courseId).stream()
				.filter(s -> since == null || !s.getSubmittedAt().isBefore(since))
				.toList();
		return QuizAssessor
				.byConcept(answered(submissions).stream().map(Answered::graded).toList(),
						properties.assessment().conceptMinRate())
				.stream()
				.collect(Collectors.toMap(QuizAssessor.ConceptStat::conceptKey, Function.identity(), (a, b) -> a,
						LinkedHashMap::new));
	}

	private record Answered(QuizAssessor.Graded graded, LocalDateTime submittedAt) {
	}

	private List<Answered> answered(List<QuizSubmission> submissions) {
		if (submissions.isEmpty()) {
			return List.of();
		}
		Map<Long, QuizSubmission> byId = submissions.stream()
				.collect(Collectors.toMap(QuizSubmission::getId, Function.identity()));
		Map<Long, QuizQuestion> questions = questionRepository
				.findAllByQuizIdIn(submissions.stream().map(QuizSubmission::getQuizId).toList()).stream()
				.collect(Collectors.toMap(QuizQuestion::getId, Function.identity(), (a, b) -> a));
		List<Answered> answered = new ArrayList<>();
		for (QuizAnswer answer : answerRepository.findAllBySubmissionIdIn(byId.keySet())) {
			QuizQuestion question = questions.get(answer.getQuestionId());
			if (question != null) {
				answered.add(new Answered(new QuizAssessor.Graded(question, answer.getGradingStatus(), answer.getScore(),
						answer.getMaxScore(), answer.getGradingNote()), byId.get(answer.getSubmissionId()).getSubmittedAt()));
			}
		}
		return answered;
	}

	// 피드백을 남긴(또는 바꾼) 뒤에 낸 답만 본다
	private static String confirmation(String conceptKey, LocalDateTime since, List<Answered> answered,
			double minRate) {
		List<QuizAssessor.Graded> after = answered.stream()
				.filter(a -> !a.submittedAt().isBefore(since))
				.map(Answered::graded)
				.toList();
		QuizAssessor.ConceptStat stat = QuizAssessor.byConcept(after, minRate).stream()
				.filter(s -> s.conceptKey().equals(conceptKey))
				.findFirst()
				.orElse(null);
		if (stat == null) {
			return "not_taken";
		}
		if (stat.gradedCount() < CONFIRM_MIN_GRADED || stat.pendingCount() > 0 || stat.reviewCount() > 0) {
			return "needs_check";
		}
		return stat.weak() ? "not_confirmed" : "confirmed";
	}
}
