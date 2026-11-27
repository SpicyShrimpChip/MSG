package com.msg.backend.domain.material.application;

import com.msg.backend.domain.conversation.application.PersonalInfoDetector;
import com.msg.backend.domain.course.dao.CourseRepository;
import com.msg.backend.domain.course.entity.Course;
import com.msg.backend.domain.material.entity.Material;
import com.msg.backend.domain.quiz.application.QuizAiUsage;
import com.msg.backend.domain.quiz.application.QuizProperties;
import com.msg.backend.domain.quiz.dao.QuizAiCallRepository;
import com.msg.backend.domain.user.entity.User;
import com.msg.backend.global.error.BusinessException;
import com.msg.backend.global.llm.AiCallQuota;
import com.msg.backend.global.llm.LlmClient;
import com.msg.backend.global.llm.LlmClients;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 자료 본문을 외부 AI로 보내기 전 확인(개념 뽑기·개념 설명·요약이 함께 쓴다). 첫 오류를 돌려준다.
 *
 * <p>순서: 자료 본문 전송 기능(403) → AI 퀴즈 처리 동의(403) → 이번 요청의 확인(400) → 연결된 모델(503)
 * → 24시간 기능별 횟수·모델 요청 수(429) → 사용자 하루 몫(429). 그다음 부르는 쪽이 본문을 읽고({@link #read})
 * 보낼 글의 개인정보를 검사한다({@link #requireNoPersonalInfo}). 기능마다 다른 것(동의·모델·한도 문구, 하루 한도, 읽을 글자 수,
 * 개인정보를 검사할 글)은 인자로 받는다. 퀴즈 만들기는 본문을 못 쓰면 오류 대신 과목 개념만으로 만들어 확인 문구만 함께 쓴다.
 */
@Component
public class MaterialAiGate {

	public static final String CONSENT_MESSAGE = "자료 본문을 외부 AI로 보내려면 확인에 체크해 주세요.";
	public static final String PERSONAL_INFO_MESSAGE = "자료 본문에 이메일·전화번호 같은 개인정보가 있어 외부 AI로 보내지 않았어요.";

	/** 기능마다 다른 문구: AI 퀴즈 처리 동의 없음(403), 모델 없음(503), 24시간 한도(429) */
	public record Messages(String quizConsent, String noModel, String limit) {
	}

	private final MaterialContentProperties contentProperties;
	private final QuizProperties quizProperties;
	private final LlmClients llmClients;
	private final QuizAiCallRepository callRepository;
	private final QuizAiUsage usage;
	private final AiCallQuota aiCallQuota;
	private final MaterialTextExtractor extractor;
	private final PersonalInfoDetector personalInfoDetector;
	private final CourseRepository courseRepository;
	private final Clock clock;

	public MaterialAiGate(MaterialContentProperties contentProperties, QuizProperties quizProperties,
			LlmClients llmClients, QuizAiCallRepository callRepository, QuizAiUsage usage, AiCallQuota aiCallQuota,
			MaterialTextExtractor extractor, PersonalInfoDetector personalInfoDetector,
			CourseRepository courseRepository, Clock clock) {
		this.contentProperties = contentProperties;
		this.quizProperties = quizProperties;
		this.llmClients = llmClients;
		this.callRepository = callRepository;
		this.usage = usage;
		this.aiCallQuota = aiCallQuota;
		this.extractor = extractor;
		this.personalInfoDetector = personalInfoDetector;
		this.courseRepository = courseRepository;
		this.clock = clock;
	}

	/** "오늘 AI 호출 한도를 다 써서 지금은 {action} 수 없어요. 내일 다시 시도해 주세요." */
	public static String limitMessage(String action) {
		return "오늘 AI 호출 한도를 다 써서 지금은 " + action + " 수 없어요. 내일 다시 시도해 주세요.";
	}

	/** 보내기 전 확인(본문을 읽기 전까지). 통과하면 부를 모델(설정 순서) */
	public List<LlmClient> checkBeforeSend(User user, boolean confirmed, String callPurpose, int dailyLimit,
			Messages messages) {
		if (!contentProperties.enabled()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, MaterialContentProperties.DISABLED_MESSAGE);
		}
		if (!user.isAiQuizConsent()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, messages.quizConsent());
		}
		if (!confirmed) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, CONSENT_MESSAGE);
		}
		List<LlmClient> clients = llmClients.configuredInOrder(quizProperties.generationProviders());
		if (clients.isEmpty()) {
			throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, messages.noModel());
		}
		Long userId = user.getId();
		LocalDateTime since = LocalDateTime.now(clock).minusHours(24);
		if (callRepository.countByUserIdAndPurposeAndCreatedAtAfter(userId, callPurpose, since) >= dailyLimit
				|| usage.modelCallBudgetExhausted(userId)) {
			throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, messages.limit());
		}
		aiCallQuota.ensureRemaining(userId, 1);
		return clients;
	}

	/** 본문을 maxChars까지 읽는다. 읽지 못하면 422(data.reason = 이유) */
	public MaterialTextExtractor.Extracted read(Material material, int maxChars) {
		MaterialTextExtractor.Extracted extracted = extractor.extract(material, maxChars);
		if (extracted.status() != MaterialTextExtractor.Status.OK) {
			throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, unreadableMessage(extracted.status()),
					Map.of("reason", extracted.status().name()));
		}
		return extracted;
	}

	/** 보낼 글에 개인정보 패턴(IP 제외)이 있으면 422(data.reason = PERSONAL_INFO) */
	public void requireNoPersonalInfo(String text) {
		if (personalInfoDetector.containsPersonalInfoExceptIp(text)) {
			throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, PERSONAL_INFO_MESSAGE,
					Map.of("reason", "PERSONAL_INFO"));
		}
	}

	/** 자료가 연결된 내 과목. 연결이 없거나 찾지 못하면 null */
	public Course courseOrNull(Material material, Long userId) {
		return material.getCourseId() == null ? null
				: courseRepository.findByIdAndUserId(material.getCourseId(), userId).orElse(null);
	}

	public static String unreadableMessage(MaterialTextExtractor.Status status) {
		return switch (status) {
			case NO_TEXT -> "PDF에서 글을 찾지 못했어요. 스캔한 PDF는 글자 인식(OCR)이 필요해 아직 지원하지 않아요.";
			case ENCRYPTED -> "암호가 걸린 PDF라 본문을 읽지 못했어요. 암호를 푼 파일을 올려 주세요.";
			case EMPTY -> "자료에서 읽을 글이 없어요.";
			case NOT_SUPPORTED -> "이 형식은 본문을 읽지 못해요.";
			default -> "자료 파일을 읽지 못했어요. 손상되지 않은 파일인지 확인해 주세요.";
		};
	}
}
