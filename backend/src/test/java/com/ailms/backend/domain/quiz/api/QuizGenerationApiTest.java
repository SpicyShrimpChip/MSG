package com.ailms.backend.domain.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AI 퀴즈 만들기 → 풀기 → 채점(객관식·주관식·서술형) → 결과 판단·개념별 집계.
 * 모델은 가짜(FakeLlmClients)라 실제 제공사를 부르지 않는다. 가짜 모델이 돌려주는 문제·채점은 테스트용 글이다.
 */
@IntegrationTest
class QuizGenerationApiTest {

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
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private FakeLlmClients fakeLlm;

	private String token;
	private String otherToken;
	private long courseId;

	// 서술형 채점: 모델마다 기준별 수준 (테스트가 정한다)
	private final Map<LlmProvider, List<String>> essayLevels = new HashMap<>();
	// 주관식 의미 판단: 모델마다 verdict
	private final Map<LlmProvider, String> shortVerdicts = new HashMap<>();

	@BeforeEach
	void setUp() throws Exception {
		fakeLlm.resetAll();
		essayLevels.clear();
		shortVerdicts.clear();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com", true);
		otherToken = signupAndLogin("other@example.com", true);
		courseId = createCourse(token, "네트워크 보안", List.of("NAT", "VLAN"));
	}

	// ---------- 만들기 ----------

