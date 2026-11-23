package com.msg.backend.domain.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.msg.backend.domain.conversation.entity.Conversation;
import com.msg.backend.domain.conversation.entity.ConversationMessage;
import com.msg.backend.domain.conversation.entity.ConversationSource;
import com.msg.backend.domain.conversation.entity.MessageRole;
import com.msg.backend.domain.course.entity.Course;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AnalysisInputBuilderTest {

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);
	private static final int MAX_INPUT = 30_000;
	private static final int MAX_MESSAGE = 1_500;

	private final AnalysisInputBuilder builder = new AnalysisInputBuilder(
			new AnalysisProperties(MAX_INPUT, MAX_MESSAGE, 1, 4000, 10, Duration.ofMinutes(10),
					Duration.ofSeconds(150)));
	private final Course course = Course.create(1L, "정보보호", "2026-2", List.of("해시"), NOW);
	private long nextMessageId = 1;

	@Test
	void 짧은_대화는_모두_보낸다() {
		Conversation conversation = conversation(1L, NOW.minusDays(1));
		List<ConversationMessage> messages = studyMessages(conversation, 3, 100);

		AnalysisInput input = builder.build(course, List.of(conversation), messages);

		assertThat(input.conversationIds()).containsExactly(1L);
		assertThat(input.sentMessages()).hasSize(6);
		assertThat(input.truncatedMessageCount()).isZero();
		assertThat(input.userPrompt()).doesNotContain("보내지 않음");
	}

	@Test
	void 상한보다_긴_대화_하나는_빼지_않고_최근_메시지부터_담는다() {
		Conversation conversation = conversation(1L, NOW.minusDays(1));
		// 메시지 50개, AI 답은 잘려도 1,500자 → 합치면 3만 자를 넘는다
		List<ConversationMessage> messages = studyMessages(conversation, 25, 2_000);

		AnalysisInput input = builder.build(course, List.of(conversation), messages);

		assertThat(input.conversationIds()).containsExactly(1L);
		assertThat(input.sentMessages()).isNotEmpty().hasSizeLessThan(messages.size());
		// 마지막 메시지는 반드시 담긴다
		assertThat(input.sentMessages()).containsKey(messages.get(messages.size() - 1).getId());
		assertThat(input.sentMessages()).doesNotContainKey(messages.get(0).getId());
		int omitted = messages.size() - input.sentMessages().size();
		assertThat(input.userPrompt()).contains("앞쪽 메시지 " + omitted + "개는 보내지 않음");
		assertThat(input.userPrompt().length()).isLessThan(MAX_INPUT + 500);
		// 잘린 메시지 수는 실제로 보낸 메시지만 센다
		long sentLong = messages.stream()
				.filter(message -> input.sentMessages().containsKey(message.getId()))
				.filter(message -> message.getContent().length() > MAX_MESSAGE)
				.count();
		assertThat(input.truncatedMessageCount()).isEqualTo((int) sentLong);
	}

	@Test
	void 최근의_긴_대화가_이전_대화를_밀어내지_않는다() {
		Conversation recentLong = conversation(1L, NOW.minusDays(1));
		Conversation olderShort = conversation(2L, NOW.minusDays(5));
		List<ConversationMessage> messages = new ArrayList<>(studyMessages(recentLong, 25, 2_000));
		messages.addAll(studyMessages(olderShort, 2, 200));

		AnalysisInput input = builder.build(course, List.of(recentLong, olderShort), messages);

		assertThat(input.conversationIds()).containsExactly(1L, 2L);
		messages.stream()
				.filter(message -> message.getConversationId().equals(2L))
				.forEach(message -> assertThat(input.sentMessages()).containsKey(message.getId()));
		assertThat(input.userPrompt().length()).isLessThan(MAX_INPUT + 500);
	}

	@Test
	void 키워드가_없는_대화는_보내지_않는다() {
		Conversation conversation = conversation(1L, NOW.minusDays(1));
		List<ConversationMessage> messages = List.of(
				message(conversation, MessageRole.USER, "오늘 점심 뭐 먹지"),
				message(conversation, MessageRole.ASSISTANT, "김치찌개 어때요"));

		AnalysisInput input = builder.build(course, List.of(conversation), messages);

		assertThat(input.conversationIds()).isEmpty();
		assertThat(input.sentMessages()).isEmpty();
	}

	private Conversation conversation(Long id, LocalDateTime sourceCreatedAt) {
		Conversation conversation = Conversation.start(1L, 1L, 1L, ConversationSource.CHATGPT, "c" + id,
				"공부 " + id, sourceCreatedAt, 1, NOW);
		ReflectionTestUtils.setField(conversation, "id", id);
		return conversation;
	}

	// 질문(키워드 포함)과 answerLength 글자 AI 답을 pairs번 주고받는 대화
	private List<ConversationMessage> studyMessages(Conversation conversation, int pairs, int answerLength) {
		List<ConversationMessage> messages = new ArrayList<>();
		for (int i = 0; i < pairs; i++) {
			messages.add(message(conversation, MessageRole.USER, "해시 충돌 질문 " + i));
			messages.add(message(conversation, MessageRole.ASSISTANT, "해시 설명 ".repeat(answerLength / 6 + 1)
					.substring(0, answerLength)));
		}
		return messages;
	}

	private ConversationMessage message(Conversation conversation, MessageRole role, String content) {
		ConversationMessage message = ConversationMessage.of(conversation.getId(), 1, (int) nextMessageId, role,
				content, false);
		ReflectionTestUtils.setField(message, "id", nextMessageId++);
		return message;
	}
}
