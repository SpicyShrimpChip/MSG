package com.ailms.backend.domain.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.ailms.backend.support.TestPdfs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 학습자료 핵심 개념 뽑기와 그 개념으로 만드는 사전·사후평가. 가짜 모델만 쓴다(실제 AI 결과가 아님).
 * 자료 본문 전송 기능이 켜진 설정(app.material-content.enabled=true)으로 돈다. 꺼진 기본값은 QuizSafetyApiTest가 확인한다.
 */
@IntegrationTest
@TestPropertySource(properties = "app.material-content.enabled=true")
class MaterialConceptApiTest {

	private static final String PASSWORD = "Passw0rd!";
	// 가짜 모델 답: 하나는 본문에 있고, 하나는 지어낸 근거, 하나는 본문에 없는 개념
	private static final String CONCEPTS = "{\"concepts\":["
			+ "{\"name\":\"VLAN\",\"description\":\"망을 논리적으로 나눔\",\"quote\":\"VLAN은 브로드캐스트 도메인을 나눈다\"},"
			+ "{\"name\":\"트렁크\",\"description\":\"여러 VLAN을 한 링크로\",\"quote\":\"트렁크 포트는 태그를 붙인다\"},"
			+ "{\"name\":\"OSPF\",\"description\":\"라우팅\",\"quote\":\"OSPF는 링크 상태 라우팅이다\"},"
			+ "{\"name\":\"서브넷\",\"description\":\"주소 범위\",\"quote\":\"없는 문장을 지어냈다\"}]}";

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