	@Test
	void AI_퀴즈_처리_동의가_없으면_403이고_모델을_부르지_않는다() throws Exception {
		String noConsent = signupAndLogin("no@example.com", false);
		long course = createCourse(noConsent, "네트워크", List.of("NAT"));
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create(noConsent, body(course, "exam_prep", List.of("multiple_choice"), 1))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(containsString("AI 퀴즈 처리 동의")));

		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
		assertThat(count("quiz_generation")).isZero();
	}

	@Test
	void 연결된_모델이_없으면_503이고_가짜_문제를_만들지_않는다() throws Exception {
		create(token, body(courseId, "exam_prep", List.of("multiple_choice"), 1))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("연결된 AI 모델이 없어 지금은 퀴즈를 만들 수 없어요."));

		assertThat(count("quiz")).isZero();
	}

	@Test
	void 시험_대비_퀴즈를_만들고_풀기_화면에는_정답_정보가_없다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		Map<String, Object> request = body(courseId, "exam_prep", List.of("essay", "short_answer", "multiple_choice"), 3);
		request.put("examScope", "3~5주차 NAT");

		String created = create(token, request)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.title").value("네트워크 보안 · 시험 대비 예상 문제"))
				.andExpect(jsonPath("$.data.mode").value("exam_prep"))
				.andExpect(jsonPath("$.data.difficulty").value("medium"))
				.andExpect(jsonPath("$.data.examScope").value("3~5주차 NAT"))
				.andExpect(jsonPath("$.data.generatedBy.provider").value("gpt"))
				.andExpect(jsonPath("$.data.materialContentUsed").value(false))
				.andExpect(jsonPath("$.data.questions", hasSize(3)))
				.andExpect(jsonPath("$.data.questions[0].type").value("multiple_choice"))
				.andExpect(jsonPath("$.data.questions[1].type").value("short_answer"))
				.andExpect(jsonPath("$.data.questions[2].type").value("essay"))
				.andExpect(jsonPath("$.data.questions[2].maxScore").value(6))
				.andReturn().getResponse().getContentAsString();
		long quizId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

		String solve = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(solve).doesNotContain("answerIndex", "correct", "acceptedAnswers", "NAPT", "modelAnswer",
				"일대일", "차이 설명", "aiExplanation");

		// 모델에 보낸 글: 과목·개념·유형별 수·시험 범위만 (출제 보장 금지 지시 포함)
		LlmRequest sent = fakeLlm.get(LlmProvider.GPT).requests().get(0);
		assertThat(sent.user()).contains("네트워크 보안", "NAT", "VLAN", "\"multiple_choice\":1", "3~5주차 NAT");
		assertThat(sent.system()).contains("출제 가능성은 추정");
		assertThat(jdbcTemplate.queryForObject("select status from quiz_generation", String.class))
				.isEqualTo("SUCCEEDED");

		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].mode").value("exam_prep"))
				.andExpect(jsonPath("$.data[0].aiGenerated").value(true))
				.andExpect(jsonPath("$.data[0].questionCount").value(3));
	}

	@Test
	void 같은_요청_키로_다시_보내면_새로_만들지_않고_같은_퀴즈를_200으로_준다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		Map<String, Object> request = body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3);

		long first = id(create(token, request).andExpect(status().isCreated()));
		long second = id(create(token, request).andExpect(status().isOk()));

		assertThat(second).isEqualTo(first);
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(1);
		assertThat(count("quiz")).isEqualTo(1);
	}

	@Test
	void 형식이_틀리면_다음_모델로_넘어가고_모두_틀리면_502이고_아무것도_저장하지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer("{\"questions\":[]}");
		fakeLlm.get(LlmProvider.CLAUDE).answer(QuizFixtures.threeTypes(false));

		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.generatedBy.provider").value("claude"));

		fakeLlm.get(LlmProvider.CLAUDE).answer("문제를 만들 수 없어요");
		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.message").value(containsString("형식에 맞지 않아 저장하지 않았어요")));

		assertThat(count("quiz")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_generation where status = 'FAILED'"
				+ " and error_code = 'INVALID_OUTPUT'", Integer.class)).isEqualTo(1);
	}

	@Test
	void 요청_값을_검사한다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 2))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("고른 유형이 3가지라 문제 수는 3개 이상이어야 해요."));
		create(token, body(courseId, "exam_prep", List.of("multiple_choice"), 11))
				.andExpect(status().isBadRequest());
		create(token, body(courseId, "cram", List.of("multiple_choice"), 1))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("학습 모드를 다시 골라 주세요."));
		create(token, body(courseId, "exam_prep", List.of("true_false"), 1))
				.andExpect(status().isBadRequest());
		Map<String, Object> unknownConcept = body(courseId, "exam_prep", List.of("multiple_choice"), 1);
		unknownConcept.put("concepts", List.of("OSPF"));
		create(token, unknownConcept).andExpect(status().isBadRequest());
		Map<String, Object> personal = body(courseId, "exam_prep", List.of("multiple_choice"), 1);
		personal.put("examScope", "교수님 연락처 010-1234-5678");
		create(token, personal).andExpect(status().isBadRequest());
		create(otherToken, body(courseId, "exam_prep", List.of("multiple_choice"), 1))
				.andExpect(status().isNotFound());

		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
	}

	@Test
	void 업무_활용은_내_관심_직무로_상황_문제를_만들고_평가_기준_이름을_미리_보여_준다() throws Exception {
		mockMvc.perform(patch("/api/users/me/profile").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("learningPurpose", "work", "jobField", "it_consulting"))))
				.andExpect(status().isOk());
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(true));

		long quizId = id(create(token, body(courseId, "work", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.mode").value("work"))
				.andExpect(jsonPath("$.data.jobField").value("it_consulting")));

		assertThat(fakeLlm.get(LlmProvider.GPT).requests().get(0).user()).contains("IT 컨설팅");
		mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.questions[2].type").value("essay"))
				.andExpect(jsonPath("$.data.questions[2].scenario").value(containsString("방문객 Wi-Fi")))
				.andExpect(jsonPath("$.data.questions[2].role").value("IT 컨설턴트"))
				.andExpect(jsonPath("$.data.questions[2].criteria", hasSize(2)))
				.andExpect(jsonPath("$.data.questions[0].criteria", hasSize(0)));
	}

	// ---------- 제출·채점 ----------

	@Test
	void 객관식은_코드_주관식은_허용_답_일치_서술형은_세_모델_다수_의견과_코드_합산으로_채점한다() throws Exception {
		long quizId = generate();
		enableGraders();
		essayLevels.put(LlmProvider.GPT, List.of("met", "partial"));
		essayLevels.put(LlmProvider.CLAUDE, List.of("met", "partial"));
		essayLevels.put(LlmProvider.GEMINI, List.of("partial", "not_met"));
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(0), "choiceIndex", 1),
				Map.of("questionId", ids.get(1), "text", " napt "),
				Map.of("questionId", ids.get(2), "text", "Static NAT는 하나씩 짝짓고 PAT는 포트로 나눈다.")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[0].correct").value(true))
				.andExpect(jsonPath("$.data.results[0].gradingMethod").value("code"))
				.andExpect(jsonPath("$.data.results[1].correct").value(true))
				.andExpect(jsonPath("$.data.results[1].gradingMethod").value("match"))
				.andExpect(jsonPath("$.data.results[1].acceptedAnswers[0]").value("PAT"))
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("graded"))
				.andExpect(jsonPath("$.data.results[2].gradingMethod").value("ai"))
				.andExpect(jsonPath("$.data.results[2].score").value(5))
				.andExpect(jsonPath("$.data.results[2].maxScore").value(6))
				.andExpect(jsonPath("$.data.results[2].rubric[0].level").value("met"))
				.andExpect(jsonPath("$.data.results[2].rubric[0].earned").value(4))
				.andExpect(jsonPath("$.data.results[2].rubric[0].agreeCount").value(2))
				.andExpect(jsonPath("$.data.results[2].rubric[0].judgedCount").value(3))
				.andExpect(jsonPath("$.data.results[2].rubric[1].earned").value(1))
				.andExpect(jsonPath("$.data.results[2].aiFeedback.strengths").value("잘한 점"))
				.andExpect(jsonPath("$.data.results[2].aiModelAnswer").value(containsString("일대일")))
				.andExpect(jsonPath("$.data.score").value(7))
				.andExpect(jsonPath("$.data.maxScore").value(8))
				.andExpect(jsonPath("$.data.correctCount").value(2))
				.andExpect(jsonPath("$.data.assessment.status").value("ready"))
				.andExpect(jsonPath("$.data.assessment.byType", hasSize(3)))
				.andExpect(jsonPath("$.data.assessment.byConcept[0].conceptId").value("nat"));

		// 답 원문은 일치한 주관식이 아닌 서술형만 모델로 갔다
		String gradingPrompt = fakeLlm.get(LlmProvider.GPT).requests().get(1).user();
		assertThat(gradingPrompt).contains("PAT는 포트로").doesNotContain("napt");
	}

	@Test
	void 주관식이_허용_답과_다르면_AI_의미_판단_과반으로_정하고_엇갈리면_확인_필요() throws Exception {
		long quizId = generate();
		enableGraders();
		essayLevels.put(LlmProvider.GPT, List.of("met", "met"));
		essayLevels.put(LlmProvider.CLAUDE, List.of("partial", "met"));
		essayLevels.put(LlmProvider.GEMINI, List.of("not_met", "met"));
		shortVerdicts.put(LlmProvider.GPT, "correct");
		shortVerdicts.put(LlmProvider.CLAUDE, "correct");
		shortVerdicts.put(LlmProvider.GEMINI, "incorrect");
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(1), "text", "포트 주소 변환"),
				Map.of("questionId", ids.get(2), "text", "차이를 설명한다")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[0].choiceIndex").value(-1))
				.andExpect(jsonPath("$.data.results[0].correct").value(false))
				.andExpect(jsonPath("$.data.results[1].correct").value(true))
				.andExpect(jsonPath("$.data.results[1].gradingMethod").value("ai"))
				.andExpect(jsonPath("$.data.results[1].aiJudgedCount").value(3))
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("needs_review"))
				.andExpect(jsonPath("$.data.results[2].rubric[0].level").value(nullValue()))
				.andExpect(jsonPath("$.data.results[2].gradingMessage").value(containsString("엇갈려")))
				.andExpect(jsonPath("$.data.reviewCount").value(1))
				.andExpect(jsonPath("$.data.assessment.status").value("needs_check"));
	}

	@Test
	void 모델이_없으면_채점_완료로_치지_않고_대기로_두고_모델이_생기면_다시_채점한다() throws Exception {
		long quizId = generate();
		fakeLlm.resetAll();
		List<Long> ids = questionIds(quizId);

		String result = submit(quizId, List.of(Map.of("questionId", ids.get(0), "choiceIndex", 1),
				Map.of("questionId", ids.get(1), "text", "PAT"),
				Map.of("questionId", ids.get(2), "text", "PAT는 포트로 여러 단말을 나눈다")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("pending"))
				.andExpect(jsonPath("$.data.results[2].gradingMessage").value(containsString("연결된 AI 모델이 없어")))
				.andExpect(jsonPath("$.data.results[2].score").value(0))
				.andExpect(jsonPath("$.data.pendingCount").value(1))
				.andExpect(jsonPath("$.data.score").value(2))
				.andExpect(jsonPath("$.data.maxScore").value(2))
				.andExpect(jsonPath("$.data.assessment.status").value("needs_check"))
				.andReturn().getResponse().getContentAsString();
		long submissionId = ((Number) JsonPath.read(result, "$.data.submissionId")).longValue();

		enableGraders();
		essayLevels.put(LlmProvider.GPT, List.of("met", "met"));
		essayLevels.put(LlmProvider.CLAUDE, List.of("met", "met"));
		essayLevels.put(LlmProvider.GEMINI, List.of("met", "partial"));
		mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions/" + submissionId + "/grading")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("graded"))
				.andExpect(jsonPath("$.data.results[2].score").value(6))
				.andExpect(jsonPath("$.data.pendingCount").value(0))
				.andExpect(jsonPath("$.data.score").value(8));

		mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions/" + submissionId + "/grading")
						.header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(status().isNotFound());
	}

	@Test
	void 동의를_철회하면_AI_채점을_하지_않고_대기로_둔다() throws Exception {
		long quizId = generate();
		enableGraders();
		mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content("{\"aiQuiz\":false}")).andExpect(status().isOk());
		List<Long> ids = questionIds(quizId);
		int before = fakeLlm.get(LlmProvider.GPT).requests().size();

		submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "PAT는 포트로 나눈다")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("pending"))
				.andExpect(jsonPath("$.data.results[2].gradingMessage").value(containsString("동의하지 않아")));

		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(before);
	}

	@Test
	void AI로_보낼_답에_개인정보가_있으면_422이고_저장하지_않는다_IP_예시는_막지_않는다() throws Exception {
		long quizId = generate();
		enableGraders();
		essayLevels.put(LlmProvider.GPT, List.of("met", "met"));
		essayLevels.put(LlmProvider.CLAUDE, List.of("met", "met"));
		essayLevels.put(LlmProvider.GEMINI, List.of("met", "met"));
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "제 메일 me@example.com 으로 연락 주세요")))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.data.questionIds[0]").value(ids.get(2)));
		assertThat(count("quiz_submission")).isZero();

		submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "192.168.0.0/24 대역을 PAT로 나눈다")))
				.andExpect(status().isCreated());
	}

	@Test
	void 객관식이_아닌_문제에_보기_번호를_보내면_400() throws Exception {
		long quizId = generate();
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(2), "choiceIndex", 0))).andExpect(status().isBadRequest());
	}

	// ---------- 개념별 결과와 피드백 연결 ----------

	@Test
	void 개념별_퀴즈_결과와_이미_알아요_확인_결과를_준다_피드백만으로는_바뀌지_않는다() throws Exception {
		long quizId = generate();
		mockMvc.perform(put("/api/analysis/courses/" + courseId + "/feedback")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"concept\":\"NAT\",\"feedback\":\"already_know\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/quizzes/concept-stats").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].conceptId").value("nat"))
				.andExpect(jsonPath("$.data[0].questionCount").value(0))
				.andExpect(jsonPath("$.data[0].feedback").value("already_know"))
				.andExpect(jsonPath("$.data[0].confirmation").value("not_taken"))
				.andExpect(jsonPath("$.data[1].feedback").value(nullValue()));

		List<Long> ids = questionIds(quizId);
		fakeLlm.resetAll();
		submit(quizId, List.of(Map.of("questionId", ids.get(0), "choiceIndex", 1),
				Map.of("questionId", ids.get(1), "text", "PAT"))).andExpect(status().isCreated());

		mockMvc.perform(get("/api/quizzes/concept-stats").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].questionCount").value(3))
				.andExpect(jsonPath("$.data[0].gradedCount").value(3))
				.andExpect(jsonPath("$.data[0].score").value(2))
				.andExpect(jsonPath("$.data[0].maxScore").value(8))
				.andExpect(jsonPath("$.data[0].weak").value(true))
				.andExpect(jsonPath("$.data[0].confirmation").value("not_confirmed"))
				.andExpect(jsonPath("$.data[1].conceptId").value("vlan"))
				.andExpect(jsonPath("$.data[1].questionCount").value(1));

		mockMvc.perform(get("/api/quizzes/concept-stats").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(status().isNotFound());
	}

	// ---------- 도우미 ----------

	// GPT로 시험 대비 3문제(객관식·주관식·서술형)를 만든다
	private long generate() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		return id(create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isCreated()));
	}

	// 세 모델 모두 채점에 응답한다. 문제 만들기 요청이 다시 오면 같은 퀴즈 글을 돌려준다
	private void enableGraders() {
		for (LlmProvider provider : LlmProvider.values()) {
			fakeLlm.get(provider).respond(grader(provider));
		}
	}

	private Function<LlmRequest, LlmResponse> grader(LlmProvider provider) {
		return request -> {
			if (!request.system().contains("채점 보조자")) {
				return new LlmResponse(QuizFixtures.threeTypes(false), 100, 50);
			}
			try {
				JsonNode items = objectMapper.readTree(request.user()).get("items");
				ObjectNode root = objectMapper.createObjectNode();
				ArrayNode out = root.putArray("items");
				for (JsonNode item : items) {
					ObjectNode node = out.addObject().put("id", item.get("id").asText());
					if ("short_answer".equals(item.get("type").asText())) {
						node.put("verdict", shortVerdicts.getOrDefault(provider, "correct")).put("reason", "뜻이 같다");
					} else {
						ArrayNode rubric = node.putArray("rubric");
						List<String> levels = essayLevels.getOrDefault(provider, List.of("met", "met"));
						for (int i = 0; i < item.get("rubric").size(); i++) {
							rubric.addObject().put("index", i).put("level", levels.get(i)).put("reason", "근거 " + i);
						}
						node.put("strengths", "잘한 점").put("missing", "빠진 점").put("improvements", "개선")
								.put("practical", "실무");
					}
				}
				return new LlmResponse(root.toString(), 100, 50);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
	}

	private List<Long> questionIds(long quizId) throws Exception {
		String response = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$.data.questions[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private ResultActions submit(long quizId, List<Map<String, Object>> answers) throws Exception {
		return mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("answers", answers))));
	}

	private ResultActions create(String token, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/quizzes")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private static Map<String, Object> body(long courseId, String mode, List<String> types, int count) {
		Map<String, Object> body = new HashMap<>();
		body.put("requestKey", UUID.randomUUID().toString());
		body.put("courseId", courseId);
		body.put("mode", mode);
		body.put("questionTypes", types);
		body.put("questionCount", count);
		return body;
	}

	private static long id(ResultActions actions) throws Exception {
		return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}

	private long createCourse(String token, String name, List<String> keywords) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", name, "semester", "2026-2", "keywords", keywords))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private String signupAndLogin(String email, boolean consent) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"email", email,
						"name", "테스터",
						"password", PASSWORD,
						"passwordConfirm", PASSWORD,
						"agreedTerms", true,
						"agreedPrivacy", true,
						"agreedAiAnalysis", consent,
						"agreedAiQuiz", consent))))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/auth/login")
				.header("X-Requested-With", "XMLHttpRequest")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
