package com.ailms.backend.domain.analysis.application;

import com.ailms.backend.domain.conversation.entity.Conversation;
import com.ailms.backend.domain.conversation.entity.ConversationMessage;
import com.ailms.backend.domain.conversation.entity.MessageRole;
import com.ailms.backend.domain.course.entity.Course;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 모델에 보낼 글을 만든다. 외부로 나가는 양을 줄이려고 다음만 보낸다.
 * <ul>
 * <li>과목 키워드가 들어간 메시지와 그 앞뒤 contextWindow개 (키워드가 제목에만 있으면 대화 전체)</li>
 * <li>메시지 하나는 maxMessageChars까지 (넘으면 뒤를 자르고 표시)</li>
 * <li>전체는 maxInputChars까지 (최근 대화부터 담고, 넘치는 대화는 뺀다)</li>
 * </ul>
 * 대화는 가져올 때 이미 브라우저에서 개인정보를 가리고 서버가 다시 검사한 글이다.
 * TODO: 공부 대화 안에 섞인 사생활 이야기(TMI)는 키워드 창 밖이면 빠지지만, 같은 메시지 안에 있으면 그대로 간다.
 */
@Component
@RequiredArgsConstructor
class AnalysisInputBuilder {

	static final String TRUNCATED_MARK = " …(뒤 생략)";

	private final AnalysisProperties properties;

	AnalysisInput build(Course course, List<Conversation> conversations, List<ConversationMessage> messages) {
		List<AnalysisInput.Concept> concepts = concepts(course);
		List<String> keys = concepts.stream().map(AnalysisInput.Concept::key).toList();
		Map<Long, List<ConversationMessage>> byConversation = new LinkedHashMap<>();
		messages.forEach(message -> byConversation.computeIfAbsent(message.getConversationId(), id -> new ArrayList<>())
				.add(message));

		StringBuilder body = new StringBuilder();
		List<Long> conversationIds = new ArrayList<>();
		Map<Long, MessageRole> sent = new LinkedHashMap<>();
		int truncated = 0;
		for (Conversation conversation : newestFirst(conversations)) {
			List<ConversationMessage> selected = select(conversation,
					byConversation.getOrDefault(conversation.getId(), List.of()), keys);
			if (selected.isEmpty()) {
				continue;
			}
			StringBuilder block = new StringBuilder();
			block.append("=== 대화: ").append(conversation.getTitle()).append(" ===\n");
			int blockTruncated = 0;
			for (ConversationMessage message : selected) {
				String text = message.getContent();
				if (text.length() > properties.maxMessageChars()) {
					text = text.substring(0, properties.maxMessageChars()) + TRUNCATED_MARK;
					blockTruncated++;
				}
				block.append("[m").append(message.getId()).append("] (")
						.append(message.getRole() == MessageRole.USER ? "사용자" : "AI").append(") ")
						.append(text).append('\n');
			}
			if (body.length() + block.length() > properties.maxInputChars()) {
				continue;
			}
			body.append(block).append('\n');
			conversationIds.add(conversation.getId());
			selected.forEach(message -> sent.put(message.getId(), message.getRole()));
			truncated += blockTruncated;
		}

		String prompt = "과목: " + course.getName() + "\n"
				+ "평가할 개념 (이 이름 그대로만 쓴다): "
				+ String.join(", ", concepts.stream().map(concept -> "\"" + concept.name() + "\"").toList()) + "\n\n"
				+ "대화 (메시지마다 [id] (말한 사람) 글):\n" + body;
		return new AnalysisInput(concepts, conversationIds, sent, truncated, prompt);
	}

	static List<AnalysisInput.Concept> concepts(Course course) {
		return course.getKeywords().stream()
				.map(keyword -> new AnalysisInput.Concept(Course.nameKey(keyword), keyword))
				.toList();
	}

	// 키워드가 들어간 메시지와 앞뒤 메시지만. 키워드가 제목에만 있으면 전체
	private List<ConversationMessage> select(Conversation conversation, List<ConversationMessage> messages,
			List<String> keys) {
		TreeSet<Integer> picked = new TreeSet<>();
		for (int i = 0; i < messages.size(); i++) {
			if (containsAny(messages.get(i).getContent(), keys)) {
				for (int j = Math.max(0, i - properties.contextWindow());
						j <= Math.min(messages.size() - 1, i + properties.contextWindow()); j++) {
					picked.add(j);
				}
			}
		}
		if (picked.isEmpty()) {
			return containsAny(conversation.getTitle(), keys) ? messages : List.of();
		}
		return picked.stream().map(messages::get).toList();
	}

	private static boolean containsAny(String text, List<String> keys) {
		String lower = text.toLowerCase(Locale.ROOT);
		return keys.stream().anyMatch(lower::contains);
	}

	// 외부 서비스에서 시작한 시각이 최근인 대화부터 (시각이 없으면 뒤로), 같으면 나중에 가져온 것부터
	private static List<Conversation> newestFirst(List<Conversation> conversations) {
		return conversations.stream()
				.sorted(Comparator.comparing(Conversation::getSourceCreatedAt,
								Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(Conversation::getId, Comparator.reverseOrder()))
				.toList();
	}
}
