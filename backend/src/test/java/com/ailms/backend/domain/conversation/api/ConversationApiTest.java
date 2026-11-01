package com.ailms.backend.domain.conversation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.conversation.dao.ConversationRepository;
import com.ailms.backend.domain.conversation.entity.Conversation;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 대화 가져오기 API. 대화 내용·개인정보는 모두 가상 값이다(실제 내보내기 파일은 쓰지 않는다).
 * 회원을 지우면 과목·가져오기 작업·대화가 함께 지워지므로(ON DELETE CASCADE) 매번 회원부터 새로 만든다.
 */
@IntegrationTest
class ConversationApiTest {

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

	private String token;
	private String otherToken;
	private long courseId;
	private long otherCourseId;

	@BeforeEach
	void setUp() throws Exception {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com", true);
		otherToken = signupAndLogin("other@example.com", true);
		courseId = createCourse(token, "운영체제");
		otherCourseId = createCourse(otherToken, "정보보호개론");
	}

	// ---------- 저장 ----------

	@Test
	void 가린_대화를_저장하고_모든_요청을_받으면_분석_대상이_된다() throws Exception {
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(
				conversation("c-1", "교착 상태 질문", List.of(
						message("user", "교착 상태 조건이 뭐야? 내 메일은 [이메일]이야"),
						message("assistant", "상호 배제, 점유 대기, 비선점, 순환 대기예요."))))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.importedCount").value(1))
				.andExpect(jsonPath("$.data.importCompleted").value(true));

		Map<String, Object> row = jdbcTemplate.queryForMap("select * from conversation");
		assertThat(row.get("source")).isEqualTo("CHATGPT");
		assertThat(row.get("client_id")).isEqualTo("c-1");
		assertThat(((Number) row.get("course_id")).longValue()).isEqualTo(courseId);
		assertThat(row.get("message_count")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForList(
				"select role from conversation_message order by part_index, seq", String.class))
				.containsExactly("USER", "ASSISTANT");

		mockMvc.perform(get("/api/conversations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].source").value("chatgpt"))
				.andExpect(jsonPath("$.data[0].title").value("교착 상태 질문"))
				.andExpect(jsonPath("$.data[0].courseId").value(courseId))
				.andExpect(jsonPath("$.data[0].messageCount").value(2))
				// 외부 서비스의 UTC 시각을 서버 시간대로 바꿔 저장한다
				.andExpect(jsonPath("$.data[0].createdAt").value(LocalDateTime
						.ofInstant(Instant.parse("2026-10-20T09:00:00Z"), ZoneId.systemDefault())
						.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)))
				.andExpect(jsonPath("$.data[0].analysisReady").value(true))
				.andExpect(jsonPath("$.data[0].messages").doesNotExist());
		assertThat(analyzableClientIds("me@example.com")).containsExactly("c-1");
	}

	@Test
	void 나눠_보낸_긴_메시지는_이어짐_표시와_함께_순서대로_저장한다() throws Exception {
		Map<String, Object> first = message("user", "가".repeat(20_000));
		first.put("continued", true);
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(
				conversation("c-1", "긴 글", List.of(first, message("user", "나머지 글"), message("assistant", "답"))))))
				.andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForList(
				"select continued from conversation_message order by part_index, seq", Boolean.class))
				.containsExactly(true, false, false);
	}

	@Test
	void AI_대화_분석에_동의하지_않으면_403이고_저장하지_않는다() throws Exception {
		String noConsent = signupAndLogin("no-consent@example.com", false);
		long course = createCourse(noConsent, "자료구조");

		send(noConsent, importBody(UUID.randomUUID().toString(), 1, 1, course, List.of(simple("c-1"))))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("AI 대화 분석에 동의해야 대화를 가져올 수 있어요."));
		assertThat(count("conversation")).isZero();
	}

	@Test
	void 남의_과목으로_보내면_404() throws Exception {
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, otherCourseId, List.of(simple("c-1"))))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("과목을 찾을 수 없어요."));
		assertThat(count("conversation")).isZero();
	}

	@Test
	void 토큰이_없으면_모두_401() throws Exception {
		mockMvc.perform(post("/api/conversations").contentType(MediaType.APPLICATION_JSON)
				.content(json(importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(simple("c-1"))))))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/conversations/1")).andExpect(status().isUnauthorized());
	}

	// ---------- 형식·한도 ----------

	@Test
	void 형식이_틀리면_요청_전체를_400으로_거절한다() throws Exception {
		String importId = UUID.randomUUID().toString();
		List<Map<String, Object>> invalid = new ArrayList<>();

		Map<String, Object> badSource = simple("c-1");
		badSource.put("source", "gemini");
		invalid.add(importBody(importId, 1, 1, courseId, List.of(badSource)));

		invalid.add(importBody(importId, 1, 1, courseId,
				List.of(conversation("c-1", "제목", List.of(message("system", "글"))))));
		invalid.add(importBody(importId, 1, 1, courseId,
				List.of(conversation("c-1", "제목", List.of(message("user", "가".repeat(20_001)))))));
		invalid.add(importBody(importId, 1, 1, courseId,
				List.of(conversation("c-1", "가".repeat(501), List.of(message("user", "글"))))));
		invalid.add(importBody(importId, 1, 1, courseId, List.of(conversation("c-1", "제목", List.of()))));
		invalid.add(importBody(importId, 1, 1, courseId,
				IntStream.rangeClosed(1, 51).mapToObj(i -> simple("c-" + i)).toList()));
		invalid.add(importBody(importId, 1, 1, courseId, List.of(part("c-1", 3, 2, "글"))));
		invalid.add(importBody(importId, 2, 1, courseId, List.of(simple("c-1"))));
		invalid.add(importBody("not-a-uuid", 1, 1, courseId, List.of(simple("c-1"))));
		// 같은 대화가 한 요청에 두 번
		invalid.add(importBody(importId, 1, 1, courseId, List.of(simple("c-1"), simple("c-1"))));

		for (Map<String, Object> body : invalid) {
			send(token, body).andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
		}
		assertThat(count("conversation")).isZero();
		assertThat(count("conversation_import")).isZero();
	}

	@Test
	void 요청이_2MB를_넘으면_본문을_읽기_전에_413() throws Exception {
		Map<String, Object> body = importBody(UUID.randomUUID().toString(), 1, 1, courseId,
				List.of(conversation("c-1", "제목", List.of(message("user", "a".repeat(2 * 1024 * 1024))))));

		send(token, body)
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.message").value("한 번에 보낼 수 있는 크기(2MB)를 넘었어요. 대화를 나눠서 보내 주세요."));
	}

	@Test
	void 프론트가_나눈_1MB_요청은_받는다() throws Exception {
		// 메시지 20,000자 × 50개 ≈ 1MB (프론트 MAX_REQUEST_BYTES 근처)
		List<Map<String, Object>> messages = IntStream.range(0, 50)
				.mapToObj(i -> message(i % 2 == 0 ? "user" : "assistant", "a".repeat(20_000)))
				.toList();
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId,
				List.of(conversation("c-1", "긴 대화", messages))))
				.andExpect(status().isCreated());
		assertThat(count("conversation_message")).isEqualTo(50);
	}

	// ---------- 개인정보 재검사 ----------

	@Test
	void 개인정보가_남은_대화가_있으면_요청_전체를_저장하지_않고_422로_목록을_돌려준다() throws Exception {
		String importId = UUID.randomUUID().toString();
		Map<String, Object> leaked = conversation("c-2", "연락처", List.of(message("user", "제 번호는 010-1234-5678이에요")));

		send(token, importBody(importId, 1, 1, courseId, List.of(simple("c-1"), leaked)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("저장할 수 없는 대화가 있어 이번에 보낸 대화를 저장하지 않았어요."))
				.andExpect(jsonPath("$.data.rejected", hasSize(1)))
				.andExpect(jsonPath("$.data.rejected[0].clientId").value("c-2"))
				.andExpect(jsonPath("$.data.rejected[0].reason")
						.value("개인정보로 보이는 글이 남아 있어 저장하지 않았어요. 가린 내용을 다시 확인해 주세요."));
		assertThat(count("conversation")).isZero();
		assertThat(count("conversation_import")).isZero();

		// 가린 뒤 같은 작업 id로 다시 보내면 저장된다
		Map<String, Object> masked = conversation("c-2", "연락처", List.of(message("user", "제 번호는 [전화번호]이에요")));
		send(token, importBody(importId, 1, 1, courseId, List.of(simple("c-1"), masked)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importCompleted").value(true));
		assertThat(count("conversation")).isEqualTo(2);
	}

	@Test
	void 제목과_나뉜_메시지_경계에_걸친_개인정보도_찾는다() throws Exception {
		Map<String, Object> inTitle = conversation("c-1", "test@example.com 질문", List.of(message("user", "글")));
		Map<String, Object> head = message("user", "제 메일은 test@exam");
		head.put("continued", true);
		Map<String, Object> acrossSplit = conversation("c-2", "제목", List.of(head, message("user", "ple.com 이에요")));

		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(inTitle, acrossSplit)))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.rejected[0].clientId").value("c-1"))
				.andExpect(jsonPath("$.data.rejected[1].clientId").value("c-2"));
	}

	// ---------- 가져오기 작업 ----------

	@Test
	void 작업의_요청을_모두_받기_전에는_분석에_쓰지_않는다() throws Exception {
		String importId = UUID.randomUUID().toString();
		send(token, importBody(importId, 1, 2, courseId, List.of(simple("c-1"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importCompleted").value(false));

		mockMvc.perform(get("/api/conversations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].analysisReady").value(false));
		assertThat(analyzableClientIds("me@example.com")).isEmpty();

		long secondCourse = createCourse(token, "자료구조");
		send(token, importBody(importId, 2, 2, secondCourse, List.of(simple("c-2"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importCompleted").value(true));
		assertThat(analyzableClientIds("me@example.com")).containsExactlyInAnyOrder("c-1", "c-2");
	}

	@Test
	void 중간_요청이_실패해도_앞서_저장한_대화는_남기고_다시_보내면_이어서_완료한다() throws Exception {
		String importId = UUID.randomUUID().toString();
		send(token, importBody(importId, 1, 2, courseId, List.of(simple("c-1")))).andExpect(status().isCreated());
		Map<String, Object> leaked = conversation("c-2", "제목", List.of(message("user", "메일 me@example.com")));
		send(token, importBody(importId, 2, 2, courseId, List.of(leaked))).andExpect(status().isUnprocessableEntity());

		assertThat(jdbcTemplate.queryForList("select client_id from conversation", String.class)).containsExactly("c-1");
		assertThat(analyzableClientIds("me@example.com")).isEmpty();

		// 응답을 받지 못했다고 보고 1번부터 다시 보내도 중복 저장하지 않는다
		send(token, importBody(importId, 1, 2, courseId, List.of(simple("c-1"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importedCount").value(1))
				.andExpect(jsonPath("$.data.importCompleted").value(false));
		send(token, importBody(importId, 2, 2, courseId, List.of(simple("c-2"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importCompleted").value(true));

		assertThat(count("conversation")).isEqualTo(2);
		assertThat(count("conversation_message")).isEqualTo(4);
		assertThat(analyzableClientIds("me@example.com")).containsExactlyInAnyOrder("c-1", "c-2");
	}

	@Test
	void 같은_작업인데_전체_요청_수가_다르면_400() throws Exception {
		String importId = UUID.randomUUID().toString();
		send(token, importBody(importId, 1, 2, courseId, List.of(simple("c-1")))).andExpect(status().isCreated());

		send(token, importBody(importId, 2, 3, courseId, List.of(simple("c-2"))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("가져오기 작업 정보가 올바르지 않아요."));
	}

	@Test
	void 작업_id는_사용자마다_따로다() throws Exception {
		String importId = UUID.randomUUID().toString();
		send(token, importBody(importId, 1, 1, courseId, List.of(simple("c-1")))).andExpect(status().isCreated());
		send(otherToken, importBody(importId, 1, 1, otherCourseId, List.of(simple("c-1"))))
				.andExpect(status().isCreated());

		assertThat(count("conversation")).isEqualTo(2);
		assertThat(count("conversation_import")).isEqualTo(2);
	}

	// ---------- 조각·중복 ----------

	@Test
	void 조각은_순서가_바뀌어_다른_요청으로_와도_한_대화로_이어_붙인다() throws Exception {
		String importId = UUID.randomUUID().toString();
		send(token, importBody(importId, 1, 2, courseId, List.of(part("long", 2, 2, "뒤쪽"))))
				.andExpect(status().isCreated());
		mockMvc.perform(get("/api/conversations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].analysisReady").value(false));

		send(token, importBody(importId, 2, 2, courseId, List.of(part("long", 1, 2, "앞쪽"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importCompleted").value(true));

		Map<String, Object> row = jdbcTemplate.queryForMap("select * from conversation");
		assertThat(row.get("client_id")).isEqualTo("long");
		assertThat(row.get("part_count")).isEqualTo(2);
		assertThat(row.get("received_part_count")).isEqualTo(2);
		assertThat(row.get("message_count")).isEqualTo(4);
		assertThat(jdbcTemplate.queryForList(
				"select content from conversation_message order by part_index, seq", String.class))
				.containsExactly("앞쪽 질문", "앞쪽 답", "뒤쪽 질문", "뒤쪽 답");
		assertThat(analyzableClientIds("me@example.com")).containsExactly("long");
	}

	@Test
	void 이미_가져온_대화를_다른_과목으로_다시_보내면_처음_과목을_유지한다() throws Exception {
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(simple("c-1"))))
				.andExpect(status().isCreated());
		long secondCourse = createCourse(token, "자료구조");

		send(token, importBody(UUID.randomUUID().toString(), 1, 1, secondCourse, List.of(simple("c-1"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importedCount").value(1));

		assertThat(count("conversation")).isEqualTo(1);
		assertThat(count("conversation_message")).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select course_id from conversation", Long.class)).isEqualTo(courseId);
	}

	@Test
	void 끝나지_않은_작업의_대화를_새_작업으로_다시_보내면_새_작업이_끝날_때_분석_대상이_된다() throws Exception {
		// 앞 작업은 2번 요청을 보내지 못하고 끝났다
		send(token, importBody(UUID.randomUUID().toString(), 1, 2, courseId, List.of(simple("c-1"))))
				.andExpect(status().isCreated());
		assertThat(analyzableClientIds("me@example.com")).isEmpty();

		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(simple("c-1"), simple("c-2"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.importCompleted").value(true));

		assertThat(count("conversation")).isEqualTo(2);
		assertThat(analyzableClientIds("me@example.com")).containsExactlyInAnyOrder("c-1", "c-2");
	}

	@Test
	void 다_받지_못한_대화를_다르게_나눠_보내면_422() throws Exception {
		send(token, importBody(UUID.randomUUID().toString(), 1, 2, courseId, List.of(part("long", 1, 2, "앞쪽"))))
				.andExpect(status().isCreated());

		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(part("long", 2, 3, "가운데"))))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.rejected[0].clientId").value("long#2"))
				.andExpect(jsonPath("$.data.rejected[0].reason")
						.value("앞서 보낸 같은 대화와 나뉜 방식이 달라 저장하지 않았어요. 가져온 대화를 지운 뒤 다시 보내 주세요."));
		assertThat(count("conversation_part")).isEqualTo(1);
	}

	// ---------- 목록·삭제 ----------

	@Test
	void 목록은_내_대화만_보이고_남의_대화는_삭제할_수_없다() throws Exception {
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(simple("mine"))))
				.andExpect(status().isCreated());
		send(otherToken, importBody(UUID.randomUUID().toString(), 1, 1, otherCourseId, List.of(simple("theirs"))))
				.andExpect(status().isCreated());
		long theirs = jdbcTemplate.queryForObject("select id from conversation where client_id = 'theirs'", Long.class);

		mockMvc.perform(get("/api/conversations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].title").value("제목 mine"));
		mockMvc.perform(delete("/api/conversations/" + theirs).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("대화를 찾을 수 없어요."));
		assertThat(count("conversation")).isEqualTo(2);
	}

	@Test
	void 대화를_삭제하면_메시지와_조각도_지워진다() throws Exception {
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, courseId, List.of(simple("c-1"))))
				.andExpect(status().isCreated());
		long id = jdbcTemplate.queryForObject("select id from conversation", Long.class);

		mockMvc.perform(delete("/api/conversations/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());

		assertThat(count("conversation")).isZero();
		assertThat(count("conversation_part")).isZero();
		assertThat(count("conversation_message")).isZero();
	}

	@Test
	void 과목을_지우면_그_과목에_가져온_대화도_지워진다() throws Exception {
		long secondCourse = createCourse(token, "자료구조");
		send(token, importBody(UUID.randomUUID().toString(), 1, 2, courseId, List.of(simple("c-1"))));
		send(token, importBody(UUID.randomUUID().toString(), 1, 1, secondCourse, List.of(simple("c-2"))));

		mockMvc.perform(delete("/api/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForList("select client_id from conversation", String.class)).containsExactly("c-2");
		assertThat(count("conversation_message")).isEqualTo(2);
	}

	// ---------- 도우미 ----------

	private ResultActions send(String accessToken, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/conversations")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(json(body)));
	}

	private static Map<String, Object> importBody(String importId, int requestIndex, int requestCount, long courseId,
			List<Map<String, Object>> conversations) {
		Map<String, Object> body = new HashMap<>();
		body.put("importId", importId);
		body.put("requestIndex", requestIndex);
		body.put("requestCount", requestCount);
		body.put("courseId", courseId);
		body.put("conversations", conversations);
		return body;
	}

	private static Map<String, Object> conversation(String clientId, String title, List<Map<String, Object>> messages) {
		Map<String, Object> conversation = new HashMap<>();
		conversation.put("clientId", clientId);
		conversation.put("source", "chatgpt");
		conversation.put("title", title);
		conversation.put("createdAt", "2026-10-20T09:00:00Z");
		conversation.put("messages", messages);
		return conversation;
	}

	// 질문·답 두 메시지짜리 대화
	private static Map<String, Object> simple(String clientId) {
		return conversation(clientId, "제목 " + clientId,
				List.of(message("user", clientId + " 질문"), message("assistant", clientId + " 답")));
	}

	// 긴 대화의 조각 (clientId는 프론트처럼 '<원래 id>#<번호>')
	private static Map<String, Object> part(String parentId, int index, int count, String label) {
		Map<String, Object> conversation = conversation(parentId + "#" + index, "긴 대화",
				List.of(message("user", label + " 질문"), message("assistant", label + " 답")));
		conversation.put("part", Map.of("parentClientId", parentId, "index", index, "count", count));
		return conversation;
	}

	private static Map<String, Object> message(String role, String text) {
		Map<String, Object> message = new HashMap<>();
		message.put("role", role);
		message.put("text", text);
		return message;
	}

	private List<String> analyzableClientIds(String email) {
		Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
		return conversationRepository.findAnalyzable(userId).stream().map(Conversation::getClientId).toList();
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}

	private String json(Object value) throws Exception {
		return objectMapper.writeValueAsString(value);
	}

	private static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private long createCourse(String accessToken, String name) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("name", name, "semester", "2026-2", "keywords", List.of()))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
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
		String response = mockMvc.perform(post("/api/auth/login")
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
