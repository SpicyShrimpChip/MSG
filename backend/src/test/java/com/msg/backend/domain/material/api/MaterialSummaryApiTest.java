package com.msg.backend.domain.material.api;

import static com.msg.backend.support.TestApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.global.llm.AiCallQuota;
import com.msg.backend.global.llm.LlmProvider;
import com.msg.backend.support.FakeLlmClients;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestApi;
import com.msg.backend.support.TestPdfs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 학습자료 요약본: 원문 인용 검증·저장·재사용·자료 변경·오류. 가짜 모델만 쓴다(실제 AI 결과가 아님).
 */
@IntegrationTest
@TestPropertySource(properties = "app.material-content.enabled=true")
class MaterialSummaryApiTest {

	// 가짜 모델 답: 개요·개념 하나는 원문 그대로(검증 완료), 하나는 지어낸 인용(검증 실패), 하나는 인용 없음(근거 부족),
	// 예시는 자료에 없는 보충 설명, 원리 하나는 위치가 다른 쪽의 문장
	private static final String SUMMARY = """
			{"overview":{"text":"공개키 암호와 VLAN을 다룬 자료","basis":"material","quote":"Public key encryption uses a key pair"},
			"concepts":[
			 {"name":"공개키 암호","definition":"공개키와 개인키 한 쌍을 쓴다","basis":"material","quote":"Public key encryption uses a key pair"},
			 {"name":"전자서명","definition":"개인키로 서명한다","basis":"material","quote":"Signatures are made with quantum keys"},
			 {"name":"인증서","definition":"공개키를 보증한다","basis":"material"}],
			"principles":[{"text":"VLAN은 브로드캐스트 도메인을 나눈다","basis":"material","quote":"VLAN splits broadcast domains"}],
			"examples":[{"text":"택배 상자 자물쇠 비유","basis":"supplement"}],
			"keyPoints":[{"text":"키 쌍을 구분한다","basis":"material","quote":"uses a key pair"}],
			"selfCheck":[{"question":"공개키와 개인키의 역할은?","hint":"암호화와 복호화"}]}
			""";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;
	@Autowired
	private FakeLlmClients fakeLlm;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private Clock clock;

	private String token;
	private long courseId;
	private long material;

	@BeforeEach
	void setUp() throws Exception {
		fakeLlm.resetAll();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		courseId = TestApi.createCourse(mockMvc, objectMapper, token, "정보보호개론", List.of("공개키 암호", "VLAN"));
		material = upload("정보보호개론.pdf", TestPdfs.withPages("Course introduction and history",
				"Public key encryption uses a key pair", "VLAN splits broadcast domains"));
	}

