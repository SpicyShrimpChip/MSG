package com.ailms.backend.domain.study.application;

import com.ailms.backend.domain.material.dao.MaterialConceptRepository;
import com.ailms.backend.domain.material.dao.MaterialRepository;
import com.ailms.backend.domain.material.entity.Material;
import com.ailms.backend.domain.material.entity.MaterialConcept;
import com.ailms.backend.domain.quiz.application.QuizAssessor;
import com.ailms.backend.domain.quiz.application.QuizProperties;
import com.ailms.backend.domain.quiz.application.QuizStatsService;
import com.ailms.backend.domain.quiz.dao.QuizAnswerRepository;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.dao.QuizSubmissionRepository;
import com.ailms.backend.domain.quiz.entity.GradingStatus;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizAnswer;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.quiz.entity.QuizSubmission;
import com.ailms.backend.domain.study.dao.ConceptStudyProgressRepository;
import com.ailms.backend.domain.study.dto.ConceptStudyResponse;
import com.ailms.backend.domain.study.entity.ConceptStudyProgress;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.global.error.BusinessException;
import com.ailms.backend.global.security.ApiErrorResponder;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
	static final String MATERIAL_NOT_FOUND_MESSAGE = "자료를 찾을 수 없어요.";
	static final String NO_COURSE_MESSAGE = "과목에 연결된 자료만 학습 기록을 남길 수 있어요.";
	static final String CONCEPT_NOT_FOUND_MESSAGE = "이 자료에서 그 개념을 찾을 수 없어요. 핵심 개념을 먼저 뽑아 주세요.";

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
		Material material = findMaterial(userId, materialId);
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
		userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, ApiErrorResponder.UNAUTHORIZED_MESSAGE));
		Material material = findMaterial(userId, materialId);
		if (material.getCourseId() == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, NO_COURSE_MESSAGE);
		}
		String conceptKey = conceptId.strip();
		MaterialConcept concept = conceptRepository.findAllByMaterialIdAndUserIdOrderBySortOrderAsc(materialId, userId)
				.stream().filter(c -> c.getConceptKey().equals(conceptKey)).findFirst()
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, CONCEPT_NOT_FOUND_MESSAGE));
		Checks checks = checks(userId,
				quizRepository.findAllByUserIdAndMaterialIdAndCheckConceptKeyIsNotNullOrderByIdDesc(userId, materialId))
				.get(key(materialId, conceptKey));
		LocalDateTime now = LocalDateTime.now(clock);
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

	// 확인 퀴즈마다 가장 최근 제출을 그 개념의 문제만으로 센다. 채점이 끝나지 않은 답은 대기로 세고 틀린 답으로 세지 않는다
	private Map<String, Checks> checks(Long userId, List<Quiz> quizzes) {
		double minRate = properties.assessment().conceptMinRate();
		Map<String, List<Attempt>> attempts = new HashMap<>();
		Map<String, Long> open = new HashMap<>();
		for (Quiz quiz : quizzes) {
			String key = key(quiz.getMaterialId(), quiz.getCheckConceptKey());
			QuizSubmission submission = submissionRepository.findFirstByQuizIdAndUserIdOrderByIdDesc(quiz.getId(), userId)
					.orElse(null);
			if (submission == null) {
				// 최신 순이라 처음 만나는 안 푼 퀴즈가, 그보다 나중에 낸 결과가 없을 때만 "이어 풀기" 대상이다
				if (!attempts.containsKey(key)) {
					open.putIfAbsent(key, quiz.getId());
				}
				continue;
			}
			Map<Long, QuizAnswer> answers = answerRepository.findAllBySubmissionId(submission.getId()).stream()
					.collect(Collectors.toMap(QuizAnswer::getQuestionId, Function.identity(), (a, b) -> a));
			List<QuizAssessor.Graded> graded = new ArrayList<>();
			for (QuizQuestion question : questionRepository.findAllByQuizIdOrderBySortOrderAsc(quiz.getId())) {
				QuizAnswer answer = answers.get(question.getId());
				graded.add(answer == null
						? new QuizAssessor.Graded(question, GradingStatus.GRADED, 0, question.maxScore(), null)
						: new QuizAssessor.Graded(question, answer.getGradingStatus(), answer.getScore(),
								answer.getMaxScore(), answer.getGradingNote()));
			}
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

	private static int minGraded() {
		return QuizStatsService.CONFIRM_MIN_GRADED;
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

	private Material findMaterial(Long userId, Long materialId) {
		return materialRepository.findByIdAndUserId(materialId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, MATERIAL_NOT_FOUND_MESSAGE));
	}
}
