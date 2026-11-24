package com.msg.backend.domain.analysis.application;

import com.msg.backend.domain.analysis.dao.AnalysisModelResultRepository;
import com.msg.backend.domain.analysis.dao.AnalysisRunRepository;
import com.msg.backend.domain.analysis.dao.ConceptConsensusRepository;
import com.msg.backend.domain.analysis.dao.ConceptFeedbackRepository;
import com.msg.backend.domain.analysis.dao.ConceptJudgmentRepository;
import com.msg.backend.domain.analysis.dao.JudgmentEvidenceRepository;
import com.msg.backend.domain.analysis.dto.AnalysisRunResponse;
import com.msg.backend.domain.analysis.dto.FeedbackResponse;
import com.msg.backend.domain.analysis.dto.LatestAnalysisResponse;
import com.msg.backend.domain.analysis.entity.AnalysisModelResult;
import com.msg.backend.domain.analysis.entity.AnalysisRun;
import com.msg.backend.domain.analysis.entity.AnalysisRunStatus;
import com.msg.backend.domain.analysis.entity.ConceptConsensus;
import com.msg.backend.domain.analysis.entity.ConceptFeedback;
import com.msg.backend.domain.analysis.entity.ConceptJudgment;
import com.msg.backend.domain.analysis.entity.ConsensusOutcome;
import com.msg.backend.domain.analysis.entity.FeedbackType;
import com.msg.backend.domain.analysis.entity.JudgmentEvidence;
import com.msg.backend.domain.analysis.entity.ModelResultStatus;
import com.msg.backend.domain.analysis.entity.UnderstandingStatus;
import com.msg.backend.domain.conversation.dao.ConversationMessageRepository;
import com.msg.backend.domain.conversation.dao.ConversationRepository;
import com.msg.backend.domain.conversation.entity.Conversation;
import com.msg.backend.domain.conversation.entity.ConversationMessage;
import com.msg.backend.domain.conversation.entity.MessageRole;
import com.msg.backend.domain.course.dao.CourseRepository;
import com.msg.backend.domain.quiz.application.QuizStatsService;
import com.msg.backend.domain.quiz.dto.ConceptQuizStatsResponse;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.llm.LlmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장한 분석 결과를 화면용으로 묶는다. 모델별 판단·근거, 개념별 비교, 사용자 피드백, 추천 순서를 함께 준다.
 * 근거 메시지는 사용자 본인의 대화라 앞부분(120자)을 함께 보여 준다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisQueryService {

	static final String RUN_NOT_FOUND_MESSAGE = "분석 결과를 찾을 수 없어요.";
	static final String NO_RUN_MESSAGE = "아직 이 과목을 분석한 결과가 없어요.";
	static final String UNFINISHED_MESSAGE = "분석이 제시간에 끝나지 않아 결과를 받지 못했어요.";
	// 모델이 하나 이상 판단한 분석만 화면 결과로 쓴다. 모두 실패한 분석(FAILED)은 이전 결과를 덮지 않는다(#209)
	private static final Set<AnalysisRunStatus> JUDGED = Set.of(AnalysisRunStatus.COMPLETED, AnalysisRunStatus.PARTIAL);

	private final AnalysisRunRepository runRepository;
	private final AnalysisModelResultRepository modelResultRepository;
	private final ConceptJudgmentRepository judgmentRepository;
	private final JudgmentEvidenceRepository evidenceRepository;
	private final ConceptConsensusRepository consensusRepository;
	private final ConceptFeedbackRepository feedbackRepository;
	private final ConversationRepository conversationRepository;
	private final ConversationMessageRepository messageRepository;
	private final CourseRepository courseRepository;
	private final QuizStatsService quizStatsService;
	private final MasteryCalculator masteryCalculator;
	private final AnalysisProperties properties;
	private final Clock clock;

	public AnalysisRunResponse getRun(Long userId, Long runId) {
		return toResponse(runRepository.findByIdAndUserId(runId, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, RUN_NOT_FOUND_MESSAGE)));
	}

	// 과목의 최근 분석: 모델이 하나 이상 판단한 가장 최근 분석과, 그 뒤에 시작한 분석이 진행 중·결과 없음이면 그 시도.
	// 둘 다 없으면(한 번도 분석하지 않음) 404
	public LatestAnalysisResponse getLatest(Long userId, Long courseId) {
		courseRepository.checkOwned(courseId, userId);
		AnalysisRun judged = runRepository.findFirstByUserIdAndCourseIdAndStatusInOrderByIdDesc(userId, courseId, JUDGED)
				.orElse(null);
		LatestAnalysisResponse.LastAttempt attempt = runRepository
				.findFirstByUserIdAndCourseIdOrderByIdDesc(userId, courseId)
				.filter(newest -> judged == null || !newest.getId().equals(judged.getId()))
				.map(this::lastAttempt)
				.orElse(null);
		if (judged == null && attempt == null) {
			throw new BusinessException(HttpStatus.NOT_FOUND, NO_RUN_MESSAGE);
		}
		return new LatestAnalysisResponse(judged == null ? null : toResponse(judged), attempt);
	}

	// 판단한 모델이 없는 시도. 시작한 지 runningTimeout이 지나도 RUNNING이면(서버가 중간에 멈춤) 끝나지 못한 것으로 본다
	private LatestAnalysisResponse.LastAttempt lastAttempt(AnalysisRun run) {
		if (run.getStatus() == AnalysisRunStatus.RUNNING) {
			boolean active = run.getCreatedAt().isAfter(LocalDateTime.now(clock).minus(properties.runningTimeout()));
			return new LatestAnalysisResponse.LastAttempt(run.getId(), active ? "running" : "failed",
					run.getCreatedAt(), null, active ? null : UNFINISHED_MESSAGE);
		}
		return new LatestAnalysisResponse.LastAttempt(run.getId(), "failed", run.getCreatedAt(),
				run.getCompletedAt(), failedMessage(run));
	}

	// 모두 실패한 이유. 부른 모델의 이유가 모두 같으면 그 이유를 덧붙인다 (예: 모두 호출 한도 초과)
	private String failedMessage(AnalysisRun run) {
		List<AnalysisModelResult> called = modelResultRepository.findAllByRunId(run.getId()).stream()
				.filter(result -> result.getStatus() != ModelResultStatus.NOT_CONFIGURED)
				.toList();
		if (called.isEmpty()) {
			return "분석 결과를 받지 못했어요.";
		}
		String summary = called.size() == 1 ? "연결된 모델이 결과를 주지 못했어요."
				: "연결된 모델 " + called.size() + "개가 모두 결과를 주지 못했어요.";
		List<String> reasons = called.stream().map(AnalysisQueryService::message).distinct().toList();
		return reasons.size() == 1 ? summary + " " + reasons.get(0) : summary;
	}

	private AnalysisRunResponse toResponse(AnalysisRun run) {
		// GPT → Claude → Gemini (LlmProvider 순서. DB는 글자 순으로 정렬하므로 여기서 다시 정렬한다)
		List<AnalysisModelResult> results = modelResultRepository.findAllByRunId(run.getId()).stream()
				.sorted(Comparator.comparing(AnalysisModelResult::getProvider))
				.toList();
		Map<Long, AnalysisModelResult> resultById = results.stream()
				.collect(Collectors.toMap(AnalysisModelResult::getId, Function.identity()));
		List<ConceptJudgment> judgments = resultById.isEmpty() ? List.of()
				: judgmentRepository.findAllByModelResultIdIn(resultById.keySet());
		Map<Long, List<JudgmentEvidence>> evidenceByJudgment = (judgments.isEmpty() ? List.<JudgmentEvidence>of()
				: evidenceRepository.findAllByJudgmentIdInOrderByIdAsc(
						judgments.stream().map(ConceptJudgment::getId).toList()))
				.stream().collect(Collectors.groupingBy(JudgmentEvidence::getJudgmentId));
		Map<Long, ConversationMessage> messages = messageRepository.findAllById(evidenceByJudgment.values().stream()
						.flatMap(List::stream).map(JudgmentEvidence::getMessageId).collect(Collectors.toSet()))
				.stream().collect(Collectors.toMap(ConversationMessage::getId, Function.identity()));
		Map<Long, Conversation> conversations = conversationRepository.findAllById(messages.values().stream()
						.map(ConversationMessage::getConversationId).collect(Collectors.toSet()))
				.stream().collect(Collectors.toMap(Conversation::getId, Function.identity()));
		Map<String, ConceptFeedback> feedback = feedbackRepository
				.findAllByUserIdAndCourseIdOrderByIdAsc(run.getUserId(), run.getCourseId()).stream()
				.collect(Collectors.toMap(ConceptFeedback::getConceptKey, Function.identity()));

		List<AnalysisRunResponse.ModelResult> models = results.stream()
				.map(result -> new AnalysisRunResponse.ModelResult(result.getProvider().apiValue(), result.getModel(),
						result.getStatus().apiValue(), message(result),
						(int) judgments.stream().filter(j -> j.getModelResultId().equals(result.getId())).count(),
						result.getDroppedEvidenceCount()))
				.toList();

		// 퀴즈 결과는 대화 분석과 따로 센 값이라 판단·추천 순서를 바꾸지 않고 나란히 보여 준다
		Map<String, ConceptQuizStatsResponse> quizStats = courseRepository.findById(run.getCourseId())
				.map(course -> quizStatsService.byCourse(run.getUserId(), course))
				.orElse(List.of()).stream()
				.filter(stat -> stat.questionCount() > 0)
				.collect(Collectors.toMap(ConceptQuizStatsResponse::conceptId, Function.identity(), (a, b) -> a));
		List<ConceptConsensus> consensus = consensusRepository.findAllByRunIdOrderByIdAsc(run.getId());
		List<ConceptConsensus> ordered = new ArrayList<>(consensus);
		ordered.sort(Comparator
				.comparing((ConceptConsensus c) -> wantsToStudy(feedback.get(c.getConceptKey())) ? 0 : 1)
				.thenComparing(AnalysisQueryService::rank));
		List<AnalysisRunResponse.ConceptResult> concepts = new ArrayList<>();
		for (int i = 0; i < ordered.size(); i++) {
			ConceptConsensus item = ordered.get(i);
			ConceptFeedback mine = feedback.get(item.getConceptKey());
			List<AnalysisRunResponse.Judgment> modelJudgments = judgments.stream()
					.filter(judgment -> judgment.getConceptKey().equals(item.getConceptKey()))
					.sorted(Comparator.comparing(judgment -> resultById.get(judgment.getModelResultId()).getProvider()))
					.map(judgment -> toJudgment(judgment, resultById.get(judgment.getModelResultId()),
							evidenceByJudgment.getOrDefault(judgment.getId(), List.of()), messages, conversations))
					.toList();
			concepts.add(new AnalysisRunResponse.ConceptResult(item.getConceptName(),
					new AnalysisRunResponse.Comparison(item.getOutcome().apiValue(),
							item.getStatus() == null ? null : item.getStatus().apiValue(), item.getJudgedCount(),
							item.getAgreeCount(), item.getCalledCount()),
					modelJudgments, mine == null ? null : FeedbackResponse.from(mine), i + 1,
					mine != null && mine.getFeedback() == FeedbackType.ALREADY_KNOW,
					quizStats.get(item.getConceptKey()),
					mastery(item, modelJudgments, quizStats.get(item.getConceptKey()))));
		}
		return new AnalysisRunResponse(run.getId(), run.getCourseId(), run.getStatus().apiValue(),
				run.getPromptVersion(), run.getCreatedAt(), run.getCompletedAt(),
				new AnalysisRunResponse.Input(run.getConversationCount(), run.getMessageCount(),
						run.getTruncatedMessageCount()),
				models, concepts);
	}

	// 숙련도: 퀴즈(채점이 끝난 문제)와 대화(사용자가 쓴 근거 메시지가 있는 대화 수 + 모델 비교가 일치한 상태)를 규칙으로 계산한다.
	// 대화 수는 모델이 여럿이어도 같은 대화는 한 번만 센다(모델이 같은 판단을 해도 근거가 늘지 않는다).
	// TODO(미결): 대화 신호를 메시지 단위로 세는 방법은 주미와 정하기 (지금은 개념 단위 판단을 대화 수만큼 센다)
	private AnalysisRunResponse.Mastery mastery(ConceptConsensus item,
			List<AnalysisRunResponse.Judgment> modelJudgments, ConceptQuizStatsResponse quiz) {
		int conversationCount = (int) modelJudgments.stream()
				.flatMap(judgment -> judgment.evidence().stream())
				.filter(evidence -> evidence.kind() != null && evidence.kind().startsWith("user_"))
				.map(AnalysisRunResponse.Evidence::conversationId)
				.distinct().count();
		MasteryCalculator.Result result = masteryCalculator.calculate(quiz == null ? 0 : quiz.gradedCount(),
				quiz == null ? 0 : quiz.score(), quiz == null ? 0 : quiz.maxScore(), conversationCount,
				item.getStatus());
		return new AnalysisRunResponse.Mastery(result.level().apiValue(), result.score(), result.evidenceWeight(),
				result.quizGradedCount(), result.quizRate(), result.conversationCount(),
				result.conversationStatus() == null ? null : result.conversationStatus().apiValue(),
				result.formulaVersion(), result.quizNeeded(), List.of());
	}

	private static AnalysisRunResponse.Judgment toJudgment(ConceptJudgment judgment, AnalysisModelResult result,
			List<JudgmentEvidence> evidence, Map<Long, ConversationMessage> messages,
			Map<Long, Conversation> conversations) {
		return new AnalysisRunResponse.Judgment(result.getProvider().apiValue(), result.getModel(),
				judgment.getStatus().apiValue(), judgment.getReportedStatus().apiValue(), judgment.getAdjustedReason(),
				judgment.getReason(), judgment.getNeedsCheck(), judgment.getRecommendedActivity(),
				evidence.stream()
						.filter(item -> messages.containsKey(item.getMessageId()))
						.map(item -> {
							ConversationMessage message = messages.get(item.getMessageId());
							Conversation conversation = conversations.get(message.getConversationId());
							return new AnalysisRunResponse.Evidence(message.getId(), message.getConversationId(),
									conversation == null ? null : conversation.getTitle(),
									message.getRole() == MessageRole.USER ? "user" : "assistant",
									item.getKind().apiValue(), item.getNote(),
									EvidenceExcerpt.of(message.getContent()));
						})
						.toList());
	}

	private static boolean wantsToStudy(ConceptFeedback feedback) {
		return feedback != null && feedback.getFeedback() == FeedbackType.WANT_TO_STUDY;
	}

	// 추천 순서 (코드 규칙, 숙련도 점수 아님): 이해 부족 → 부분 이해 → 의견 불일치 → 근거 부족·결과 없음 → 이해
	private static int rank(ConceptConsensus consensus) {
		if (consensus.getOutcome() == ConsensusOutcome.DISAGREEMENT) {
			return 2;
		}
		UnderstandingStatus status = consensus.getStatus();
		if (status == null) {
			return 3;
		}
		return switch (status) {
			case NOT_UNDERSTOOD -> 0;
			case PARTIAL -> 1;
			case INSUFFICIENT_EVIDENCE -> 3;
			case UNDERSTOOD -> 4;
		};
	}

	private static String message(AnalysisModelResult result) {
		if (result.getStatus() == ModelResultStatus.SUCCEEDED) {
			return null;
		}
		if (result.getStatus() == ModelResultStatus.NOT_CONFIGURED) {
			return "이 모델은 연결되지 않아 부르지 않았어요.";
		}
		if (result.getStatus() == ModelResultStatus.INVALID_OUTPUT) {
			return "약속한 형식으로 답하지 않아 결과에서 뺐어요.";
		}
		LlmException.Code code;
		try {
			code = LlmException.Code.valueOf(result.getErrorCode());
		} catch (RuntimeException e) {
			return "결과를 받지 못했어요.";
		}
		return switch (code) {
			case TIMEOUT -> "응답 시간이 지나 결과를 받지 못했어요.";
			case RATE_LIMITED -> "호출 한도를 넘어 결과를 받지 못했어요.";
			case SERVER_ERROR -> "제공사 서버 오류로 결과를 받지 못했어요.";
			case AUTH_FAILED -> "API 키가 맞지 않아 부르지 못했어요.";
			case REJECTED -> "요청이 거절됐어요. 모델 이름이나 계정 상태(크레딧·권한)를 확인해 주세요.";
			case NETWORK -> "제공사에 연결하지 못했어요.";
			case BAD_RESPONSE -> "응답을 읽지 못했어요.";
			case DAILY_LIMIT -> "오늘 서비스 전체 AI 사용량이 다 차서 부르지 않았어요.";
			case USER_DAILY_LIMIT -> "오늘 쓸 수 있는 AI 사용량을 다 써서 부르지 않았어요.";
		};
	}
}
