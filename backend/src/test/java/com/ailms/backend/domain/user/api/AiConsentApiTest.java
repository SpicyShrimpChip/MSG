package com.ailms.backend.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.conversation.dao.ConversationRepository;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AI 대화 분석 동의 바꾸기와 대화 가져오기의 연동. 대화 내용은 모두 가상 값이다.
 * 회원을 지우면 과목·대화가 함께 지워지므로(ON DELETE CASCADE) 매번 회원부터 새로 만든다.
 */
@IntegrationTest
class AiConsentApiTest {

	private static final String PASSWORD = "Passw0rd!";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private ConversationRepository conversationRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 토큰이_없으면_401() throws Exception {
		mockMvc.perform(patch("/api/users/me/consents").contentType(MediaType.APPLICATION_JSON)
				.content("{\"aiAnalysis\":true}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void 가입_때_동의하지_않았어도_동의하면_대화를_가져올_수_있고_다시_로그인해도_유지된다() throws Exception {
		String token = signupAndLogin("me@example.com", false);
		long course = createCourse(token);
		sendConversation(token, course, "c-1").andExpect(status().isForbidden());

		changeConsent(token, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("me@example.com"))
				.andExpect(jsonPath("$.data.aiAnalysisConsent").value(true));
		assertThat(jdbcTemplate.queryForObject(
				"select ai_analysis_consented_at from users where email = 'me@example.com'", Object.class)).isNotNull();

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.aiAnalysisConsent").value(true));
		sendConversation(token, course, "c-1").andExpect(status().isCreated());

		// 다시 로그인해도 DB에 저장한 값을 읽는다
		login("me@example.com")
				.andExpect(jsonPath("$.data.user.aiAnalysisConsent").value(true));
	}

	@Test
	void 철회하면_내_대화와_가져오기_기록만_지우고_다시_동의하기_전까지_가져오기를_막는다() throws Exception {
		String token = signupAndLogin("me@example.com", true);
		String otherToken = signupAndLogin("other@example.com", true);
		long course = createCourse(token);
		long otherCourse = createCourse(otherToken);
		sendConversation(token, course, "c-1").andExpect(status().isCreated());
		sendConversation(token, course, "c-2").andExpect(status().isCreated());
		sendConversation(otherToken, otherCourse, "o-1").andExpect(status().isCreated());

		changeConsent(token, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.aiAnalysisConsent").value(false));

		assertThat(jdbcTemplate.queryForList("select client_id from conversation", String.class)).containsExactly("o-1");
		assertThat(count("conversation_message")).isEqualTo(2);
		assertThat(count("conversation_import")).isEqualTo(1);
		assertThat(count("conversation_import_request")).isEqualTo(1);
		// 과목은 그대로 남는다 (지우는 것은 AI 대화뿐)
		assertThat(count("course")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
				"select ai_analysis_consented_at from users where email = 'me@example.com'", Object.class)).isNull();

		sendConversation(token, course, "c-3")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("AI 대화 분석에 동의해야 대화를 가져올 수 있어요."));
		mockMvc.perform(get("/api/conversations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data").isEmpty());

		// 다시 동의하면 다시 가져올 수 있다
		changeConsent(token, true).andExpect(status().isOk());
		sendConversation(token, course, "c-1").andExpect(status().isCreated());
	}

	@Test
	void 내_동의만_바뀌고_다른_사용자의_동의는_그대로다() throws Exception {
		String token = signupAndLogin("me@example.com", true);
		signupAndLogin("other@example.com", true);

		changeConsent(token, false).andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject(
				"select ai_analysis_consent from users where email = 'other@example.com'", Boolean.class)).isTrue();
	}

	@Test
	void 같은_값으로_다시_보내도_동의_시각은_바뀌지_않는다() throws Exception {
		String token = signupAndLogin("me@example.com", true);
		Object before = jdbcTemplate.queryForObject(
				"select ai_analysis_consented_at from users where email = 'me@example.com'", Object.class);

		changeConsent(token, true).andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForObject(
				"select ai_analysis_consented_at from users where email = 'me@example.com'", Object.class))
				.isEqualTo(before);
	}

	@Test
	void 동의_여부가_없으면_400() throws Exception {
		String token = signupAndLogin("me@example.com", false);

		mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("동의 여부를 골라 주세요."));
		mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"aiAnalysis\":\"yes\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void 동의하지_않은_사용자의_대화는_분석_대상에서_빠진다() throws Exception {
		String token = signupAndLogin("me@example.com", true);
		sendConversation(token, createCourse(token), "c-1").andExpect(status().isCreated());
		Long userId = jdbcTemplate.queryForObject("select id from users where email = 'me@example.com'", Long.class);
		assertThat(conversationRepository.findAnalyzable(userId)).hasSize(1);

		// 철회 API를 거치지 않고 동의만 꺼진 상태 (지우기와 분석이 엇갈린 경우를 흉내 낸다)
		jdbcTemplate.update("update users set ai_analysis_consent = false where id = ?", userId);

		assertThat(conversationRepository.findAnalyzable(userId)).isEmpty();
	}

	// ---------- 도우미 ----------

	private ResultActions changeConsent(String token, boolean consent) throws Exception {
		return mockMvc.perform(patch("/api/users/me/consents")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("aiAnalysis", consent))));
	}

	private ResultActions sendConversation(String token, long courseId, String clientId) throws Exception {
		Map<String, Object> conversation = new HashMap<>();
		conversation.put("clientId", clientId);
		conversation.put("source", "chatgpt");
		conversation.put("title", "교착 상태 질문");
		conversation.put("messages", List.of(
				Map.of("role", "user", "text", "교착 상태 조건이 뭐야?"),
				Map.of("role", "assistant", "text", "상호 배제, 점유 대기, 비선점, 순환 대기예요.")));
		Map<String, Object> body = Map.of(
				"importId", UUID.randomUUID().toString(),
				"requestIndex", 1,
				"requestCount", 1,
				"courseId", courseId,
				"conversations", List.of(conversation));
		return mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(body)));
	}

	private long createCourse(String token) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("name", "운영체제", "semester", "2026-2", "keywords", List.of()))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private ResultActions login(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.header("X-Requested-With", "XMLHttpRequest")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(Map.of("email", email, "password", PASSWORD))));
	}

	private String signupAndLogin(String email, boolean agreedAiAnalysis) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of(
								"email", email,
								"name", "테스터",
								"password", PASSWORD,
								"passwordConfirm", PASSWORD,
								"agreedTerms", true,
								"agreedPrivacy", true,
								"agreedAiAnalysis", agreedAiAnalysis))))
				.andExpect(status().isCreated());
		String response = login(email).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