	private String token;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		fakeLlm.resetAll();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		courseId = createCourse(List.of("VLAN", "NAT"));
		fakeLlm.get(LlmProvider.GPT).respond(this::answer);
	}

	// 개념 뽑기 지시문이면 개념, 아니면 요청에 맞춘 가짜 문제
	private LlmResponse answer(LlmRequest request) {
		return new LlmResponse(request.system().contains("핵심 개념을 고르는 조교") ? CONCEPTS
				: QuizFixtures.forRequest(request.user()), 10, 10);
	}

	@Test
	void 본문에서_확인되는_개념만_근거_위치와_함께_저장하고_자료_상세에도_보인다() throws Exception {
		long material = upload("슬라이드.pptx", TestFiles.pptxWithSlides("네트워크 개요",
				"VLAN은 브로드캐스트 도메인을 나눈다", "트렁크 포트는 태그를 붙인다"));

		extract(material, true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].conceptId").value("vlan"))
				.andExpect(jsonPath("$.data[0].evidenceLocation").value("슬라이드 2"))
				.andExpect(jsonPath("$.data[0].courseKeyword").value(true))
				.andExpect(jsonPath("$.data[1].name").value("트렁크"))
				.andExpect(jsonPath("$.data[1].evidenceLocation").value("슬라이드 3"))
				.andExpect(jsonPath("$.data[1].courseKeyword").value(false));
		String sent = fakeLlm.get(LlmProvider.GPT).requests().get(0).user();
		assertThat(sent).contains("VLAN은 브로드캐스트 도메인을 나눈다", "courseKeywords");
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_ai_call where purpose = 'CONCEPTS'",
				Integer.class)).isEqualTo(1);

		mockMvc.perform(get("/api/materials/" + material).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.concepts", hasSize(2)))
				.andExpect(jsonPath("$.data.concepts[0].evidenceQuote").value("VLAN은 브로드캐스트 도메인을 나눈다"));
	}

	@Test
	void 텍스트_PDF에서_쪽_위치를_찾고_스캔_PDF는_422() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer("{\"concepts\":[{\"name\":\"NAT\",\"description\":\"d\","
				+ "\"quote\":\"NAT maps private addresses\"}]}");
		long pdf = upload("강의.pdf", TestPdfs.withPages("Intro page", "NAT maps private addresses"));
		long scanned = upload("스캔.pdf", TestPdfs.withPages((String) null));

		extract(pdf, true).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].evidenceLocation").value("p. 2"));
		extract(scanned, true).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.reason").value("NO_TEXT"))
				.andExpect(jsonPath("$.message").value(containsString("OCR")));
	}

	@Test
	void 확인_체크_AI_퀴즈_처리_동의_개인정보_남의_자료를_검사한다() throws Exception {
		long material = upload("노트.txt", "VLAN은 브로드캐스트 도메인을 나눈다".getBytes());
		long personal = upload("연락.txt", "VLAN 문의 me@example.com".getBytes());

		extract(material, false).andExpect(status().isBadRequest());
		extract(personal, true).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.reason").value("PERSONAL_INFO"));
		mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"aiQuiz\":false}")).andExpect(status().isOk());
		extract(material, true).andExpect(status().isForbidden());
		String other = signupAndLogin("other@example.com");
		mockMvc.perform(post("/api/materials/" + material + "/concepts").header(HttpHeaders.AUTHORIZATION, bearer(other))
						.contentType(MediaType.APPLICATION_JSON).content("{\"materialContentConsent\":true}"))
				.andExpect(status().isNotFound());
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
	}

	@Test
	void 지어낸_개념만_오면_저장하지_않고_502() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer("{\"concepts\":[{\"name\":\"OSPF\",\"quote\":\"OSPF는 없다\"}]}");
		long material = upload("노트.txt", "VLAN은 브로드캐스트 도메인을 나눈다".getBytes());

		extract(material, true).andExpect(status().isBadGateway());
		assertThat(jdbcTemplate.queryForObject("select count(*) from material_concept", Integer.class)).isZero();
	}

	@Test
	void 사전평가는_자료_개념으로_만들고_사후평가도_같은_개념이며_자료를_지우면_개념도_지워진다() throws Exception {
		long material = upload("슬라이드.pptx", TestFiles.pptxWithSlides("VLAN은 브로드캐스트 도메인을 나눈다",
				"트렁크 포트는 태그를 붙인다"));
		extract(material, true).andExpect(status().isOk());

		String pre = create(material, "pre").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.topicSource").value("material"))
				.andReturn().getResponse().getContentAsString();
		String prompt = lastPrompt();
		assertThat(prompt).contains("\"concepts\":[\"VLAN\",\"트렁크\"]");
		long preId = ((Number) JsonPath.read(pre, "$.data.id")).longValue();
		create(material, "post").andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.topicSource").value("material"))
				.andExpect(jsonPath("$.data.preAssessmentId").value(preId));
		assertThat(lastPrompt()).contains("\"concepts\":[\"VLAN\",\"트렁크\"]");

		mockMvc.perform(delete("/api/materials/" + material).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from material_concept", Integer.class)).isZero();
	}

	@Test
	void 개념_하나의_학습용_설명을_만들고_자료에_없는_연결_문장은_버린다() throws Exception {
		long material = upload("슬라이드.pptx", TestFiles.pptxWithSlides("네트워크 개요",
				"VLAN은 브로드캐스트 도메인을 나눈다", "트렁크 포트는 태그를 붙인다"));
		extract(material, true).andExpect(status().isOk());
		fakeLlm.get(LlmProvider.GPT).answer("{\"explanation\":\"VLAN은 스위치 하나를 여러 개의 논리적인 망으로 나누는 기술이다. "
				+ "같은 VLAN끼리만 브로드캐스트가 전달된다.\",\"materialContext\":\"트렁크와 함께 쓰인다\","
				+ "\"contextQuote\":\"트렁크 포트는 태그를 붙인다\",\"importance\":\"망 분리의 기본\",\"example\":\"층별로 VLAN을 나눈다\"}");

		explain(material, "vlan", true)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.conceptId").value("vlan"))
				.andExpect(jsonPath("$.data.evidenceQuote").value("VLAN은 브로드캐스트 도메인을 나눈다"))
				.andExpect(jsonPath("$.data.studyNote.explanation").value(containsString("논리적인 망")))
				.andExpect(jsonPath("$.data.studyNote.contextQuote").value("트렁크 포트는 태그를 붙인다"))
				.andExpect(jsonPath("$.data.studyNote.contextLocation").value("슬라이드 3"))
				.andExpect(jsonPath("$.data.studyNote.importance").value("망 분리의 기본"))
				.andExpect(jsonPath("$.data.studyNote.generatedBy").value("gpt"));
		String sent = lastPrompt();
		assertThat(sent).contains("\"name\":\"VLAN\"", "otherConcepts", "트렁크");
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_ai_call where purpose = 'CONCEPT_NOTE'",
				Integer.class)).isEqualTo(1);

		// 목록·자료 상세에도 설명이 붙는다. 지어낸 연결 문장은 저장하지 않는다
		fakeLlm.get(LlmProvider.GPT).answer("{\"explanation\":\"트렁크는 여러 VLAN의 프레임을 한 링크로 나르는 연결이다.\","
				+ "\"contextQuote\":\"자료에 없는 문장이다\"}");
		explain(material, "트렁크", true).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.studyNote.contextQuote").value(nullValue()))
				.andExpect(jsonPath("$.data.studyNote.example").value(nullValue()));
		mockMvc.perform(get("/api/materials/" + material).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.concepts[0].studyNote.importance").value("망 분리의 기본"));
	}

	@Test
	void 학습용_설명은_없는_개념_404_확인_없음_400_설명이_비면_502() throws Exception {
		long material = upload("노트.txt", "VLAN은 브로드캐스트 도메인을 나눈다".getBytes());
		extract(material, true).andExpect(status().isOk());

		explain(material, "ospf", true).andExpect(status().isNotFound());
		explain(material, "vlan", false).andExpect(status().isBadRequest());
		fakeLlm.get(LlmProvider.GPT).answer("{\"explanation\":\"짧음\"}");
		explain(material, "vlan", true).andExpect(status().isBadGateway());
		mockMvc.perform(get("/api/materials/" + material + "/concepts").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].studyNote").value(nullValue()));
	}

	private ResultActions explain(long material, String conceptId, boolean confirmed) throws Exception {
		return mockMvc.perform(post("/api/materials/" + material + "/concepts/study-notes")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(
						Map.of("conceptId", conceptId, "materialContentConsent", confirmed))));
	}

	private String lastPrompt() {
		List<LlmRequest> requests = fakeLlm.get(LlmProvider.GPT).requests();
		return requests.get(requests.size() - 1).user();
	}

	private ResultActions extract(long material, boolean confirmed) throws Exception {
		return mockMvc.perform(post("/api/materials/" + material + "/concepts")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content("{\"materialContentConsent\":" + confirmed + "}"));
	}

	private ResultActions create(long material, String kind) throws Exception {
		return mockMvc.perform(post("/api/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("requestKey", UUID.randomUUID().toString(),
						"materialId", material, "kind", kind))));
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

	private long createCourse(List<String> keywords) throws Exception {
		String response = mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", "네트워크", "semester", "2026-2", "keywords", keywords))))
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
