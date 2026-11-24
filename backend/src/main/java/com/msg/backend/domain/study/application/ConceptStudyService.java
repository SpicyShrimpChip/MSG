package com.msg.backend.domain.study.application;

import com.msg.backend.domain.material.dao.MaterialConceptRepository;
import com.msg.backend.domain.material.dao.MaterialRepository;
import com.msg.backend.domain.material.entity.Material;
import com.msg.backend.domain.material.entity.MaterialConcept;
import com.msg.backend.domain.quiz.application.QuizAssessor;
import com.msg.backend.domain.quiz.application.QuizProperties;
import com.msg.backend.domain.quiz.dao.QuizAnswerRepository;
import com.msg.backend.domain.quiz.dao.QuizQuestionRepository;
import com.msg.backend.domain.quiz.dao.QuizRepository;
import com.msg.backend.domain.quiz.dao.QuizSubmissionRepository;
import com.msg.backend.domain.quiz.entity.Quiz;
import com.msg.backend.domain.quiz.entity.QuizAnswer;
import com.msg.backend.domain.quiz.entity.QuizQuestion;
import com.msg.backend.domain.quiz.entity.QuizSubmission;
import com.msg.backend.domain.study.dao.ConceptStudyProgressRepository;
import com.msg.backend.domain.study.dto.ConceptStudyResponse;
import com.msg.backend.domain.study.entity.ConceptStudyProgress;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개념별 학습 기록과 확인 퀴즈 결과를 합쳐 학습 상태를 만든다. 학습 완료는 concept_study_progress에, 확인 퀴즈 결과는 퀴즈 기록
 * (quiz.check_concept_key + 제출·답)에 따로 있고 복사하지 않는다(다시 채점하면 바로 반영, 지난 결과는 이력으로 남음).
 *
 * <p>학습 완료는 본인 자료의 뽑아 둔 개념에만 남길 수 있다. 같은 개념을 여러 번 보내도 기록은 하나이고(사용자 행 잠금 + 유니크 키),
 * "복습 필요" 상태에서 다시 완료할 때만 최근 학습 시각과 횟수가 바뀐다. 학습 완료는 AI 분석·퀴즈 결과·숙련도를 바꾸지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ConceptStudyService {

	static final int HISTORY_LIMIT = 5;
	static final String NO_COURSE_MESSAGE = "과목에 연결된 자료만 학습 기록을 남길 수 있어요.";

	private final UserRepository userRepository;
	private final MaterialRepository materialRepository;
	private final MaterialConceptRepository conceptRepository;
	private final ConceptStudyProgressRepository progressRepository;
	private final QuizRepository quizRepository;
	private final QuizSubmissionRepository submissionRepository;
	private final QuizQuestionRepository questionRepository;
	private final QuizAnswerRepository answerRepository;
	private final QuizProperties properties;
	private final Clock clock;

	/** 자료의 핵심 개념마다 학습 상태 (뽑은 개념 순서) */
	@Transactional(readOnly = true)
	public List<ConceptStudyResponse> listForMaterial(Long userId, Long materialId) {
		Material material = materialRepository.getOwned(materialId, userId);
		Map<String, ConceptStudyProgress> progress = progressRepository.findAllByUserIdAndMaterialId(userId, materialId)
				.stream().collect(Collectors.toMap(ConceptStudyProgress::getConceptKey, Function.identity()));
		Map<String, Checks> checks = checks(userId,
				quizRepository.findAllByUserIdAndMaterialIdAndCheckConceptKeyIsNotNullOrderByIdDesc(userId, materialId));
		return conceptRepository.findAllByMaterialIdAndUserIdOrderBySortOrderAsc(materialId, userId).stream()
				.map(concept -> view(material.getId(), material.getCourseId(), concept.getConceptKey(),
						concept.getName(), progress.get(concept.getConceptKey()),
						checks.get(key(materialId, concept.getConceptKey()))))
				.toList();
	}

	/**
	 * 과목의 자료 개념마다 학습 상태 (커리큘럼용). 키는 {@link #key(Long, String)}. 소유 확인은 부르는 쪽이 한다.
	 */
	@Transactional(readOnly = true)
	public Map<String, ConceptStudyResponse> forCourse(Long userId, Long courseId, List<MaterialConcept> concepts) {
		Map<String, ConceptStudyProgress> progress = progressRepository.findAllByUserIdAndCourseId(userId, courseId)
				.stream().collect(Collectors.toMap(p -> key(p.getMaterialId(), p.getConceptKey()), Function.identity()));
		Map<String, Checks> checks = checks(userId,
				quizRepository.findAllByUserIdAndCourseIdAndCheckConceptKeyIsNotNullOrderByIdDesc(userId, courseId));
		Map<String, ConceptStudyResponse> result = new HashMap<>();
		for (MaterialConcept concept : concepts) {
			String key = key(concept.getMaterialId(), concept.getConceptKey());
			result.put(key, view(concept.getMaterialId(), courseId, concept.getConceptKey(), concept.getName(),
					progress.get(key), checks.get(key)));
		}
		return result;
	}

	/**
	 * 학습 완료. 처음이면 기록을 만들고, 지금 "복습 필요"면 다시 공부한 것으로 최근 학습 시각·횟수를 바꾼다. 그 밖(이미 완료·이해 확인·
	 * 추가 확인 필요)에는 아무것도 바꾸지 않는다(중복 클릭 안전). 남의 자료·없는 개념은 404.
	 */
	@Transactional
	public ConceptStudyResponse complete(Long userId, Long materialId, String conceptId) {
		// 같은 사용자의 동시 요청을 차례로 처리한다 (중복 행 방지, 유니크 키가 한 번 더 막는다)
		userRepository.lockLoginUser(userId);
		Material material = materialRepository.getOwned(materialId, userId);
		if (material.getCourseId() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NO_COURSE_MESSAGE);
		}
		String conceptKey = conceptId.strip();
		MaterialConcept concept = conceptRepository.getOwned(materialId, userId, conceptKey);
		Checks checks = checks(userId,
				quizRepository.findAllByUserIdAndMaterialIdAndCheckConceptKeyIsNotNullOrderByIdDesc(userId, materialId))
				.get(key(materialId, conceptKey));
		// DB 열(TIMESTAMP(6))과 같은 마이크로초로 맞춘다. Linux JDK는 나노초까지 주므로, 맞추지 않으면 저장 직후 응답과
		// 다시 읽은 값이 달라진다
		LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
		ConceptStudyProgress progress = progressRepository
				.findByUserIdAndMaterialIdAndConceptKey(userId, materialId, conceptKey).orElse(null);
		if (progress == null) {
			progress = progressRepository.save(ConceptStudyProgress.complete(userId, material.getCourseId(), materialId,
					conceptKey, concept.getName(), now));
		} else if (status(progress, checks) == ConceptStudyRules.Status.REVIEW_NEEDED) {
			progress.restudy(now);
		}
		return view(materialId, material.getCourseId(), conceptKey, concept.getName(), progress, checks);
	}

	public static String key(Long materialId, String conceptKey) {
		return materialId + ":" + conceptKey;
	}

	// ---------- 확인 퀴즈 결과 ----------

	private record Attempt(ConceptStudyResponse.CheckAttempt response, ConceptStudyRules.CheckResult result) {
	}

	private record Checks(List<Attempt> attempts, Long openQuizId) {

		Attempt latest() {
			return attempts.isEmpty() ? null : attempts.get(0);
		}
	}

	// 확인 퀴즈마다 가장 최근 제출을 그 개념의 문제만으로 센다. 채점이 끝나지 않은 답은 대기로 세고 틀린 답으로 세지 않는다.
	// 제출·문제·답은 퀴즈마다 따로 읽지 않고 한 번에 읽는다 (QuizStatsService.answered와 같은 방식)
	private Map<String, Checks> checks(Long userId, List<Quiz> quizzes) {
		double minRate = properties.assessment().conceptMinRate();
		Map<String, List<Attempt>> attempts = new HashMap<>();
		Map<String, Long> open = new HashMap<>();
		Map<Long, QuizSubmission> latest = latestSubmissions(userId, quizzes);
		Map<Long, List<QuizQuestion>> questionsByQuiz = questionsByQuiz(latest.keySet());
		Map<Long, List<QuizAnswer>> answersBySubmission = latest.isEmpty() ? Map.of()
				: answerRepository.findAllBySubmissionIdIn(latest.values().stream().map(QuizSubmission::getId).toList())
						.stream().collect(Collectors.groupingBy(QuizAnswer::getSubmissionId));
		for (Quiz quiz : quizzes) {
			String key = key(quiz.getMaterialId(), quiz.getCheckConceptKey());
			QuizSubmission submission = latest.get(quiz.getId());
			if (submission == null) {
				// 최신 순이라 처음 만나는 안 푼 퀴즈가, 그보다 나중에 낸 결과가 없을 때만 "이어 풀기" 대상이다
				if (!attempts.containsKey(key)) {
					open.putIfAbsent(key, quiz.getId());
				}
				continue;
			}
			List<QuizAssessor.Graded> graded = QuizAssessor.graded(
					questionsByQuiz.getOrDefault(quiz.getId(), List.of()),
					QuizAssessor.answersByQuestion(answersBySubmission.getOrDefault(submission.getId(), List.of())));
			QuizAssessor.ConceptStat stat = QuizAssessor.byConcept(graded, minRate).stream()
					.filter(s -> s.conceptKey().equals(quiz.getCheckConceptKey())).findFirst().orElse(null);
			ConceptStudyRules.CheckResult result = stat == null
					? new ConceptStudyRules.CheckResult(submission.getSubmittedAt(), 0, 0, 0, 0)
					: new ConceptStudyRules.CheckResult(submission.getSubmittedAt(), stat.gradedCount(), stat.score(),
							stat.maxScore(), stat.pendingCount() + stat.reviewCount());
			ConceptStudyRules.Status judged = ConceptStudyRules.judge(result, minRate, minGraded());
			attempts.computeIfAbsent(key, k -> new ArrayList<>()).add(new Attempt(
					new ConceptStudyResponse.CheckAttempt(quiz.getId(), result.submittedAt(), result.gradedCount(),
							result.score(), result.maxScore(), result.rate(), result.waitingCount(), judged.apiValue()),
					result));
		}
		Map<String, Checks> checks = new HashMap<>();
		attempts.forEach((key, list) -> {
			list.sort(Comparator.comparing((Attempt a) -> a.result().submittedAt()).reversed());
			checks.put(key, new Checks(list, open.get(key)));
		});
		open.forEach((key, quizId) -> checks.putIfAbsent(key, new Checks(List.of(), quizId)));
		return checks;
	}

	// 퀴즈마다 가장 최근 제출(가장 큰 id). 퀴즈 id → 제출, 안 푼 퀴즈는 없음
	private Map<Long, QuizSubmission> latestSubmissions(Long userId, List<Quiz> quizzes) {
		if (quizzes.isEmpty()) {
			return Map.of();
		}
		return submissionRepository.findLatestByQuizIds(userId, quizzes.stream().map(Quiz::getId).toList()).stream()
				.collect(Collectors.toMap(QuizSubmission::getQuizId, Function.identity(),
						BinaryOperator.maxBy(Comparator.comparing(QuizSubmission::getId))));
	}

	// 퀴즈마다 문제 목록 (문제 순서대로, findAllByQuizIdOrderBySortOrderAsc와 같은 순서)
	private Map<Long, List<QuizQuestion>> questionsByQuiz(Collection<Long> quizIds) {
		if (quizIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, QuizQuestion> byId = new LinkedHashMap<>();
		questionRepository.findAllByQuizIdIn(quizIds).forEach(question -> byId.putIfAbsent(question.getId(), question));
		return byId.values().stream()
				.sorted(Comparator.comparingInt(QuizQuestion::getSortOrder))
				.collect(Collectors.groupingBy(QuizQuestion::getQuizId));
	}

	private int minGraded() {
		return properties.assessment().confirmMinGraded();
	}

	private ConceptStudyRules.Status status(ConceptStudyProgress progress, Checks checks) {
		Attempt latest = checks == null ? null : checks.latest();
		return ConceptStudyRules.resolve(progress == null ? null : progress.getLastStudiedAt(),
				latest == null ? null : latest.result(), properties.assessment().conceptMinRate(), minGraded());
	}

	private ConceptStudyResponse view(Long materialId, Long courseId, String conceptKey, String name,
			ConceptStudyProgress progress, Checks checks) {
		ConceptStudyRules.Status status = status(progress, checks);
		Attempt latest = checks == null ? null : checks.latest();
		int studyCount = progress == null ? 0 : progress.getStudyCount();
		return new ConceptStudyResponse(materialId, courseId, conceptKey, name, status.apiValue(), progress != null,
				progress == null ? null : progress.getFirstCompletedAt(),
				progress == null ? null : progress.getLastStudiedAt(), studyCount,
				latest == null ? null : latest.response(),
				checks == null ? List.of()
						: checks.attempts().stream().limit(HISTORY_LIMIT).map(Attempt::response).toList(),
				checks == null ? null : checks.openQuizId(),
				ConceptStudyRules.reason(status, latest == null ? null : latest.result(),
						properties.assessment().conceptMinRate(), minGraded(), studyCount));
	}
}