	@Test
	void 원문_인용을_항목마다_검증해_저장하고_보충_설명은_따로_표시한다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);

		generate("exam_prep", true, false)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("ready"))
				.andExpect(jsonPath("$.data.reused").value(false))
				.andExpect(jsonPath("$.data.purpose").value("exam_prep"))
				.andExpect(jsonPath("$.data.content.overview.check").value("verified"))
				.andExpect(jsonPath("$.data.content.overview.location").value("p. 2"))
				.andExpect(jsonPath("$.data.content.concepts", hasSize(3)))
				.andExpect(jsonPath("$.data.content.concepts[0].check").value("verified"))
				.andExpect(jsonPath("$.data.content.concepts[1].check").value("failed"))
				.andExpect(jsonPath("$.data.content.concepts[1].quote").doesNotExist())
				.andExpect(jsonPath("$.data.content.concepts[2].check").value("insufficient"))
				.andExpect(jsonPath("$.data.content.principles[0].location").value("p. 3"))
				.andExpect(jsonPath("$.data.content.examples[0].basis").value("supplement"))
				.andExpect(jsonPath("$.data.content.examples[0].check").doesNotExist())
				.andExpect(jsonPath("$.data.content.selfCheck[0].question").value("공개키와 개인키의 역할은?"))
				.andExpect(jsonPath("$.data.verification.verified").value(4))
				.andExpect(jsonPath("$.data.verification.failed").value(1))
				.andExpect(jsonPath("$.data.verification.insufficient").value(1))
				.andExpect(jsonPath("$.data.verification.supplement").value(1))
				// 6개 중 4개 확인 → 일부 검증 (모두 확인해야 검증 완료)
				.andExpect(jsonPath("$.data.verification.status").value("partial"))
				.andExpect(jsonPath("$.data.sources[0].location").value("p. 2"))
				.andExpect(jsonPath("$.data.generatedBy.provider").value("gpt"))
				.andExpect(jsonPath("$.data.promptVersion").value("summary-v1"))
				.andExpect(jsonPath("$.data.sourceScope", hasSize(3)))
				.andExpect(jsonPath("$.data.sourceTruncated").value(false));

		// 보낸 글: 자료가 짧아 전체(쪽 표시 포함)와 학습 목적
		String sent = fakeLlm.get(LlmProvider.GPT).requests().get(0).user();
		assertThat(sent).contains("Public key encryption", "VLAN splits", "[p. 1]", "\"purpose\":\"exam_prep\"");
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_ai_call where purpose = 'SUMMARY'",
				Integer.class)).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select content_hash from material_summary", String.class)).hasSize(64);

		// 다시 보기: 모델을 부르지 않고 저장한 요약
		view("exam_prep").andExpect(jsonPath("$.data.status").value("ready"))
				.andExpect(jsonPath("$.data.stale").value(false))
				.andExpect(jsonPath("$.data.content.concepts[0].name").value("공개키 암호"));
		view("self_development").andExpect(jsonPath("$.data.status").value("none"));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(1);
	}

	@Test
	void 같은_목적이면_재사용하고_다시_만들기나_지시문이_바뀌면_새로_만든다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("exam_prep", true, false).andExpect(status().isOk());

		generate("exam_prep", true, false).andExpect(jsonPath("$.data.reused").value(true));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(1);

		generate("exam_prep", true, true).andExpect(jsonPath("$.data.reused").value(false));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(2);

		// 저장한 해시가 달라도 비교하지 않는다 (자료 파일을 바꾸는 API가 없어 지시문 버전만 본다)
		jdbcTemplate.update("update material_summary set content_hash = 'old'");
		view("exam_prep").andExpect(jsonPath("$.data.stale").value(false));
		generate("exam_prep", true, false).andExpect(jsonPath("$.data.reused").value(true));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(2);

		// 지시문이 바뀐 것처럼 (요약을 만든 지시문 버전이 지금과 다름)
		jdbcTemplate.update("update material_summary set prompt_version = 'summary-v0'");
		view("exam_prep").andExpect(jsonPath("$.data.stale").value(true))
				.andExpect(jsonPath("$.data.staleReason").value(containsString("요약 방식이 바뀌었어요")));
		generate("exam_prep", true, false).andExpect(jsonPath("$.data.reused").value(false))
				.andExpect(jsonPath("$.data.stale").value(false));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(3);
		assertThat(jdbcTemplate.queryForObject("select count(*) from material_summary", Integer.class)).isEqualTo(1);
	}

	@Test
	void 업무_활용은_직무를_보내고_목적마다_따로_저장한다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("work", true, false).andExpect(status().isOk());
		generate("self_development", true, false).andExpect(status().isOk());

		assertThat(fakeLlm.get(LlmProvider.GPT).requests().get(0).user()).contains("\"purpose\":\"work\"", "jobField");
		assertThat(fakeLlm.get(LlmProvider.GPT).requests().get(1).user()).doesNotContain("jobField");
		assertThat(jdbcTemplate.queryForObject("select count(*) from material_summary", Integer.class)).isEqualTo(2);
	}

	@Test
	void 인용을_하나도_확인하지_못하면_저장하지_않고_502이고_이전_요약은_남는다() throws Exception {
		String fabricated = "{\"overview\":{\"text\":\"개요\",\"basis\":\"material\",\"quote\":\"없는 문장을 지어냈다\"},"
				+ "\"concepts\":[{\"name\":\"X\",\"definition\":\"y\",\"basis\":\"material\",\"quote\":\"이것도 지어냈다\"}]}";
		fakeLlm.get(LlmProvider.GPT).answer(fabricated);
		generate("exam_prep", true, false).andExpect(status().isBadGateway());
		view("exam_prep").andExpect(jsonPath("$.data.status").value("none"));

		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("exam_prep", true, false).andExpect(status().isOk());
		fakeLlm.get(LlmProvider.GPT).answer(fabricated);
		generate("exam_prep", true, true).andExpect(status().isBadGateway());
		view("exam_prep").andExpect(jsonPath("$.data.status").value("ready"))
				.andExpect(jsonPath("$.data.content.concepts[0].name").value("공개키 암호"));
		assertThat(jdbcTemplate.queryForObject("select status from material_summary", String.class))
				.isEqualTo("SUCCEEDED");
	}

	@Test
	void 확인_동의_모델_목적_개인정보_스캔_PDF_남의_자료를_검사한다() throws Exception {
		generate("exam_prep", true, false).andExpect(status().isServiceUnavailable());
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("exam_prep", false, false).andExpect(status().isBadRequest());
		generate("unknown", true, false).andExpect(status().isBadRequest());

		long personal = upload("연락.txt", "공개키 암호 문의는 me@example.com 으로".getBytes());
		generateFor(token, personal, "exam_prep", true).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.reason").value("PERSONAL_INFO"))
				.andExpect(jsonPath("$.message").value("자료 본문에 이메일·전화번호 같은 개인정보가 있어 외부 AI로 보내지 않았어요."));
		long scanned = upload("스캔.pdf", TestPdfs.withPages((String) null));
		generateFor(token, scanned, "exam_prep", true).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.reason").value("NO_TEXT"))
				.andExpect(jsonPath("$.message").value(containsString("OCR")));

		String other = signupAndLogin("other@example.com");
		mockMvc.perform(get("/api/materials/" + material + "/summary").header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound());

		mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"aiQuiz\":false}")).andExpect(status().isOk());
		generate("exam_prep", true, false).andExpect(status().isForbidden());
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
	}

	@Test
	void 확인_체크는_모델_확인보다_먼저다() throws Exception {
		generate("exam_prep", false, false).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("자료 본문을 외부 AI로 보내려면 확인에 체크해 주세요."));
		generate("exam_prep", true, false).andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("연결된 AI 모델이 없어 지금은 요약을 만들 수 없어요."));
	}

	@Test
	void 모델_호출_한도에_닿으면_429이고_요약_문구다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("exam_prep", true, false).andExpect(status().isOk());
		Long userId = jdbcTemplate.queryForObject("select id from users where email = 'me@example.com'", Long.class);
		jdbcTemplate.update("insert into quiz_ai_call (user_id, purpose, provider, attempts, outcome, latency_ms,"
				+ " created_at) values (?, 'GRADING', 'gpt', 200, 'FAILED', 0, ?)", userId,
				Timestamp.valueOf(LocalDateTime.now(clock)));

		generate("work", true, false).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value("오늘 AI 호출 한도를 다 써서 지금은 요약을 만들 수 없어요. 내일 다시 시도해 주세요."));
		// 재사용은 보내기 전 확인보다 먼저다
		generate("exam_prep", true, false).andExpect(status().isOk()).andExpect(jsonPath("$.data.reused").value(true));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(1);
	}

	@Test
	void 남의_자료로_요약을_만들면_404이고_모델을_부르지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		String other = signupAndLogin("other@example.com");

		generateFor(other, material, "exam_prep", true).andExpect(status().isNotFound());
		for (LlmProvider provider : LlmProvider.values()) {
			assertThat(fakeLlm.get(provider).requests()).as(provider + " 호출").isEmpty();
		}
		assertThat(jdbcTemplate.queryForObject("select count(*) from material_summary", Integer.class)).isZero();
	}

	@Test
	void 사용자_하루_AI_몫을_다_쓰면_요약을_만들지_않고_429이지만_저장한_요약은_다시_쓴다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("exam_prep", true, false).andExpect(status().isOk());
		Long userId = jdbcTemplate.queryForObject("select id from users where email = 'me@example.com'", Long.class);
		// 테스트 설정의 사용자 몫은 1,000,000(TestLlm). 몫을 모두 쓴 것처럼 채운다 (user_ai_call_daily, #234)
		jdbcTemplate.update("DELETE FROM user_ai_call_daily WHERE user_id = ?", userId);
		jdbcTemplate.update("INSERT INTO user_ai_call_daily (user_id, usage_date, call_count) VALUES (?, ?, ?)", userId,
				LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))), 1_000_000);

		generate("exam_prep", true, false).andExpect(status().isOk()).andExpect(jsonPath("$.data.reused").value(true));
		generate("exam_prep", true, true).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value(AiCallQuota.USER_EXHAUSTED_MESSAGE));
		generate("work", true, false).andExpect(status().isTooManyRequests());
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(1);
	}

	@Test
	void 자료를_지우면_요약도_지워진다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(SUMMARY);
		generate("exam_prep", true, false).andExpect(status().isOk());
		mockMvc.perform(delete("/api/materials/" + material).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().is2xxSuccessful());
		assertThat(jdbcTemplate.queryForObject("select count(*) from material_summary", Integer.class)).isZero();
	}

	private ResultActions generate(String purpose, boolean confirmed, boolean regenerate) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("purpose", purpose);
		body.put("materialContentConsent", confirmed);
		body.put("regenerate", regenerate);
		return mockMvc.perform(post("/api/materials/" + material + "/summary")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private ResultActions generateFor(String asToken, long target, String purpose, boolean confirmed) throws Exception {
		return mockMvc.perform(post("/api/materials/" + target + "/summary")
				.header(HttpHeaders.AUTHORIZATION, bearer(asToken)).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("purpose", purpose, "materialContentConsent", confirmed))));
	}

	private ResultActions view(String purpose) throws Exception {
		return mockMvc.perform(get("/api/materials/" + material + "/summary").param("purpose", purpose)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
	}

	private long upload(String name, byte[] content) throws Exception {
		String response = mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", name, null, content))
						.param("title", name.substring(0, name.indexOf('.'))).param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private String signupAndLogin(String email) throws Exception {
		return TestApi.signupAndLogin(mockMvc, objectMapper, email,
				Map.of("agreedAiAnalysis", false, "agreedAiQuiz", true));
	}
}
