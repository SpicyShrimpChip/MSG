package com.msg.backend.domain.quiz.api;

import static com.msg.backend.support.TestApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.quiz.application.QuizFixtures;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.global.llm.LlmProvider;
import com.msg.backend.global.llm.LlmResponse;
import com.msg.backend.support.FakeLlmClients;
import com.msg.backend.support.FaultyFileStorage;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestApi;
import com.msg.backend.support.TestPdfs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
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
 * 학습자료 본문으로 만드는 퀴즈(관련 부분 고르기·문제별 원문 근거 검증)와 인지 수준(개정 블룸) 검증. 가짜 모델만 쓴다.
 */
@IntegrationTest
@TestPropertySource(properties = "app.material-content.enabled=true")
class MaterialQuizApiTest {

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
	private FaultyFileStorage faultyFileStorage;

	private String token;
	private long courseId;
	private long material;

	@BeforeEach
	void setUp() throws Exception {
		fakeLlm.resetAll();
		faultyFileStorage.reset();
		pool = Executors.newFixedThreadPool(2);
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = TestApi.signupAndLogin(mockMvc, objectMapper, "me@example.com",
				Map.of("agreedAiAnalysis", false, "agreedAiQuiz", true));
		courseId = TestApi.createCourse(mockMvc, objectMapper, token, "정보보호개론", List.of("RSA", "VLAN", "Firewall"));
		material = upload(TestPdfs.withPages("Course history and grading policy", "Office hours and contacts list",
				"RSA is a public key algorithm. RSA uses a key pair", "Exam schedule announcement",
				"VLAN splits broadcast domains"));
	}

	// 잠금 테스트의 두 요청을 공용 풀이 아닌 전용 스레드에서 보낸다 (기다리는 동안 공용 풀이 모자라지 않게)
	private ExecutorService pool;

	@AfterEach
	void tearDown() {
		faultyFileStorage.reset();
		pool.shutdownNow();
	}

