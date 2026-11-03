package com.ailms.backend.domain.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.quiz.application.QuizFixtures;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.global.llm.LlmProvider;
import com.ailms.backend.global.llm.LlmRequest;
import com.ailms.backend.global.llm.LlmResponse;
import com.ailms.backend.support.FakeLlmClients;
import com.ailms.backend.support.IntegrationTest;
import com.ailms.backend.support.TestFiles;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 학습자료 기반 사전·사후평가: 만들기(같은 개념·유형·문항 수), 자료 본문 사용 조건, 비교, AI 상태.
 * 모델은 가짜(FakeLlmClients)라 실제 제공사를 부르지 않는다. 가짜 문제는 실제 AI가 만든 것이 아니다.
 */
@IntegrationTest
@TestPropertySource(properties = "app.material-content.enabled=true")
class AssessmentApiTest {

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
	private FakeLlmClients fakeLlm;

	private String token;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		fakeLlm.resetAll();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		courseId = createCourse(token, List.of("NAT", "VLAN"));
	}

	@Test
	void AI가_하나도_없으면_상태는_미설정이고_만들기는_503() throws Exception {
		long material = upload(token, courseId, "노트.txt", "NAT는 주소를 바꾼다".getBytes(), "text/plain");

		mockMvc.perform(get("/api/assessments/ai-status").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.available").value(false))
				.andExpect(jsonPath("$.data.generation").value(nullValue()))
				.andExpect(jsonPath("$.data.message").value(containsString("AI 미설정")));
		create(token, assess(material, "pre")).andExpect(status().isServiceUnavailable());
	}

	@Test
	void 한_모델만_있으면_그_모델로_만들고_상태에_보인다() throws Exception {
		useGenerator(LlmProvider.CLAUDE);
		long material = upload(token, courseId, "노트.txt", "NAT".getBytes(), "text/plain");

		mockMvc.perform(get("/api/assessments/ai-status").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.available").value(true))
				.andExpect(jsonPath("$.data.generation.provider").value("claude"))
				.andExpect(jsonPath("$.data.grading", hasSize(1)));
		create(token, assess(material, "pre"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.generatedBy.provider").value("claude"));
	}

	@Test
	void 사전평가를_만들고_사후평가는_같은_개념_유형_문항_수로_만든다() throws Exception {
		useGenerator(LlmProvider.GPT);
		long material = upload(token, courseId, "노트.txt", "NAT와 VLAN 정리".getBytes(), "text/plain");

		create(token, assess(material, "post")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("먼저 사전평가")));

		create(token, assess(material, "pre"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.assessmentKind").value("pre"))
				.andExpect(jsonPath("$.data.title").value("노트 · 사전평가"))
				.andExpect(jsonPath("$.data.materialId").value(material))
				.andExpect(jsonPath("$.data.questions", hasSize(6)));

		Map<String, Object> post = assess(material, "post");
		post.put("questionTypes", List.of("essay"));
		post.put("questionCount", 2);
		String response = create(token, post)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.assessmentKind").value("post"))
				.andExpect(jsonPath("$.data.questions", hasSize(6)))
				.andExpect(jsonPath("$.data.questions[0].type").value("multiple_choice"))
				.andReturn().getResponse().getContentAsString();
		assertThat((Number) JsonPath.read(response, "$.data.preAssessmentId")).isNotNull();
		String prompt = fakeLlm.get(LlmProvider.GPT).requests().get(1).user();
		assertThat(prompt).contains("\"assessment\":\"post\"", "avoidQuestions", "NAT 문제 1");

		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].assessmentKind").value("post"))
				.andExpect(jsonPath("$.data[1].assessmentKind").value("pre"));
	}

	@Test
	void 비교는_최신_사전평가와_그_사후평가를_채점된_답으로_센다() throws Exception {
		useGenerator(LlmProvider.GPT);
		long material = upload(token, courseId, "노트.txt", "NAT".getBytes(), "text/plain");

		mockMvc.perform(get("/api/assessments/comparison").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.pre").value(nullValue()))
				.andExpect(jsonPath("$.data.post").value(nullValue()));

		long pre = id(create(token, assess(material, "pre")).andExpect(status().isCreated()));
		mockMvc.perform(get("/api/assessments/comparison").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.pre.quizId").value(pre))
				.andExpect(jsonPath("$.data.pre.submissionId").value(nullValue()));

		// 사전: 객관식 3문제 중 1개만 맞힘(보기 0이 정답), 주관식 3문제 다 맞힘 → 4/6
		submitAll(pre, List.of(0, 1, 1), "정답");
		long post = id(create(token, assess(material, "post")).andExpect(status().isCreated()));
		// 사후: 객관식·주관식 다 맞힘 → 6/6
		submitAll(post, List.of(0, 0, 0), "정답");

		mockMvc.perform(get("/api/assessments/comparison").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.material.id").value(material))
				.andExpect(jsonPath("$.data.pre.score").value(4))
				.andExpect(jsonPath("$.data.pre.maxScore").value(6))
				.andExpect(jsonPath("$.data.post.quizId").value(post))
				.andExpect(jsonPath("$.data.post.score").value(6))
				.andExpect(jsonPath("$.data.post.maxScore").value(6))
				.andExpect(jsonPath("$.data.concepts", hasSize(2)))
				.andExpect(jsonPath("$.data.concepts[0].conceptId").value("nat"))
				.andExpect(jsonPath("$.data.concepts[0].postScore").value(3))
				// NAT·VLAN 모두 2/3(67%) → 3/3(100%)
				.andExpect(jsonPath("$.data.concepts[0].delta").value(33))
				.andExpect(jsonPath("$.data.concepts[0].status").value("improved"))
				.andExpect(jsonPath("$.data.concepts[1].delta").value(33))
				.andExpect(jsonPath("$.data.overallDelta").value(33))
				.andExpect(jsonPath("$.data.comparability.sameConcepts").value(true))
				.andExpect(jsonPath("$.data.comparability.sameQuestionCount").value(true))
				.andExpect(jsonPath("$.data.basis").value(containsString("최신 사전평가")));

		String other = signupAndLogin("other@example.com");
		mockMvc.perform(get("/api/assessments/comparison").param("materialId", String.valueOf(material))
						.header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound());
		create(other, assess(material, "pre")).andExpect(status().isNotFound());
	}

	@Test
	void 자료_본문은_확인했을_때만_보내고_읽을_수_없거나_개인정보가_있으면_보내지_않는다() throws Exception {
		useGenerator(LlmProvider.GPT);
		long text = upload(token, courseId, "노트.md", "# NAT\nPAT는 포트로 단말을 나눈다".getBytes(), "text/markdown");
		long slides = upload(token, courseId, "슬라이드.pptx",
				TestFiles.pptxWithSlides("VLAN은 브로드캐스트 도메인을 나눈다", "트렁크 &amp; 태그"), null);
		long pdf = upload(token, courseId, "노트.pdf", TestFiles.pdf(), "application/pdf");
		long personal = upload(token, courseId, "연락처.txt", "문의 me@example.com".getBytes(), "text/plain");

		Map<String, Object> noConsent = assess(text, "pre");
		noConsent.put("useMaterialContent", true);
		create(token, noConsent).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("확인에 체크")));

		create(token, withContent(text)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(true))
				.andExpect(jsonPath("$.data.materialContentNote").value(nullValue()));
		assertThat(lastPrompt()).contains("materialExcerpt", "PAT는 포트로 단말을 나눈다");

		create(token, withContent(slides)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(true));
		assertThat(lastPrompt()).contains("[슬라이드 1]", "VLAN은 브로드캐스트 도메인을 나눈다", "트렁크 & 태그");

		// 글 없는 PDF(테스트 파일): 본문을 쓰지 않고 이유를 준다
		create(token, withContent(pdf)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(false))
				.andExpect(jsonPath("$.data.materialContentNote").value(org.hamcrest.Matchers.oneOf("NO_TEXT", "FAILED")));
		assertThat(lastPrompt()).doesNotContain("materialExcerpt");
		long textPdf = upload(token, courseId, "강의.pdf",
				com.ailms.backend.support.TestPdfs.withPages("NAT maps private addresses"), "application/pdf");
		create(token, withContent(textPdf)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(true));
		assertThat(lastPrompt()).contains("[p. 1]", "NAT maps private addresses");

		create(token, withContent(personal)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentNote").value("PERSONAL_INFO"));
		assertThat(lastPrompt()).doesNotContain("me@example.com");
	}

	@Test
	void 과목에_연결되지_않은_자료로는_평가를_만들_수_없다() throws Exception {
		useGenerator(LlmProvider.GPT);
		String response = mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", "노트.txt", "text/plain", "NAT".getBytes()))
						.param("title", "노트").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long material = ((Number) JsonPath.read(response, "$.data.id")).longValue();

		create(token, assess(material, "pre")).andExpect(status().isBadRequest());
	}

	// ---------- 도우미 ----------

	private void useGenerator(LlmProvider provider) {
		fakeLlm.get(provider).respond(this::generate);
	}

	private LlmResponse generate(LlmRequest request) {
		return new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10);
	}

	private String lastPrompt() {
		List<LlmRequest> requests = fakeLlm.get(LlmProvider.GPT).requests();
		return requests.get(requests.size() - 1).user();
	}

	private Map<String, Object> withContent(long material) {
		Map<String, Object> body = assess(material, "pre");
		body.put("useMaterialContent", true);
		body.put("materialContentConsent", true);
		return body;
	}

	// 객관식은 순서대로 choices, 주관식은 text
	private void submitAll(long quizId, List<Integer> choices, String text) throws Exception {
		String quiz = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(quiz, "$.data.questions[*].id");
		List<String> types = JsonPath.read(quiz, "$.data.questions[*].type");
		List<Map<String, Object>> answers = new java.util.ArrayList<>();
		int mc = 0;
		for (int i = 0; i < ids.size(); i++) {
			answers.add("multiple_choice".equals(types.get(i))
					? Map.of("questionId", ids.get(i).longValue(), "choiceIndex", choices.get(mc++))
					: Map.of("questionId", ids.get(i).longValue(), "text", text));
		}
		mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("answers", answers))))
				.andExpect(status().isCreated());
	}

	private ResultActions create(String token, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
	}

	private static Map<String, Object> assess(long materialId, String kind) {
		Map<String, Object> body = new HashMap<>();
		body.put("requestKey", UUID.randomUUID().toString());
		body.put("materialId", materialId);
		body.put("kind", kind);
		return body;
	}

	private static long id(ResultActions actions) throws Exception {
		return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
	}

	private long upload(String token, long course, String name, byte[] content, String type) throws Exception {
		String title = name.substring(0, name.indexOf('.'));
		String response = mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", name, type, content))
						.param("title", title).param("courseId", String.valueOf(course))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private long createCourse(String token, List<String> keywords) throws Exception {
		String response = mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", "네트워크 보안", "semester", "2026-2", "keywords", keywords))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private String signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "name", "테스터", "password",
								PASSWORD, "passwordConfirm", PASSWORD, "agreedTerms", true, "agreedPrivacy", true,
								"agreedAiAnalysis", false, "agreedAiQuiz", true))))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
