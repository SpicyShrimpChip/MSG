package com.msg.backend.domain.analysis.application;

import com.msg.backend.domain.conversation.entity.Conversation;
import com.msg.backend.domain.conversation.entity.ConversationMessage;
import com.msg.backend.domain.conversation.entity.MessageRole;
import com.msg.backend.domain.course.entity.Course;
import com.msg.backend.global.common.TermMatch;
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
 * <li>과목 키워드가 들어간 메시지와 그 앞뒤 contextWindow개 (키워드가 제목에만 있으면 대화 전체).
 * 영문 키워드는 낱말 경계에서만 찾는다(TermMatch, 프론트에서 대화를 고른 규칙과 같다)</li>
 * <li>메시지 하나는 maxMessageChars까지 (넘으면 뒤를 자르고 표시)</li>
 * <li>전체는 maxInputChars까지. 최근 대화부터 담고, 뒤에 담을 대화가 남아 있으면 대화 하나는 남은 자리의 절반까지만 쓴다.
 * 대화가 자리보다 길면 통째로 빼지 않고 최근 메시지부터 메시지 단위로 담고 빠진 앞쪽 메시지 수를 표시한다</li>
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
		// 키워드마다 찾을 규칙(영문은 낱말 경계 정규식)을 한 번만 만들고 모든 메시지에 같이 쓴다
		List<TermMatch.Keyword> keywords = course.getKeywords().stream().map(TermMatch::keyword).toList();
		Map<Long, List<ConversationMessage>> byConversation = new LinkedHashMap<>();
		messages.forEach(message -> byConversation.computeIfAbsent(message.getConversationId(), id -> new ArrayList<>())
				.add(message));

		List<Selection> selections = new ArrayList<>();
		for (Conversation conversation : newestFirst(conversations)) {
			List<ConversationMessage> selected = select(conversation,
					byConversation.getOrDefault(conversation.getId(), List.of()), keywords);
			if (!selected.isEmpty()) {
				selections.add(new Selection(conversation, selected));
			}
		}

		StringBuilder body = new StringBuilder();
		List<Long> conversationIds = new ArrayList<>();
		Map<Long, MessageRole> sent = new LinkedHashMap<>();
		int truncated = 0;
		for (int i = 0; i < selections.size(); i++) {
			Selection selection = selections.get(i);
			int remaining = properties.maxInputChars() - body.length();
			// 뒤에 담을 대화가 남아 있으면 남은 자리의 절반까지만 써서, 긴 대화 하나가 다른 대화를 모두 밀어내지 않게 한다
			int budget = i < selections.size() - 1 ? remaining / 2 : remaining;
			Block block = block(selection, budget);
			if (block == null) {
				continue;
			}
			body.append(block.text()).append('\n');
			conversationIds.add(selection.conversation().getId());
			block.messages().forEach(message -> sent.put(message.getId(), message.getRole()));
			truncated += block.truncatedCount();
		}

		String prompt = "과목: " + course.getName() + "\n"
				+ "평가할 개념 (이 이름 그대로만 쓴다): "
				+ String.join(", ", concepts.stream().map(concept -> "\"" + concept.name() + "\"").toList()) + "\n\n"
				+ "대화 (메시지마다 [id] (말한 사람) 글):\n" + body;
		return new AnalysisInput(concepts, conversationIds, sent, truncated, prompt);
	}

	/**
	 * 대화 하나를 budget 글자 안에 담는다. 다 들어가지 않으면 메시지 단위로 최근 메시지부터 담고
	 * 빠진 앞쪽 메시지 수를 표시한다. 메시지 하나도 들어가지 않으면 null.
	 */
	private Block block(Selection selection, int budget) {
		String header = "=== 대화: " + selection.conversation().getTitle() + " ===\n";
		List<ConversationMessage> selected = selection.messages();
		List<String> lines = new ArrayList<>(selected.size());
		int truncatedCount = 0;
		for (ConversationMessage message : selected) {
			String text = message.getContent();
			if (text.length() > properties.maxMessageChars()) {
				text = text.substring(0, properties.maxMessageChars()) + TRUNCATED_MARK;
				truncatedCount++;
			}
			lines.add("[m" + message.getId() + "] (" + (message.getRole() == MessageRole.USER ? "사용자" : "AI") + ") "
					+ text + "\n");
		}
		int total = header.length() + lines.stream().mapToInt(String::length).sum();
		if (total <= budget) {
			return new Block(header + String.join("", lines), selected, truncatedCount);
		}

		// 최근 메시지부터 들어가는 만큼만. 빠진 메시지 수 표시 줄의 자리도 미리 뺀다
		int start = selected.size();
		int used = header.length() + omittedLine(selected.size()).length();
		while (start > 0 && used + lines.get(start - 1).length() <= budget) {
			start--;
			used += lines.get(start).length();
		}
		if (start == selected.size()) {
			return null;
		}
		List<ConversationMessage> kept = selected.subList(start, selected.size());
		int keptTruncated = (int) kept.stream()
				.filter(message -> message.getContent().length() > properties.maxMessageChars())
				.count();
		return new Block(header + omittedLine(start) + String.join("", lines.subList(start, lines.size())), kept,
				keptTruncated);
	}

	private static String omittedLine(int omitted) {
		return "(대화가 길어 앞쪽 메시지 " + omitted + "개는 보내지 않음)\n";
	}

	private record Selection(Conversation conversation, List<ConversationMessage> messages) {
	}

	private record Block(String text, List<ConversationMessage> messages, int truncatedCount) {
	}

	static List<AnalysisInput.Concept> concepts(Course course) {
		return course.getKeywords().stream()
				.map(keyword -> new AnalysisInput.Concept(Course.nameKey(keyword), keyword))
				.toList();
	}

	// 키워드가 들어간 메시지와 앞뒤 메시지만. 키워드가 제목에만 있으면 전체
	private List<ConversationMessage> select(Conversation conversation, List<ConversationMessage> messages,
			List<TermMatch.Keyword> keywords) {
		TreeSet<Integer> picked = new TreeSet<>();
		for (int i = 0; i < messages.size(); i++) {
			if (containsAny(messages.get(i).getContent(), keywords)) {
				for (int j = Math.max(0, i - properties.contextWindow());
						j <= Math.min(messages.size() - 1, i + properties.contextWindow()); j++) {
					picked.add(j);
				}
			}
		}
		if (picked.isEmpty()) {
			return containsAny(conversation.getTitle(), keywords) ? messages : List.of();
		}
		return picked.stream().map(messages::get).toList();
	}

	// 영문 키워드는 낱말 경계에서만, 한글이 든 키워드는 부분 문자열로 찾는다 (프론트 filter.ts와 같은 규칙, TermMatch)
	private static boolean containsAny(String text, List<TermMatch.Keyword> keywords) {
		String lower = text.toLowerCase(Locale.ROOT);
		return keywords.stream().anyMatch(keyword -> keyword.foundIn(lower));
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
