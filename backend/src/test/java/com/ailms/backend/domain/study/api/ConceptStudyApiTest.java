package com.ailms.backend.domain.study.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.ailms.backend.support.MutableClock;
import com.ailms.backend.support.TestFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 개념별 학습 기록(학습 완료)과 개념 확인 퀴즈, 복습 순환, 커리큘럼 진행률. 가짜 모델만 쓴다(실제 AI 결과가 아님).
 * 개념은 자료에서 뽑아야 하므로 자료 본문 전송이 켜진 설정으로 돈다. 가짜 문제의 객관식 정답은 0번이고, 주관식·서술형 채점 요청에는
 * 형식이 틀린 답을 줘서 채점 대기(PENDING)가 된다.
 */
@IntegrationTest
@TestPropertySource(properties = "app.material-content.enabled=true")
class ConceptStudyApiTest {

	private static final String PASSWORD = "Passw0rd!";
	private static final String CONCEPTS = "{\"concepts\":["
			+ "{\"name\":\"VLAN\",\"description\":\"망을 논리적으로 나눔\",\"quote\":\"VLAN은 브로드캐스트 도메인을 나눈다\"},"
			+ "{\"name\":\"트렁크\",\"description\":\"여러 VLAN을 한 링크로\",\"quote\":\"트렁크 포트는 태그를 붙인다\"}]}";

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
	// 테스트 시계는 멈춰 있다. 실제처럼 학습 완료·제출 사이에 시간이 흐르게 한다
	@Autowired
	private MutableClock clock;

