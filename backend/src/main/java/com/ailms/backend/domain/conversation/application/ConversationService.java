package com.ailms.backend.domain.conversation.application;

import com.ailms.backend.domain.conversation.dao.ConversationImportRepository;
import com.ailms.backend.domain.conversation.dao.ConversationMessageRepository;
import com.ailms.backend.domain.conversation.dao.ConversationPartRepository;
import com.ailms.backend.domain.conversation.dao.ConversationRepository;
import com.ailms.backend.domain.conversation.dao.ReceivedImportRequestRepository;
import com.ailms.backend.domain.conversation.dto.ConversationImportRequest;
import com.ailms.backend.domain.conversation.dto.ConversationImportResponse;
import com.ailms.backend.domain.conversation.dto.ConversationRejectedResponse;
import com.ailms.backend.domain.conversation.dto.ConversationSummaryResponse;
import com.ailms.backend.domain.conversation.dto.ImportedConversationRequest;
import com.ailms.backend.domain.conversation.dto.ImportedMessageRequest;
import com.ailms.backend.domain.conversation.dto.RejectedConversation;
import com.ailms.backend.domain.conversation.entity.Conversation;
import com.ailms.backend.domain.conversation.entity.ConversationImport;
import com.ailms.backend.domain.conversation.entity.ConversationMessage;
import com.ailms.backend.domain.conversation.entity.ConversationPart;
import com.ailms.backend.domain.conversation.entity.ConversationSource;
import com.ailms.backend.domain.conversation.entity.MessageRole;
import com.ailms.backend.domain.conversation.entity.ReceivedImportRequest;
import com.ailms.backend.domain.course.dao.CourseRepository;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.domain.user.entity.User;
import com.ailms.backend.global.error.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 AI 대화 가져오기. 사용자 id는 토큰의 값만 받고, 남의 과목·대화는 없는 것과 같게 404로 응답한다.
 *
 * <p>저장 규칙 (docs/frontend-requests.md "대화 가져오기"):
 * <ul>
 * <li>요청 하나는 모두 저장하거나 하나도 저장하지 않는다. 개인정보가 남은 대화가 하나라도 있으면 422와 그 목록을 돌려준다.</li>
 * <li>요청마다 가져오기 작업 id와 번호가 붙어 있고, 작업의 요청을 모두 받으면 작업이 완료된다.
 * 앞서 성공한 요청의 대화는 지우지 않지만, 작업이 완료되기 전에는 분석에 쓰지 않는다
 * ({@link ConversationRepository#findAnalyzable}).</li>
 * <li>이미 받은 요청을 다시 받으면 저장하지 않고 같은 결과를 돌려준다. 이미 저장한 대화·조각도 다시 저장하지 않는다.</li>
 * <li>같은 대화를 다른 과목으로 다시 보내도 처음 과목을 유지한다.</li>
 * </ul>
 * 대화 본문은 로그에 남기지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

	static final String NOT_FOUND_MESSAGE = "대화를 찾을 수 없어요.";
	static final String COURSE_NOT_FOUND_MESSAGE = "과목을 찾을 수 없어요.";
	static final String CONSENT_REQUIRED_MESSAGE = "AI 대화 분석에 동의해야 대화를 가져올 수 있어요.";
	static final String REJECTED_MESSAGE = "저장할 수 없는 대화가 있어 이번에 보낸 대화를 저장하지 않았어요.";
	static final String PERSONAL_INFO_REASON =
			"개인정보로 보이는 글이 남아 있어 저장하지 않았어요. 가린 내용을 다시 확인해 주세요.";
	static final String PART_MISMATCH_REASON =
			"앞서 보낸 같은 대화와 나뉜 방식이 달라 저장하지 않았어요. 가져온 대화를 지운 뒤 다시 보내 주세요.";
	private static final String INVALID_IMPORT_MESSAGE = "가져오기 작업 정보가 올바르지 않아요.";
	private static final String INVALID_CONVERSATION_MESSAGE = "대화 정보가 올바르지 않아요.";

	private final ConversationRepository conversationRepository;
	private final ConversationPartRepository partRepository;
	private final ConversationMessageRepository messageRepository;
	private final ConversationImportRepository importRepository;
	private final ReceivedImportRequestRepository receivedRequestRepository;
	private final CourseRepository courseRepository;
	private final UserRepository userRepository;
	private final PersonalInfoDetector personalInfoDetector;
	private final Clock clock;

	// 요청 안의 대화(조각) 하나를 저장 단위로 정리한 것. 조각 없이 온 대화는 1/1 조각이다
	private record Incoming(ImportedConversationRequest request, ConversationSource source, String key,
			int partIndex, int partCount) {
	}

	public List<ConversationSummaryResponse> getConversations(Long userId) {
		List<Conversation> conversations = conversationRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId);
		Map<Long, ConversationImport> imports = importRepository
				.findAllById(conversations.stream().map(Conversation::getImportId).collect(Collectors.toSet()))
				.stream()
				.collect(Collectors.toMap(ConversationImport::getId, Function.identity()));
		return conversations.stream()
				.map(conversation -> new ConversationSummaryResponse(conversation.getId(), conversation.getCourseId(),
						conversation.getSource().apiValue(), conversation.getTitle(),
						conversation.getSourceCreatedAt(), conversation.getCreatedAt(),
						conversation.getMessageCount(),
						conversation.isComplete() && imports.get(conversation.getImportId()).isCompleted()))
				.toList();
	}

	// 메시지·조각은 conversation을 외래 키(ON DELETE CASCADE)로 가지므로 함께 지워진다
	@Transactional
	public void delete(Long userId, Long id) {
		Conversation conversation = conversationRepository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE));
		conversationRepository.delete(conversation);
	}

	/**
	 * 사용자가 가져온 대화와 가져오기 기록을 모두 지운다 (AI 대화 분석 동의 철회).
	 * 동의 변경과 같은 트랜잭션에서만 부른다 (철회가 롤백되면 대화도 그대로 남는다).
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void deleteAllOfUser(Long userId) {
		conversationRepository.deleteAllByUserId(userId);
		importRepository.deleteAllByUserId(userId);
	}

	@Transactional
	public ConversationImportResponse importConversations(Long userId, ConversationImportRequest request) {
		// 사용자 행을 잠가 같은 사용자의 동의 철회와 차례로 처리한다 (UserRepository.findByIdForUpdate)
		User user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "로그인이 필요해요. 다시 로그인해 주세요."));
		if (!user.isAiAnalysisConsent()) {
			throw new BusinessException(HttpStatus.FORBIDDEN, CONSENT_REQUIRED_MESSAGE);
		}
		if (request.requestIndex() > request.requestCount()) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_IMPORT_MESSAGE);
		}
		if (!courseRepository.existsByIdAndUserId(request.courseId(), userId)) {
			throw new BusinessException(HttpStatus.NOT_FOUND, COURSE_NOT_FOUND_MESSAGE);
		}
		List<Incoming> incoming = incoming(request.conversations());
		LocalDateTime now = LocalDateTime.now(clock);

		ConversationImport job = importRepository.findByUserIdAndClientImportId(userId, request.importId())
				.orElse(null);
		if (job != null) {
			if (job.getRequestCount() != request.requestCount()) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_IMPORT_MESSAGE);
			}
			// 이미 받은 요청: 다시 저장하지 않고 같은 결과를 돌려준다 (응답을 받지 못해 다시 보낸 경우)
			var received = receivedRequestRepository.findByImportIdAndRequestIndex(job.getId(), request.requestIndex());
			if (received.isPresent()) {
				return new ConversationImportResponse(received.get().getConversationCount(), job.isCompleted());
			}
		}

		Map<String, Conversation> existing = existing(userId, incoming);
		Map<Long, Set<Integer>> receivedParts = receivedParts(existing.values());
		List<RejectedConversation> rejected = reject(incoming, existing);
		if (!rejected.isEmpty()) {
			throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, REJECTED_MESSAGE,
					new ConversationRejectedResponse(rejected));
		}

		if (job == null) {
			job = importRepository.save(ConversationImport.start(userId, request.importId(), request.requestCount(), now));
		}
		Map<Long, ConversationImport> previousImports = previousImports(existing.values());
		for (Incoming item : incoming) {
			save(userId, request.courseId(), job, item, existing, receivedParts, previousImports, now);
		}
		receivedRequestRepository.save(
				ReceivedImportRequest.of(job.getId(), request.requestIndex(), incoming.size(), now));
		receivedRequestRepository.flush();
		job.markReceived(receivedRequestRepository.countByImportId(job.getId()), now);
		conversationRepository.flush();
		return new ConversationImportResponse(incoming.size(), job.isCompleted());
	}

	private void save(Long userId, Long courseId, ConversationImport job, Incoming item,
			Map<String, Conversation> existing, Map<Long, Set<Integer>> receivedParts,
			Map<Long, ConversationImport> previousImports, LocalDateTime now) {
		ImportedConversationRequest request = item.request();
		Conversation conversation = existing.get(mapKey(item.source(), item.key()));
		if (conversation == null) {
			conversation = conversationRepository.save(Conversation.start(userId, courseId, job.getId(), item.source(),
					item.key(), request.title(), toLocal(request.createdAt()), item.partCount(), now));
			existing.put(mapKey(item.source(), item.key()), conversation);
		}
		Set<Integer> parts = receivedParts.computeIfAbsent(conversation.getId(), id -> new HashSet<>());
		// 이미 받은 조각이거나, 이미 다 받은 대화를 다르게 나눠 다시 보낸 것 (다 받지 못한 대화는 reject에서 걸렀다).
		// 앞서 보낸 작업이 끝나지 않았다면 이번 작업으로 옮겨, 이번 작업이 끝나면 분석에 쓰게 한다
		if (parts.contains(item.partIndex()) || conversation.getPartCount() != item.partCount()) {
			ConversationImport previous = previousImports.get(conversation.getImportId());
			if (previous != null && !previous.isCompleted() && !previous.getId().equals(job.getId())) {
				conversation.moveTo(job.getId(), now);
			}
			return;
		}
		List<ImportedMessageRequest> messages = request.messages();
		List<ConversationMessage> rows = new ArrayList<>(messages.size());
		for (int seq = 0; seq < messages.size(); seq++) {
			ImportedMessageRequest message = messages.get(seq);
			rows.add(ConversationMessage.of(conversation.getId(), item.partIndex(), seq,
					MessageRole.fromApiValue(message.role()), message.text(), message.isContinued()));
		}
		messageRepository.saveAll(rows);
		partRepository.save(ConversationPart.of(conversation.getId(), item.partIndex(), messages.size(), now));
		parts.add(item.partIndex());
		conversation.addPart(job.getId(), messages.size(), now);
	}

	// 형식 검사(DTO) 다음 단계: 조각 번호가 맞는지, 같은 대화(조각)가 한 요청에 두 번 들어 있지 않은지
	private static List<Incoming> incoming(List<ImportedConversationRequest> conversations) {
		List<Incoming> result = new ArrayList<>(conversations.size());
		Set<String> seen = new HashSet<>();
		Map<String, Integer> partCounts = new HashMap<>();
		for (ImportedConversationRequest conversation : conversations) {
			ConversationSource source = ConversationSource.fromApiValue(conversation.source())
					.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST, INVALID_CONVERSATION_MESSAGE));
			String key = conversation.part() == null ? conversation.clientId() : conversation.part().parentClientId();
			int partIndex = conversation.part() == null ? 1 : conversation.part().index();
			int partCount = conversation.part() == null ? 1 : conversation.part().count();
			Integer knownCount = partCounts.putIfAbsent(mapKey(source, key), partCount);
			if (partIndex > partCount || (knownCount != null && knownCount != partCount)
					|| !seen.add(mapKey(source, key) + "\n" + partIndex)) {
				throw new BusinessException(HttpStatus.BAD_REQUEST, INVALID_CONVERSATION_MESSAGE);
			}
			result.add(new Incoming(conversation, source, key, partIndex, partCount));
		}
		return result;
	}

	// 저장하지 않을 대화: 개인정보가 남은 대화, 앞서 받은 같은 대화와 조각 수가 다른 대화
	private List<RejectedConversation> reject(List<Incoming> incoming, Map<String, Conversation> existing) {
		List<RejectedConversation> rejected = new ArrayList<>();
		for (Incoming item : incoming) {
			if (containsPersonalInfo(item.request())) {
				rejected.add(new RejectedConversation(item.request().clientId(), PERSONAL_INFO_REASON));
				continue;
			}
			Conversation saved = existing.get(mapKey(item.source(), item.key()));
			if (saved != null && !saved.isComplete() && saved.getPartCount() != item.partCount()) {
				rejected.add(new RejectedConversation(item.request().clientId(), PART_MISMATCH_REASON));
			}
		}
		return rejected;
	}

	// 나눠 보낸 긴 메시지(continued)는 이어 붙인 글로 검사한다 (경계에 걸친 개인정보도 찾게)
	private boolean containsPersonalInfo(ImportedConversationRequest conversation) {
		if (personalInfoDetector.containsPersonalInfo(conversation.title())) {
			return true;
		}
		StringBuilder text = new StringBuilder();
		for (ImportedMessageRequest message : conversation.messages()) {
			text.append(message.text());
			if (!message.isContinued()) {
				if (personalInfoDetector.containsPersonalInfo(text.toString())) {
					return true;
				}
				text.setLength(0);
			}
		}
		return personalInfoDetector.containsPersonalInfo(text.toString());
	}

	private Map<String, Conversation> existing(Long userId, List<Incoming> incoming) {
		Map<ConversationSource, Set<String>> keysBySource = incoming.stream()
				.collect(Collectors.groupingBy(Incoming::source, Collectors.mapping(Incoming::key, Collectors.toSet())));
		Map<String, Conversation> existing = new HashMap<>();
		keysBySource.forEach((source, keys) -> conversationRepository
				.findAllByUserIdAndSourceAndClientIdIn(userId, source, keys)
				.forEach(conversation -> existing.put(mapKey(source, conversation.getClientId()), conversation)));
		return existing;
	}

	private Map<Long, Set<Integer>> receivedParts(Iterable<Conversation> conversations) {
		Set<Long> ids = new HashSet<>();
		conversations.forEach(conversation -> ids.add(conversation.getId()));
		Map<Long, Set<Integer>> parts = new HashMap<>();
		if (ids.isEmpty()) {
			return parts;
		}
		partRepository.findAllByConversationIdIn(ids).forEach(part -> parts
				.computeIfAbsent(part.getConversationId(), id -> new HashSet<>())
				.add(part.getPartIndex()));
		return parts;
	}

	private Map<Long, ConversationImport> previousImports(Iterable<Conversation> conversations) {
		Set<Long> ids = new HashSet<>();
		conversations.forEach(conversation -> ids.add(conversation.getImportId()));
		return importRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(ConversationImport::getId, Function.identity()));
	}

	private static String mapKey(ConversationSource source, String key) {
		return source.name() + "\n" + key;
	}

	// 외부 서비스의 시각(ISO, 보통 UTC)을 서버 시간대의 시각으로 바꿔 저장한다 (다른 표의 시각과 같은 기준)
	private LocalDateTime toLocal(OffsetDateTime time) {
		return time == null ? null : LocalDateTime.ofInstant(time.toInstant(), clock.getZone());
	}
}
