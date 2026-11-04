package com.ailms.backend.domain.curriculum.api;

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
import com.ailms.backend.global.llm.LlmResponse;
import com.ailms.backend.support.FakeLlmClients;
import com.ailms.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 커리큘럼 4단계 흐름 (사전 테스트 → 핵심 개념 학습 → 개념별 확인 퀴즈 → 사후 평가). 가짜 모델로 만든 문제만 쓴다.
 * 가짜 문제는 개념을 순서대로 돌아가며 붙이고 객관식 정답은 0번이다(QuizFixtures).
 */
@IntegrationTest
class CurriculumFlowApiTest {

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
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 10, 10));
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		courseId = createCourse(List.of("VLAN", "NAT"));
	}

	@Test
	void 사전_테스트부터_사후_평가까지_지금_단계가_차례로_바뀐다() throws Exception {
		long material = upload("노트.txt");

		// 1. 아무것도 안 했으면 사전 테스트부터. 개념은 과목 키워드
		curriculum()
				.andExpect(jsonPath("$.data.flow.currentStep").value("pre_test"))
				.andExpect(jsonPath("$.data.flow.stages", hasSize(4)))
				.andExpect(jsonPath("$.data.flow.stages[0].status").value("current"))
				.andExpect(jsonPath("$.data.flow.stages[3].status").value("upcoming"))
				.andExpect(jsonPath("$.data.flow.conceptSource").value("course"))
				.andExpect(jsonPath("$.data.flow.assessmentMaterial.id").value(material))
				.andExpect(jsonPath("$.data.flow.preTest.status").value("none"))
				.andExpect(jsonPath("$.data.flow.concepts", hasSize(2)))
				.andExpect(jsonPath("$.data.flow.concepts[0].preStatus").value("not_taken"));

		// 2. 사전 테스트: VLAN 맞힘, NAT 틀림 → 개념 학습 단계, NAT가 먼저 공부할 개념
		long pre = createAssessment(material, "pre");
		curriculum().andExpect(jsonPath("$.data.flow.preTest.status").value("not_submitted"))
				.andExpect(jsonPath("$.data.flow.currentStep").value("pre_test"));
		submit(pre, List.of(0, 1));
		curriculum()
				.andExpect(jsonPath("$.data.flow.currentStep").value("concept_study"))
				.andExpect(jsonPath("$.data.flow.stages[0].status").value("done"))
				.andExpect(jsonPath("$.data.flow.preTest.status").value("submitted"))
				.andExpect(jsonPath("$.data.flow.preTest.rate").value(50))
				.andExpect(jsonPath("$.data.flow.concepts[0].conceptId").value("vlan"))
				.andExpect(jsonPath("$.data.flow.concepts[0].preStatus").value("ok"))
				.andExpect(jsonPath("$.data.flow.concepts[0].priority").value(false))
				.andExpect(jsonPath("$.data.flow.concepts[1].conceptId").value("nat"))
				.andExpect(jsonPath("$.data.flow.concepts[1].preStatus").value("weak"))
				.andExpect(jsonPath("$.data.flow.concepts[1].priority").value(true))
				.andExpect(jsonPath("$.data.flow.concepts[1].practiceStatus").value("not_started"));

		// 3. NAT 확인 퀴즈를 맞히면 먼저 공부할 개념을 다 확인했으므로 사후 평가 단계
		long practice = createQuiz("NAT");
		submit(practice, List.of(0));
		curriculum()
				.andExpect(jsonPath("$.data.flow.currentStep").value("post_test"))
				.andExpect(jsonPath("$.data.flow.concepts[1].practiceStatus").value("ok"))
				.andExpect(jsonPath("$.data.flow.concepts[1].practiceGradedCount").value(1))
				.andExpect(jsonPath("$.data.flow.postTest.status").value("none"))
				.andExpect(jsonPath("$.data.flow.overallDelta").value(nullValue()));

		// 4. 사후 평가를 다 맞히면 끝. 향상 폭은 사전과 비교한 서버 계산 값
		long post = createAssessment(material, "post");
		submit(post, List.of(0, 0));
		curriculum()
				.andExpect(jsonPath("$.data.flow.currentStep").value("done"))
				.andExpect(jsonPath("$.data.flow.stages[3].status").value("done"))
				.andExpect(jsonPath("$.data.flow.postTest.rate").value(100))
				.andExpect(jsonPath("$.data.flow.overallDelta").value(50))
				.andExpect(jsonPath("$.data.flow.concepts[1].postRate").value(100))
				.andExpect(jsonPath("$.data.flow.concepts[1].delta").value(100));
	}

	@Test
	void 자료가_없으면_사전_테스트_자료가_비어_있고_안내한다() throws Exception {
		curriculum()
				.andExpect(jsonPath("$.data.flow.assessmentMaterial").value(nullValue()))
				.andExpect(jsonPath("$.data.flow.stages[0].summary")
						.value(org.hamcrest.Matchers.containsString("자료를 올리면")));
	}

	private ResultActions curriculum() throws Exception {
		return mockMvc.perform(get("/api/curriculum/courses/" + courseId).header(HttpHeaders.AUTHORIZATION,
				bearer(token))).andExpect(status().isOk());
	}

	private long createAssessment(long material, String kind) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("requestKey", UUID.randomUUID().toString());
		body.put("materialId", material);
		body.put("kind", kind);
		body.put("questionTypes", List.of("multiple_choice"));
		body.put("questionCount", 2);
		return id(mockMvc.perform(post("/api/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated()));
	}

	private long createQuiz(String concept) throws Exception {
		Map<String, Object> body = Map.of("requestKey", UUID.randomUUID().toString(), "courseId", courseId, "mode",
				"self_development", "questionTypes", List.of("multiple_choice"), "questionCount", 1, "concepts",
				List.of(concept));
		return id(mockMvc.perform(post("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated()));
	}

	// 문제 순서대로 보기 번호를 고른다 (모두 객관식)
	private void submit(long quizId, List<Integer> choices) throws Exception {
		String quiz = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(quiz, "$.data.questions[*].id");
		List<Map<String, Object>> answers = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			answers.add(Map.of("questionId", ids.get(i).longValue(), "choiceIndex", choices.get(i)));
		}
		mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("answers", answers))))
				.andExpect(status().isCreated());
	}

	private static long id(ResultActions actions) throws Exception {
		return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
	}

	private long upload(String name) throws Exception {
		String response = mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", name, "text/plain", "VLAN과 NAT 정리".getBytes()))
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