	@Test
	void 개념이_나오는_쪽만_보내고_문제마다_원문_근거와_인지_수준을_결과에_보여_준다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));

		String created = create(body(List.of("RSA", "Firewall"), true)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(true))
				.andExpect(jsonPath("$.data.materialScope").value("p. 3"))
				.andExpect(jsonPath("$.data.bloomTargets", hasSize(4)))
				.andExpect(jsonPath("$.data.questions[0].bloomLevel").value("remember"))
				// 풀기 화면에는 근거 문장(정답 단서)을 싣지 않는다
				.andExpect(jsonPath("$.data.questions[0].source").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		long quizId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

		String sent = fakeLlm.get(LlmProvider.GPT).requests().get(0).user();
		assertThat(sent).contains("RSA is a public key algorithm", "[p. 3]", "bloomTargets", "minDistinctLevels")
				.doesNotContain("Course history", "Office hours", "Exam schedule", "VLAN splits");

		submitEmpty(quizId);
		mockMvc.perform(get("/api/quizzes/" + quizId + "/submissions/latest").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.results[0].bloomLevel").value("remember"))
				.andExpect(jsonPath("$.data.results[0].aiObjective").value(containsString("remember")))
				.andExpect(jsonPath("$.data.results[0].source.check").value("verified"))
				.andExpect(jsonPath("$.data.results[0].source.location").value("p. 3"))
				.andExpect(jsonPath("$.data.results[0].source.quote").value(containsString("RSA is a public key")))
				.andExpect(jsonPath("$.data.learningDesign.materialContentUsed").value(true))
				.andExpect(jsonPath("$.data.learningDesign.grounding.verified").value(2))
				.andExpect(jsonPath("$.data.learningDesign.missingConcepts[0]").value("Firewall"))
				.andExpect(jsonPath("$.data.learningDesign.promptVersion").value("quiz-gen-v6"));
	}

	@Test
	void AI가_적은_위치가_다르면_일부_일치로_낮추고_지어낸_근거가_절반을_넘으면_저장하지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(question("RSA uses a key pair", "p. 9") + "]}");
		create(body(List.of("RSA"), true, List.of("multiple_choice"), 1, List.of("remember")))
				.andExpect(status().isCreated());
		assertThat(jdbcTemplate.queryForObject("select source_check from quiz_question", String.class))
				.isEqualTo("PARTIAL");
		assertThat(jdbcTemplate.queryForObject("select source_location from quiz_question", String.class))
				.isEqualTo("p. 3");
		assertThat(jdbcTemplate.queryForObject("select source_note from quiz_question", String.class))
				.contains("p. 9");

		fakeLlm.get(LlmProvider.GPT).answer(question("RSA was invented on the moon", "p. 3") + "]}");
		create(body(List.of("RSA"), true, List.of("multiple_choice"), 1, List.of("remember")))
				.andExpect(status().isBadGateway());
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz", Integer.class)).isEqualTo(1);
	}

	@Test
	void 인지_수준을_검사한다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));
		// 창조는 서술형으로만
		create(body(List.of("RSA"), false, List.of("multiple_choice"), 2, List.of("create")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("창조")));
		create(body(List.of("RSA"), false, List.of("multiple_choice"), 2, List.of("memorize")))
				.andExpect(status().isBadRequest());
		// 고른 수준 밖의 문제는 형식 오류
		fakeLlm.get(LlmProvider.GPT).answer(question("RSA uses a key pair", "p. 3").replace("\"remember\"", "\"evaluate\"")
				.replace(",\"source\":{\"quote\":\"RSA uses a key pair\",\"location\":\"p. 3\"}", "") + "]}");
		create(body(List.of("RSA"), false, List.of("multiple_choice"), 1, List.of("remember")))
				.andExpect(status().isBadGateway());
	}

	@Test
	void 자료_본문을_쓰지_않으면_근거_없이_만들고_자료_기반으로_표시하지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));
		create(body(List.of("RSA"), false)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(false))
				.andExpect(jsonPath("$.data.materialScope").doesNotExist());
		assertThat(fakeLlm.get(LlmProvider.GPT).requests().get(0).user()).doesNotContain("RSA is a public key");
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_question where source_check is not null",
				Integer.class)).isZero();
	}

	@Test
	void 자료_본문은_요청마다_한_번만_읽고_같은_요청_키로_다시_보내면_읽지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));
		Map<String, Object> body = body(List.of("RSA"), true);
		faultyFileStorage.reset();

		long quizId = ((Number) JsonPath.read(create(body).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(true))
				.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
		assertThat(faultyFileStorage.openCount()).isEqualTo(1);

		create(body).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(quizId));
		assertThat(faultyFileStorage.openCount()).isEqualTo(1);
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(1);
	}

	@Test
	void 본문을_쓰지_않거나_요청이_틀리면_본문을_읽지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));
		faultyFileStorage.reset();
		create(body(List.of("RSA"), false)).andExpect(status().isCreated());
		Map<String, Object> noConsent = body(List.of("RSA"), true);
		noConsent.put("materialContentConsent", false);
		create(noConsent).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("자료 본문을 외부 AI로 보내려면 확인에 체크해 주세요."));
		create(body(List.of("없는개념"), true)).andExpect(status().isBadRequest());
		assertThat(faultyFileStorage.openCount()).isZero();
	}

	@Test
	void 본문을_읽는_동안_같은_사용자의_다른_요청을_막지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));
		CountDownLatch reading = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		faultyFileStorage.pauseNextOpen(reading, release);

		CompletableFuture<Integer> quiz = CompletableFuture.supplyAsync(() -> statusOf(() ->
				create(body(List.of("RSA"), true))), pool);
		assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();
		CompletableFuture<Integer> other = CompletableFuture.supplyAsync(() -> statusOf(() ->
				uploadRequest(TestPdfs.withPages("Another material"))), pool);
		try {
			assertThat(other.get(10, TimeUnit.SECONDS)).isEqualTo(201);
		} finally {
			release.countDown();
		}
		assertThat(quiz.get(30, TimeUnit.SECONDS)).isEqualTo(201);
		assertThat(jdbcTemplate.queryForObject("select material_content_used from quiz", Boolean.class)).isTrue();
	}

	@Test
	void 창조만_고르고_객관식을_섞으면_모델을_부르지_않고_400과_안내를_준다() throws Exception {
		create(body(List.of("RSA"), false, List.of("multiple_choice", "essay"), 2, List.of("create")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("객관식·주관식에 쓸 수준")));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
	}

	// 객관식 한 문제 (근거 문장·위치를 바꿔 넣는다)
	private static String question(String quote, String location) {
		return "{\"questions\":[{\"type\":\"multiple_choice\",\"concepts\":[\"RSA\"],\"bloomLevel\":\"remember\","
				+ "\"objective\":\"RSA의 성질을 떠올릴 수 있다\",\"text\":\"RSA에 대한 설명으로 옳은 것은?\","
				+ "\"choices\":[\"key pair\",\"대칭키\",\"해시\",\"VLAN\"],\"answerIndex\":0,"
				+ "\"source\":{\"quote\":\"" + quote + "\",\"location\":\"" + location + "\"}}";
	}

	private Map<String, Object> body(List<String> concepts, boolean useContent) {
		return body(concepts, useContent, List.of("multiple_choice", "short_answer"), 2, null);
	}

	private Map<String, Object> body(List<String> concepts, boolean useContent, List<String> types, int count,
			List<String> bloom) {
		Map<String, Object> body = new HashMap<>();
		body.put("requestKey", UUID.randomUUID().toString());
		body.put("courseId", courseId);
		body.put("mode", "exam_prep");
		body.put("questionTypes", types);
		body.put("questionCount", count);
		body.put("concepts", concepts);
		body.put("materialId", material);
		body.put("useMaterialContent", useContent);
		body.put("materialContentConsent", useContent);
		if (bloom != null) {
			body.put("bloomLevels", bloom);
		}
		return body;
	}

	private ResultActions create(Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
	}

	private void submitEmpty(long quizId) throws Exception {
		mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[]}"))
				.andExpect(status().is2xxSuccessful());
	}

	@FunctionalInterface
	private interface Request {
		ResultActions send() throws Exception;
	}

	private static int statusOf(Request request) {
		try {
			return request.send().andReturn().getResponse().getStatus();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private ResultActions uploadRequest(byte[] content) throws Exception {
		return mockMvc.perform(multipart("/api/materials")
				.file(new MockMultipartFile("file", "정보보호개론.pdf", null, content))
				.param("title", "정보보호개론").param("courseId", String.valueOf(courseId))
				.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private long upload(byte[] content) throws Exception {
		String response = uploadRequest(content)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}
}