	private String token;
	private long courseId;
	private long materialId;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		fakeLlm.resetAll();
		fakeLlm.get(LlmProvider.GPT).respond(this::answer);
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		courseId = createCourse(token);
		materialId = upload(token, courseId);
		mockMvc.perform(post("/api/materials/" + materialId + "/concepts").header(HttpHeaders.AUTHORIZATION,
						bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"materialContentConsent\":true}"))
				.andExpect(status().isOk());
	}

	// 개념 뽑기 → 개념, 퀴즈 만들기(typeCounts가 있는 요청) → 가짜 문제, 그 밖(채점) → 형식이 틀린 답
	private LlmResponse answer(LlmRequest request) {
		if (request.system().contains("핵심 개념을 고르는 조교")) {
			return new LlmResponse(CONCEPTS, 10, 10);
		}
		return new LlmResponse(request.user().contains("typeCounts") ? QuizFixtures.forRequest(request.user()) : "{}",
				10, 10);
	}

	@Test
	void 학습_완료는_한_번만_기록되고_다시_불러도_유지된다() throws Exception {
		progress(token).andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].conceptId").value("vlan"))
				.andExpect(jsonPath("$.data[0].status").value("not_studied"))
				.andExpect(jsonPath("$.data[0].studied").value(false))
				.andExpect(jsonPath("$.data[0].studyCount").value(0));

		String first = complete(token, "vlan").andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("studied"))
				.andExpect(jsonPath("$.data.studied").value(true))
				.andExpect(jsonPath("$.data.studyCount").value(1))
				.andReturn().getResponse().getContentAsString();
		// 중복 클릭: 기록은 하나, 횟수·처음 완료 시각 그대로
		complete(token, "vlan").andExpect(jsonPath("$.data.studyCount").value(1))
				.andExpect(jsonPath("$.data.firstCompletedAt")
						.value((String) JsonPath.read(first, "$.data.firstCompletedAt")));
		assertThat(jdbcTemplate.queryForObject("select count(*) from concept_study_progress", Integer.class))
				.isEqualTo(1);

		// 다시 불러와도 남아 있다
		progress(token).andExpect(jsonPath("$.data[0].status").value("studied"))
				.andExpect(jsonPath("$.data[1].status").value("not_studied"));
		// 학습 완료만으로는 퀴즈 결과·이해 확인이 생기지 않는다
		progress(token).andExpect(jsonPath("$.data[0].latestCheck").value(nullValue()));
	}

	@Test
	void 남의_자료_개념에는_기록할_수_없고_내_기록에_영향이_없다() throws Exception {
		complete(token, "vlan").andExpect(status().isOk());
		String other = signupAndLogin("other@example.com");

		complete(other, "vlan").andExpect(status().isNotFound());
		progress(other).andExpect(status().isNotFound());
		complete(token, "없는개념").andExpect(status().isNotFound());
		mockMvc.perform(get("/api/materials/" + materialId + "/concepts/progress")).andExpect(status().isUnauthorized());
		assertThat(jdbcTemplate.queryForObject("select count(*) from concept_study_progress", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void 확인_퀴즈는_그_개념만_다루고_복습_뒤_다시_풀면_예전_결과가_이력으로_남는다() throws Exception {
		complete(token, "vlan").andExpect(status().isOk());

		long quiz1 = createCheckQuiz("vlan", List.of("multiple_choice"), 2)
				.andExpect(jsonPath("$.data.title").value("VLAN 확인 퀴즈"))
				.andExpect(jsonPath("$.data.checkTopic.materialId").value(materialId))
				.andExpect(jsonPath("$.data.checkTopic.topicId").value("vlan"))
				.andExpect(jsonPath("$.data.checkTopic.name").value("VLAN"))
				.andReturn().getResponse().getContentAsString().transform(ConceptStudyApiTest::id);
		assertThat(lastPrompt()).contains("\"concepts\":[\"VLAN\"]");
		progress(token).andExpect(jsonPath("$.data[0].openCheckQuizId").value(quiz1));

		// 1/2 → 50% < 60% → 복습 필요
		submit(quiz1, List.of(0, 1));
		progress(token).andExpect(jsonPath("$.data[0].status").value("review_needed"))
				.andExpect(jsonPath("$.data[0].latestCheck.rate").value(50))
				.andExpect(jsonPath("$.data[0].latestCheck.result").value("review_needed"))
				.andExpect(jsonPath("$.data[0].openCheckQuizId").value(nullValue()));

		// 다시 공부하고 완료 → 학습 완료(2번째), 다시 풀어 2/2 → 이해 확인. 예전 결과는 이력에 남는다
		complete(token, "vlan").andExpect(jsonPath("$.data.status").value("studied"))
				.andExpect(jsonPath("$.data.studyCount").value(2));
		long quiz2 = createCheckQuiz("vlan", List.of("multiple_choice"), 2).andReturn().getResponse()
				.getContentAsString().transform(ConceptStudyApiTest::id);
		submit(quiz2, List.of(0, 0));
		progress(token).andExpect(jsonPath("$.data[0].status").value("understood"))
				.andExpect(jsonPath("$.data[0].studyCount").value(2))
				.andExpect(jsonPath("$.data[0].checkHistory", hasSize(2)))
				.andExpect(jsonPath("$.data[0].checkHistory[0].quizId").value(quiz2))
				.andExpect(jsonPath("$.data[0].checkHistory[1].result").value("review_needed"));
		// 이해 확인 뒤 다시 눌러도 바뀌지 않는다
		complete(token, "vlan").andExpect(jsonPath("$.data.status").value("understood"))
				.andExpect(jsonPath("$.data.studyCount").value(2));
		// 일반 퀴즈 목록에는 그대로 있다 (지우지 않음)
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz where check_concept_key = 'vlan'",
				Integer.class)).isEqualTo(2);
	}

	@Test
	void 채점_대기나_문항_부족이면_추가_확인_필요다() throws Exception {
		complete(token, "vlan").andExpect(status().isOk());
		long pending = createCheckQuiz("vlan", List.of("multiple_choice", "essay"), 2).andReturn().getResponse()
				.getContentAsString().transform(ConceptStudyApiTest::id);
		submitMixed(pending);
		progress(token).andExpect(jsonPath("$.data[0].status").value("needs_check"))
				.andExpect(jsonPath("$.data[0].latestCheck.waitingCount").value(1));

		complete(token, "트렁크").andExpect(status().isOk());
		long single = createCheckQuiz("트렁크", List.of("multiple_choice"), 1).andReturn().getResponse()
				.getContentAsString().transform(ConceptStudyApiTest::id);
		submit(single, List.of(0));
		progress(token).andExpect(jsonPath("$.data[1].status").value("needs_check"))
				.andExpect(jsonPath("$.data[1].latestCheck.gradedCount").value(1));
	}

	@Test
	void 확인_퀴즈는_자료와_뽑은_개념이_있어야_하고_모의시험과_함께_못_쓴다() throws Exception {
		Map<String, Object> noMaterial = checkBody("vlan", List.of("multiple_choice"), 2);
		noMaterial.remove("materialId");
		createQuiz(noMaterial).andExpect(status().isBadRequest());
		createCheckQuiz("ospf", List.of("multiple_choice"), 2).andExpect(status().isNotFound());
		Map<String, Object> mock = checkBody("vlan", List.of("multiple_choice"), 2);
		mock.put("mode", "exam_prep");
		mock.put("mockExam", true);
		createQuiz(mock).andExpect(status().isBadRequest());
	}

	@Test
	void 커리큘럼은_실제_학습_완료_기록으로_진행률과_단계를_정하고_복습으로_되돌아간다() throws Exception {
		// 사전 테스트: VLAN 맞힘, 트렁크 틀림 (개념은 자료에서 뽑은 개념)
		long pre = createAssessment("pre");
		submit(pre, List.of(0, 1));
		curriculum().andExpect(jsonPath("$.data.flow.currentStep").value("concept_study"))
				.andExpect(jsonPath("$.data.flow.progress.studiableConcepts").value(2))
				.andExpect(jsonPath("$.data.flow.progress.studiedConcepts").value(0))
				.andExpect(jsonPath("$.data.flow.progress.percent").value(0.0))
				.andExpect(jsonPath("$.data.flow.concepts[1].conceptId").value("트렁크"))
				.andExpect(jsonPath("$.data.flow.concepts[1].priority").value(true))
				.andExpect(jsonPath("$.data.flow.concepts[1].study.status").value("not_studied"));

		// 먼저 공부할 개념(트렁크)을 학습 완료 → 확인 퀴즈 단계, 진행률 50%
		complete(token, "트렁크");
		curriculum().andExpect(jsonPath("$.data.flow.currentStep").value("concept_quiz"))
				.andExpect(jsonPath("$.data.flow.progress.studiedConcepts").value(1))
				.andExpect(jsonPath("$.data.flow.progress.percent").value(50.0))
				.andExpect(jsonPath("$.data.flow.progress.lastStudiedConcept").value("트렁크"))
				.andExpect(jsonPath("$.data.flow.stages[1].summary")
						.value(org.hamcrest.Matchers.containsString("총 2개 개념 중 1개 학습 완료 (50.0%)")));

		// 확인 퀴즈를 틀리면 복습 필요 → 다시 개념 학습 단계
		long wrong = createCheckQuiz("트렁크", List.of("multiple_choice"), 2).andReturn().getResponse()
				.getContentAsString().transform(ConceptStudyApiTest::id);
		submit(wrong, List.of(1, 1));
		curriculum().andExpect(jsonPath("$.data.flow.currentStep").value("concept_study"))
				.andExpect(jsonPath("$.data.flow.concepts[1].study.status").value("review_needed"))
				.andExpect(jsonPath("$.data.flow.progress.reviewNeededConcepts").value(1));

		// 다시 공부하고 맞히면 사후 평가 단계
		complete(token, "트렁크");
		long right = createCheckQuiz("트렁크", List.of("multiple_choice"), 2).andReturn().getResponse()
				.getContentAsString().transform(ConceptStudyApiTest::id);
		submit(right, List.of(0, 0));
		curriculum().andExpect(jsonPath("$.data.flow.currentStep").value("post_test"))
				.andExpect(jsonPath("$.data.flow.progress.understoodConcepts").value(1))
				.andExpect(jsonPath("$.data.flow.progress.checkedConcepts").value(1));

		// 사후 평가는 기존 흐름 그대로 (사전과 같은 개념·문항 수)
		long postQuiz = createAssessment("post");
		submit(postQuiz, List.of(0, 0));
		curriculum().andExpect(jsonPath("$.data.flow.currentStep").value("done"))
				.andExpect(jsonPath("$.data.flow.overallDelta").value(50));
	}

	@Test
	void 자료나_과목을_지우면_학습_기록도_지워진다() throws Exception {
		complete(token, "vlan");
		long otherMaterial = upload(token, courseId);
		assertThat(jdbcTemplate.queryForObject("select count(*) from concept_study_progress", Integer.class))
				.isEqualTo(1);

		mockMvc.perform(delete("/api/materials/" + materialId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from concept_study_progress", Integer.class)).isZero();

		mockMvc.perform(post("/api/materials/" + otherMaterial + "/concepts").header(HttpHeaders.AUTHORIZATION,
						bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{\"materialContentConsent\":true}"))
				.andExpect(status().isOk());
		complete(token, "vlan", otherMaterial).andExpect(status().isOk());
		mockMvc.perform(delete("/api/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from concept_study_progress", Integer.class)).isZero();
	}

	@Test
	void 탈퇴하면_학습_기록과_확인_퀴즈가_지워진다() throws Exception {
		complete(token, "vlan");
		createCheckQuiz("vlan", List.of("multiple_choice"), 2).andExpect(status().isCreated());

		mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk());
		assertThat(jdbcTemplate.queryForObject("select count(*) from concept_study_progress", Integer.class)).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz where check_concept_key is not null",
				Integer.class)).isZero();
	}

	// ---------- 요청 ----------

	private ResultActions progress(String token) throws Exception {
		return mockMvc.perform(get("/api/materials/" + materialId + "/concepts/progress")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions complete(String token, String conceptId) throws Exception {
		return complete(token, conceptId, materialId);
	}

	private ResultActions complete(String token, String conceptId, long material) throws Exception {
		clock.advance(Duration.ofSeconds(1));
		return mockMvc.perform(post("/api/materials/" + material + "/concepts/progress")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("conceptId", conceptId))));
	}

	private ResultActions curriculum() throws Exception {
		return mockMvc.perform(get("/api/curriculum/courses/" + courseId).header(HttpHeaders.AUTHORIZATION,
				bearer(token))).andExpect(status().isOk());
	}

	private Map<String, Object> checkBody(String conceptId, List<String> types, int count) {
		Map<String, Object> body = new HashMap<>();
		body.put("requestKey", UUID.randomUUID().toString());
		body.put("courseId", courseId);
		body.put("materialId", materialId);
		body.put("checkConceptId", conceptId);
		body.put("mode", "self_development");
		body.put("questionTypes", types);
		body.put("questionCount", count);
		return body;
	}

	private ResultActions createCheckQuiz(String conceptId, List<String> types, int count) throws Exception {
		return createQuiz(checkBody(conceptId, types, count));
	}

	private ResultActions createQuiz(Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
	}

	private long createAssessment(String kind) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("requestKey", UUID.randomUUID().toString());
		body.put("materialId", materialId);
		body.put("kind", kind);
		body.put("questionTypes", List.of("multiple_choice"));
		body.put("questionCount", 2);
		return id(mockMvc.perform(post("/api/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
	}

	// 모두 객관식: 문제 순서대로 보기 번호
	private void submit(long quizId, List<Integer> choices) throws Exception {
		List<Map<String, Object>> answers = new ArrayList<>();
		List<Number> ids = questionIds(quizId);
		for (int i = 0; i < ids.size(); i++) {
			answers.add(Map.of("questionId", ids.get(i).longValue(), "choiceIndex", choices.get(i)));
		}
		send(quizId, answers);
	}

	// 객관식은 0번, 그 밖은 글로 답한다
	private void submitMixed(long quizId) throws Exception {
		String quiz = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(quiz, "$.data.questions[*].id");
		List<String> types = JsonPath.read(quiz, "$.data.questions[*].type");
		List<Map<String, Object>> answers = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			answers.add("multiple_choice".equals(types.get(i))
					? Map.of("questionId", ids.get(i).longValue(), "choiceIndex", 0)
					: Map.of("questionId", ids.get(i).longValue(), "text", "VLAN은 망을 나누는 기술이다"));
		}
		send(quizId, answers);
	}

	private List<Number> questionIds(long quizId) throws Exception {
		String quiz = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(quiz, "$.data.questions[*].id");
	}

	private void send(long quizId, List<Map<String, Object>> answers) throws Exception {
		clock.advance(Duration.ofSeconds(1));
		mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("answers", answers))))
				.andExpect(status().isCreated());
	}

	private String lastPrompt() {
		List<LlmRequest> requests = fakeLlm.get(LlmProvider.GPT).requests();
		return requests.get(requests.size() - 1).user();
	}

	private static long id(String response) {
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private long upload(String token, long course) throws Exception {
		String response = mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", "슬라이드.pptx", null, TestFiles.pptxWithSlides(
								"VLAN은 브로드캐스트 도메인을 나눈다", "트렁크 포트는 태그를 붙인다")))
						.param("title", "네트워크 슬라이드").param("courseId", String.valueOf(course))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(response);
	}

	private long createCourse(String token) throws Exception {
		String response = mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", "네트워크", "semester", "2026-2", "keywords", List.of("VLAN")))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return id(response);
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
