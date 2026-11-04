package com.ailms.backend.domain.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import com.ailms.backend.support.MutableClock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 퀴즈 AI 처리의 안전장치: 호출 한도(처음 채점·다시 채점·모델 요청 수), 진행 중 중복 차단, 시간 예산과 늦은 응답,
 * 남은 진행 중 기록 정리, AI 퀴즈 처리 동의(대화 분석 동의와 분리), 학습용 IP 예외, 모의시험, 맞춤 학습 순서.
 * 모델은 가짜(FakeLlmClients)라 실제 제공사를 부르지 않는다.
 */
@IntegrationTest
class QuizSafetyApiTest {

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
	@Autowired
	private MutableClock clock;

	private String token;
	private long userId;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		fakeLlm.resetAll();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com", true, true);
		userId = jdbcTemplate.queryForObject("select id from users where email = 'me@example.com'", Long.class);
		courseId = createCourse(token, List.of("NAT", "VLAN", "OSPF"));
	}

	@AfterEach
	void tearDown() {
		clock.reset();
	}

	// ---------- AI 퀴즈 처리 동의 ----------

	@Test
	void 대화_분석에만_동의하면_퀴즈_만들기는_403_AI_채점은_대기_객관식은_채점된다() throws Exception {
		String analysisOnly = signupAndLogin("analysis@example.com", true, false);
		long course = createCourse(analysisOnly, List.of("NAT", "VLAN"));
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create(analysisOnly, body(course, "exam_prep", List.of("multiple_choice"), 1))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(containsString("AI 퀴즈 처리 동의")));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(analysisOnly)))
				.andExpect(jsonPath("$.data.aiAnalysisConsent").value(true))
				.andExpect(jsonPath("$.data.aiQuizConsent").value(false));
	}

	@Test
	void 동의_없이_AI_채점이_필요한_답은_보내지_않으므로_개인정보가_있어도_저장하고_대기로_둔다() throws Exception {
		long quizId = generate();
		consent(token, "{\"aiQuiz\":false}");
		enableGraders(List.of("met", "met"));
		List<Long> ids = questionIds(quizId);
		int before = fakeLlm.get(LlmProvider.GPT).requests().size();

		submit(quizId, List.of(Map.of("questionId", ids.get(0), "choiceIndex", 1),
				Map.of("questionId", ids.get(2), "text", "제 메일 me@example.com")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[0].correct").value(true))
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("pending"))
				.andExpect(jsonPath("$.data.results[2].gradingMessage").value(containsString("AI 퀴즈 처리에 동의하지 않아")));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).hasSize(before);
	}

	@Test
	void 동의는_항목별로_바꾸고_퀴즈_동의를_철회해도_만든_퀴즈는_남는다() throws Exception {
		long quizId = generate();

		consent(token, "{\"aiQuiz\":false}")
				.andExpect(jsonPath("$.data.aiQuizConsent").value(false))
				.andExpect(jsonPath("$.data.aiAnalysisConsent").value(true));
		mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
		consent(token, "{}").andExpect(status().isBadRequest());
		consent(token, "{\"aiQuiz\":true}").andExpect(jsonPath("$.data.aiQuizConsent").value(true));
	}

	// ---------- 채점 한도·중복 ----------

	@Test
	void 다시_채점도_24시간_실행_한도에_들어가고_넘으면_429이며_답은_그대로다() throws Exception {
		long quizId = generate();
		fakeLlm.resetAll();
		List<Long> ids = questionIds(quizId);
		long submissionId = submissionId(submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "PAT 설명")))
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("pending")));
		insertRuns(submissionId, 30, "SUCCEEDED", clock.instant().plusSeconds(60));
		enableGraders(List.of("met", "met"));

		regrade(quizId, submissionId)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value(containsString("AI 채점 횟수")));

		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
		assertThat(jdbcTemplate.queryForObject("select grading_status from quiz_answer where question_id = ?",
				String.class, ids.get(2))).isEqualTo("PENDING");
	}

	@Test
	void 같은_제출을_채점하는_중이면_409이고_마감이_지난_실행은_정리하고_다시_채점한다() throws Exception {
		long quizId = generate();
		fakeLlm.resetAll();
		List<Long> ids = questionIds(quizId);
		long submissionId = submissionId(submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "PAT 설명"))));
		enableGraders(List.of("met", "met"));

		insertRuns(submissionId, 1, "RUNNING", clock.instant().plusSeconds(60));
		regrade(quizId, submissionId).andExpect(status().isConflict());
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();

		// 서버가 재시작돼 남은 실행처럼 마감이 지나면 끝난 것으로 보고 다시 채점할 수 있다
		clock.advance(Duration.ofMinutes(5));
		regrade(quizId, submissionId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("graded"));
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_grading_run where status = 'TIMED_OUT'",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void 마감이_지난_뒤_도착한_채점은_반영하지_않고_다시_채점할_수_있게_둔다() throws Exception {
		long quizId = generate();
		for (LlmProvider provider : LlmProvider.values()) {
			fakeLlm.get(provider).respond(lateGrader());
		}
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "PAT 설명")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("pending"))
				.andExpect(jsonPath("$.data.results[2].gradingMessage").value(containsString("다시 채점")));
		assertThat(jdbcTemplate.queryForObject("select status from quiz_grading_run", String.class))
				.isEqualTo("TIMED_OUT");
	}

	@Test
	void 모델_호출은_모델마다_기록하고_24시간_모델_요청_수_상한에_닿으면_만들기도_429() throws Exception {
		generate();
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from quiz_ai_call where purpose = 'GENERATION' and outcome = 'SUCCEEDED'",
				Integer.class)).isEqualTo(1);

		jdbcTemplate.update("insert into quiz_ai_call (user_id, purpose, provider, attempts, outcome, latency_ms,"
				+ " created_at) values (?, 'GRADING', 'gpt', 200, 'FAILED', 0, ?)", userId,
				Timestamp.valueOf(LocalDateTime.now(clock)));
		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value(containsString("AI 호출 한도")));
	}

	// ---------- 만들기 시간 예산·상태 ----------

	@Test
	void 마감이_지나_도착한_문제는_저장하지_않고_504_상태는_timed_out_그_뒤_새_요청은_막히지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> {
			clock.advance(Duration.ofMinutes(4));
			return new LlmResponse(QuizFixtures.threeTypes(false), 10, 10);
		});
		Map<String, Object> late = body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3);

		create(token, late).andExpect(status().isGatewayTimeout());

		assertThat(count("quiz")).isZero();
		mockMvc.perform(get("/api/quizzes/generations/" + late.get("requestKey"))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.status").value("timed_out"))
				.andExpect(jsonPath("$.data.quizId").doesNotExist())
				.andExpect(jsonPath("$.data.message").value(containsString("정해진 시간")));
		create(token, late).andExpect(status().isConflict());

		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isCreated());
	}

	@Test
	void 남은_진행_중_기록은_마감을_보고_정리하고_마감_전이면_409() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		insertGeneration("old-key", clock.instant().plusSeconds(60));
		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isConflict());

		clock.advance(Duration.ofMinutes(2));
		create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isCreated());
		assertThat(jdbcTemplate.queryForObject("select status from quiz_generation where request_key = 'old-key'",
				String.class)).isEqualTo("TIMED_OUT");
	}

	@Test
	void 형식이_틀려_실패하면_진행_중으로_남지_않고_상태에_이유가_나온다_남의_키는_404() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer("만들 수 없어요");
		Map<String, Object> request = body(courseId, "exam_prep", List.of("multiple_choice"), 1);

		create(token, request).andExpect(status().isBadGateway());

		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_generation where status = 'RUNNING'",
				Integer.class)).isZero();
		mockMvc.perform(get("/api/quizzes/generations/" + request.get("requestKey"))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.status").value("failed"))
				.andExpect(jsonPath("$.data.message").value(containsString("형식")));
		String other = signupAndLogin("other@example.com", true, true);
		mockMvc.perform(get("/api/quizzes/generations/" + request.get("requestKey"))
						.header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound());
	}

	// ---------- 학습용 IP ----------

	@Test
	void 사설_예시_IP는_보내고_공인_IP는_422() throws Exception {
		long quizId = generate();
		enableGraders(List.of("met", "met"));
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "203.0.113.5와 10.0.0.0/8을 PAT로 나눈다")))
				.andExpect(status().isCreated());
		submit(quizId, List.of(Map.of("questionId", ids.get(2), "text", "우리 집 공인 IP 8.8.4.4로 접속한다")))
				.andExpect(status().isUnprocessableEntity());
	}

	// ---------- 모의시험 ----------

	@Test
	void 모의시험은_시험_대비에서만_만들고_제목과_표시가_다르다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		Map<String, Object> exam = body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3);
		exam.put("mockExam", true);

		create(token, exam)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.title").value("네트워크 보안 · 모의시험"))
				.andExpect(jsonPath("$.data.mockExam").value(true));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests().get(0).user()).contains("\"mockExam\":true");

		Map<String, Object> work = body(courseId, "work", List.of("essay"), 1);
		work.put("mockExam", true);
		create(token, work).andExpect(status().isBadRequest());
		Map<String, Object> tooMany = body(courseId, "exam_prep", List.of("multiple_choice"), 21);
		tooMany.put("mockExam", true);
		create(token, tooMany).andExpect(status().isBadRequest());
	}

	// ---------- 맞춤 학습 순서 ----------

	@Test
	void 학습_순서는_더_공부할래요_퀴즈_보완_근거_없음_순이고_이유와_행동을_준다() throws Exception {
		long quizId = generate();
		fakeLlm.resetAll();
		List<Long> ids = questionIds(quizId);
		// NAT: 객관식 오답·주관식 정답·서술형 빈 답 → 1/8점 (보완), VLAN: 서술형 0/6
		submit(quizId, List.of(Map.of("questionId", ids.get(0), "choiceIndex", 0),
				Map.of("questionId", ids.get(1), "text", "PAT"))).andExpect(status().isCreated());
		mockMvc.perform(put("/api/analysis/courses/" + courseId + "/feedback")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
						.content("{\"concept\":\"OSPF\",\"feedback\":\"want_to_study\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/curriculum/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.recommendedMode").value("self_development"))
				.andExpect(jsonPath("$.data.steps[0].concept").value("OSPF"))
				.andExpect(jsonPath("$.data.steps[0].stage").value("priority_study"))
				.andExpect(jsonPath("$.data.steps[0].reasons[0]").value(containsString("더 공부할래요")))
				.andExpect(jsonPath("$.data.steps[1].concept").value("NAT"))
				.andExpect(jsonPath("$.data.steps[1].stage").value("review"))
				.andExpect(jsonPath("$.data.steps[1].evidence.score").value(1))
				.andExpect(jsonPath("$.data.steps[1].evidence.maxScore").value(8))
				.andExpect(jsonPath("$.data.steps[1].evidence.correctCount").value(1))
				.andExpect(jsonPath("$.data.steps[1].actions[0].type").value("review_quiz"))
				.andExpect(jsonPath("$.data.steps[2].concept").value("VLAN"));

		String other = signupAndLogin("other@example.com", true, true);
		mockMvc.perform(get("/api/curriculum/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound());
	}

	// ---------- 자료 본문 전송 (기본 꺼짐) ----------

	@Test
	void 자료_본문_전송은_기본으로_꺼져_있어_본문_사용과_개념_뽑기를_거절하고_과목_개념으로는_만든다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 1, 1));
		String uploaded = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
						.multipart("/api/materials")
						.file(new org.springframework.mock.web.MockMultipartFile("file", "노트.txt", "text/plain",
								"NAT 정리".getBytes()))
						.param("title", "노트").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		long material = ((Number) JsonPath.read(uploaded, "$.data.id")).longValue();

		mockMvc.perform(get("/api/assessments/ai-status").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.materialContentEnabled").value(false));
		Map<String, Object> withContent = new HashMap<>(Map.of("requestKey", UUID.randomUUID().toString(),
				"materialId", material, "kind", "pre", "useMaterialContent", true, "materialContentConsent", true));
		mockMvc.perform(post("/api/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(withContent)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value(containsString("꺼져 있어요")));
		mockMvc.perform(post("/api/materials/" + material + "/concepts").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"materialContentConsent\":true}"))
				.andExpect(status().isForbidden());
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();

		withContent.remove("useMaterialContent");
		withContent.put("requestKey", UUID.randomUUID().toString());
		mockMvc.perform(post("/api/assessments").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(withContent)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.materialContentUsed").value(false))
				.andExpect(jsonPath("$.data.topicSource").value("course"));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests().get(0).user()).doesNotContain("materialExcerpt", "NAT 정리");
	}

	// ---------- AI 하나만 ----------

	@Test
	void AI가_하나뿐이면_그_모델로_채점하고_다수결이_아니라_한_모델_판단으로_표시한다() throws Exception {
		long quizId = generate();
		fakeLlm.resetAll();
		fakeLlm.get(LlmProvider.GEMINI).respond(grader(List.of("met", "met")));
		List<Long> ids = questionIds(quizId);

		submit(quizId, List.of(Map.of("questionId", ids.get(0), "choiceIndex", 1),
				Map.of("questionId", ids.get(1), "text", "PAT"),
				Map.of("questionId", ids.get(2), "text", "PAT는 포트로 나눈다")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.results[2].gradingStatus").value("graded"))
				.andExpect(jsonPath("$.data.results[2].aiCalledCount").value(1))
				.andExpect(jsonPath("$.data.results[2].aiJudgedCount").value(1))
				.andExpect(jsonPath("$.data.results[2].gradingMessage").value(containsString("하나만")))
				.andExpect(jsonPath("$.data.assessment.status").value("needs_check"));
	}

	// ---------- 도우미 ----------

	private long generate() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		String response = create(token, body(courseId, "exam_prep", List.of("multiple_choice", "short_answer", "essay"), 3))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private void insertRuns(long submissionId, int count, String status, java.time.Instant deadline) {
		for (int i = 0; i < count; i++) {
			jdbcTemplate.update("insert into quiz_grading_run (user_id, submission_id, kind, status, answer_count,"
					+ " created_at, deadline_at) values (?, ?, 'REGRADE', ?, 1, ?, ?)", userId, submissionId, status,
					Timestamp.valueOf(LocalDateTime.now(clock)),
					Timestamp.valueOf(LocalDateTime.ofInstant(deadline, clock.getZone())));
		}
	}

	private void insertGeneration(String key, java.time.Instant deadline) {
		jdbcTemplate.update("insert into quiz_generation (user_id, course_id, request_key, mode, question_types,"
				+ " question_count, difficulty, status, attempts, created_at, deadline_at)"
				+ " values (?, ?, ?, 'EXAM_PREP', 'MULTIPLE_CHOICE', 1, 'MEDIUM', 'RUNNING', 0, ?, ?)", userId,
				courseId, key, Timestamp.valueOf(LocalDateTime.now(clock)),
				Timestamp.valueOf(LocalDateTime.ofInstant(deadline, clock.getZone())));
	}

	private void enableGraders(List<String> levels) {
		for (LlmProvider provider : LlmProvider.values()) {
			fakeLlm.get(provider).respond(grader(levels));
		}
	}

	// 채점 지시문이면 기준마다 levels, 아니면 퀴즈 글
	private Function<LlmRequest, LlmResponse> grader(List<String> levels) {
		return request -> {
			if (!request.system().contains("채점 보조자")) {
				return new LlmResponse(QuizFixtures.threeTypes(false), 10, 10);
			}
			try {
				JsonNode items = objectMapper.readTree(request.user()).get("items");
				ObjectNode root = objectMapper.createObjectNode();
				ArrayNode out = root.putArray("items");
				for (JsonNode item : items) {
					ObjectNode node = out.addObject().put("id", item.get("id").asText());
					if ("short_answer".equals(item.get("type").asText())) {
						node.put("verdict", "correct");
					} else {
						ArrayNode rubric = node.putArray("rubric");
						for (int i = 0; i < item.get("rubric").size(); i++) {
							rubric.addObject().put("index", i).put("level", levels.get(i));
						}
					}
				}
				return new LlmResponse(root.toString(), 10, 10);
			} catch (Exception e) {
				throw new IllegalStateException(e);
			}
		};
	}

	// 채점 마감(2분)이 지난 뒤 답하는 모델
	private Function<LlmRequest, LlmResponse> lateGrader() {
		Function<LlmRequest, LlmResponse> normal = grader(List.of("met", "met"));
		return request -> {
			if (request.system().contains("채점 보조자")) {
				clock.advance(Duration.ofMinutes(3));
			}
			return normal.apply(request);
		};
	}

	private List<Long> questionIds(long quizId) throws Exception {
		String response = mockMvc.perform(get("/api/quizzes/" + quizId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andReturn().getResponse().getContentAsString();
		List<Number> ids = JsonPath.read(response, "$.data.questions[*].id");
		return ids.stream().map(Number::longValue).toList();
	}

	private static long submissionId(ResultActions actions) throws Exception {
		return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.data.submissionId"))
				.longValue();
	}

	private ResultActions submit(long quizId, List<Map<String, Object>> answers) throws Exception {
		return mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("answers", answers))));
	}

	private ResultActions regrade(long quizId, long submissionId) throws Exception {
		return mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions/" + submissionId + "/grading")
				.header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions consent(String token, String body) throws Exception {
		return mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions create(String token, Map<String, Object> body) throws Exception {
		return mockMvc.perform(post("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
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

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
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

	private String signupAndLogin(String email, boolean analysis, boolean quiz) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "name", "테스터", "password",
								PASSWORD, "passwordConfirm", PASSWORD, "agreedTerms", true, "agreedPrivacy", true,
								"agreedAiAnalysis", analysis, "agreedAiQuiz", quiz))))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
