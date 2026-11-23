package com.msg.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.backend.global.llm.LlmResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// JSON 요청 본문의 글자 값에서 NUL 문자만 지우고, 다른 글자와 읽는 방법은 그대로 두는지 확인한다 (#215)
class NulStrippingStringDeserializerTest {

	private final ObjectMapper objectMapper = new ObjectMapper()
			.registerModule(new NulStrippingConfig().nulStrippingModule());

	record Message(String role, String text) {
	}

	record Request(String title, List<String> keywords, String[] tags, List<Message> messages,
			Map<String, String> extra) {
	}

	@Test
	void 레코드_목록_배열_맵_값_안의_NUL을_지운다() throws Exception {
		Request request = objectMapper.readValue("""
				{"title":"교착\\u0000 상태","keywords":["프로\\u0000세스"],"tags":["a\\u0000b"],
				 "messages":[{"role":"user","text":"\\u0000\\u0000실행 결과"}],"extra":{"k":"v\\u0000"}}
				""", Request.class);

		assertThat(request.title()).isEqualTo("교착 상태");
		assertThat(request.keywords()).containsExactly("프로세스");
		assertThat(request.tags()).containsExactly("ab");
		assertThat(request.messages().get(0).text()).isEqualTo("실행 결과");
		assertThat(request.extra()).containsEntry("k", "v");
	}

	@Test
	void 줄바꿈_탭_같은_다른_글자와_null은_그대로_둔다() throws Exception {
		Message message = objectMapper.readValue("{\"role\":null,\"text\":\"첫 줄\\n\\t둘째 줄\\u0001\"}",
				Message.class);

		assertThat(message.role()).isNull();
		assertThat(message.text()).isEqualTo("첫 줄\n\t둘째 줄\u0001");
	}

	@Test
	void 숫자를_글자로_받는_기본_동작은_그대로다() throws Exception {
		assertThat(objectMapper.readValue("{\"role\":12,\"text\":true}", Message.class))
				.isEqualTo(new Message("12", "true"));
	}

	// 모델 답변은 readTree로 읽어 이 역직렬화기를 거치지 않는다. 저장 전에는 LlmResponse가 지운다
	@Test
	void 모델_답변_글은_LlmResponse에서_지운다() throws Exception {
		JsonNode node = objectMapper.readTree("{\"text\":\"a\\u0000b\"}");
		assertThat(node.get("text").asText()).isEqualTo("a\u0000b");

		assertThat(new LlmResponse("{\"concepts\":\"a\u0000b\"}", 1, 1).text()).isEqualTo("{\"concepts\":\"ab\"}");
	}
}
