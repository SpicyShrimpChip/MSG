package com.ailms.backend.domain.analysis.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.global.llm.LlmException;
import com.ailms.backend.global.llm.LlmProvider;
import com.ailms.backend.global.llm.LlmRequest;
import com.ailms.backend.support.FakeLlmClients;
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
 * 학습 대화 개념 분석과 학습 피드백. 실제 AI 제공사 대신 테스트 전용 가짜 모델(FakeLlmClients)을 쓴다.
 * 대화 내용은 모두 가상 값이다. 회원을 지우면 과목·대화·분석·피드백이 함께 지워지므로 매번 회원부터 새로 만든다.
 */
@IntegrationTest
class AnalysisApiTest {

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
	private FakeLlmClients models;

	private String token;
	private long courseId;
	// 가져온 대화의 메시지 id (순서대로)
	private List<Long> m;

	@BeforeEach
	void setUp() throws Exception {
		models.resetAll();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com", true);
		courseId = createCourse(token, "운영체제", List.of("교착 상태", "페이징", "스케줄링"));
		importConversation(token, courseId, "c-1", "운영체제 질문", List.of(
				"user:교착 상태 네 가지 조건은 상호 배제, 점유 대기, 비선점, 순환 대기 맞지?",
				"assistant:네, 맞아요. 네 조건이 모두 있어야 교착 상태가 생겨요.",
				"user:페이징이 뭐야?",
				"assistant:페이징은 메모리를 같은 크기의 페이지로 나누는 방식이에요.",
				"user:아 그렇구나. 주말에 영화 봤어",
				"assistant:재미있었겠네요.",
				"user:점심 메뉴 고민된다"));
		m = jdbcTemplate.queryForList("select id from conversation_message order by part_index, seq", Long.class);
	}

	// ---------- 모델 연결 상태 ----------

	@Test
	void 모델_연결_상태는_설정_여부만_보여_주고_키는_내보내지_않는다() throws Exception {
		models.get(LlmProvider.GPT).answer("{}");

		mockMvc.perform(get("/api/analysis/providers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(3)))
				.andExpect(jsonPath("$.data[0].provider").value("gpt"))
				.andExpect(jsonPath("$.data[0].configured").value(true))
				.andExpect(jsonPath("$.data[0].model").value("fake-gpt"))
				.andExpect(jsonPath("$.data[1].provider").value("claude"))
				.andExpect(jsonPath("$.data[1].configured").value(false))
				.andExpect(jsonPath("$.data[1].model").doesNotExist())
				.andExpect(jsonPath("$.data[2].provider").value("gemini"))
				.andExpect(jsonPath("$.data[0].apiKey").doesNotExist());
	}

	@Test
	void 설정된_모델이_없으면_503이고_분석_기록을_만들지_않는다() throws Exception {
		run(token, courseId)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.message").value("AI 모델이 아직 설정되지 않아 분석할 수 없어요."));
		assertThat(count("analysis_run")).isZero();
	}

	// ---------- 세 모델 비교 ----------

	@Test
	void 세_모델이_같은_입력을_따로_분석하고_개념별로_의견을_비교한다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(
				concept("교착 상태", "understood", evidence(m.get(0), "user_explanation")),
				concept("페이징", "not_understood", evidence(m.get(2), "user_question")),
				concept("스케줄링", "insufficient_evidence")));
		models.get(LlmProvider.CLAUDE).answer(answer(
				concept("교착 상태", "understood", evidence(m.get(0), "user_explanation")),
				concept("페이징", "partial", evidence(m.get(2), "user_question")),
				concept("스케줄링", "insufficient_evidence")));
		// AI 설명만 근거로 이해했다고 함(→ 근거 부족) + 보내지 않은 메시지 근거(→ 버림)
		String geminiAnswer = answer(
				concept("교착 상태", "partial", evidence(m.get(0), "user_explanation")),
				concept("페이징", "understood", evidence(m.get(3), "ai_explanation"), evidence(9_999_999L, "user_question")),
				concept("스케줄링", "insufficient_evidence"));
		models.get(LlmProvider.GEMINI).answer(geminiAnswer);

		String body = run(token, courseId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("completed"))
				.andExpect(jsonPath("$.data.promptVersion").value("concept-analysis-v1"))
				.andExpect(jsonPath("$.data.models[*].status").value(contains(
						"succeeded", "succeeded", "succeeded")))
				.andExpect(jsonPath("$.data.models[2].droppedEvidenceCount").value(1))
				// 추천 순서: 의견 불일치(페이징) → 근거 부족(스케줄링) → 이해(교착 상태)
				.andExpect(jsonPath("$.data.concepts[*].concept").value(contains(
						"페이징", "스케줄링", "교착 상태")))
				.andExpect(jsonPath("$.data.concepts[0].comparison.outcome").value("disagreement"))
				.andExpect(jsonPath("$.data.concepts[0].comparison.status").doesNotExist())
				.andExpect(jsonPath("$.data.concepts[0].comparison.judgedCount").value(3))
				.andExpect(jsonPath("$.data.concepts[0].comparison.agreeCount").value(1))
				.andExpect(jsonPath("$.data.concepts[1].comparison.outcome").value("unanimous"))
				.andExpect(jsonPath("$.data.concepts[1].comparison.status").value("insufficient_evidence"))
				.andExpect(jsonPath("$.data.concepts[2].comparison.outcome").value("majority"))
				.andExpect(jsonPath("$.data.concepts[2].comparison.status").value("understood"))
				.andExpect(jsonPath("$.data.concepts[2].comparison.agreeCount").value(2))
				.andExpect(jsonPath("$.data.concepts[2].comparison.calledCount").value(3))
				.andExpect(jsonPath("$.data.concepts[2].studyOrder").value(3))
				.andReturn().getResponse().getContentAsString();

		// Gemini의 페이징 판단: 모델이 보고한 상태는 남기고 비교에는 근거 부족으로 쓴다
		List<Map<String, Object>> geminiJudgments = JsonPath.read(body,
				"$.data.concepts[0].judgments[?(@.provider == 'gemini')]");
		Map<String, Object> geminiPaging = geminiJudgments.get(0);
		assertThat(geminiPaging.get("reportedStatus")).isEqualTo("understood");
		assertThat(geminiPaging.get("status")).isEqualTo("insufficient_evidence");
		assertThat(geminiPaging.get("adjustedReason")).isEqualTo("사용자가 쓴 메시지 근거가 없어 판단 근거 부족으로 봤어요.");
		// 근거 메시지는 대화 제목·말한 사람·앞부분과 함께 준다
		Map<String, Object> evidence = JsonPath.read(body,
				"$.data.concepts[2].judgments[0].evidence[0]");
		assertThat(((Number) evidence.get("messageId")).longValue()).isEqualTo(m.get(0));
		assertThat(evidence.get("role")).isEqualTo("user");
		assertThat(evidence.get("kind")).isEqualTo("user_explanation");
		assertThat(evidence.get("conversationTitle")).isEqualTo("운영체제 질문");
		assertThat((String) evidence.get("excerpt")).startsWith("교착 상태 네 가지 조건은");

		// 세 모델이 똑같은 입력을 받았고, 다른 모델의 판단은 들어 있지 않다
		LlmRequest gpt = models.get(LlmProvider.GPT).requests().get(0);
		assertThat(models.get(LlmProvider.CLAUDE).requests()).containsExactly(gpt);
		assertThat(models.get(LlmProvider.GEMINI).requests()).containsExactly(gpt);
		assertThat(gpt.user()).contains("[m" + m.get(0) + "] (사용자)", "[m" + m.get(3) + "] (AI)",
				"\"교착 상태\", \"페이징\", \"스케줄링\"");
		assertThat(gpt.user()).doesNotContain("understood", "fake-");
		// 키워드와 먼 메시지는 보내지 않는다 (페이징 메시지 앞뒤 하나까지만)
		assertThat(gpt.user()).contains("주말에 영화 봤어").doesNotContain("점심 메뉴");

		// 모델 답변 원본을 그대로 보관한다
		assertThat(jdbcTemplate.queryForObject(
				"select raw_output from analysis_model_result where provider = 'GEMINI'", String.class))
				.isEqualTo(geminiAnswer);
		// 보낸 메시지: 키워드가 든 0~3번과 그 앞뒤(4번)까지 5개. 5·6번은 보내지 않는다
		assertThat(jdbcTemplate.queryForObject("select message_count from analysis_run", Integer.class)).isEqualTo(5);
	}

	@Test
	void 일부_모델이_실패하면_성공한_모델만으로_비교하고_실패를_따로_남긴다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(
				concept("교착 상태", "partial", evidence(m.get(0), "user_explanation"))));
		models.get(LlmProvider.CLAUDE).respond(request -> {
			throw new LlmException(LlmException.Code.RATE_LIMITED, "429");
		});
		models.get(LlmProvider.GEMINI).answer("분석할 수 없어요");

		run(token, courseId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("partial"))
				.andExpect(jsonPath("$.data.models[1].status").value("failed"))
				.andExpect(jsonPath("$.data.models[1].message").value("호출 한도를 넘어 결과를 받지 못했어요."))
				.andExpect(jsonPath("$.data.models[2].status").value("invalid_output"))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].comparison.outcome")
						.value("single_model"))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].comparison.judgedCount").value(1))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].comparison.calledCount").value(3))
				// GPT가 빠뜨린 개념은 판단한 모델이 없다 (근거 부족으로 채우지 않는다)
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '페이징')].comparison.outcome").value("no_result"));

		// 한도 초과는 한 번 더 불러 본다
		assertThat(models.get(LlmProvider.CLAUDE).requests()).hasSize(2);
		assertThat(models.get(LlmProvider.GEMINI).requests()).hasSize(1);
	}

	@Test
	void 키가_없는_모델은_부르지_않고_미설정으로_남긴다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(concept("교착 상태", "insufficient_evidence")));

		run(token, courseId)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.status").value("completed"))
				.andExpect(jsonPath("$.data.models[1].status").value("not_configured"))
				.andExpect(jsonPath("$.data.models[1].model").doesNotExist())
				.andExpect(jsonPath("$.data.models[2].status").value("not_configured"))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].comparison.calledCount").value(1));
	}

	// ---------- 동의·소유·입력 확인 ----------

	@Test
	void 분석할_수_없는_경우를_알려_준다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(concept("교착 상태", "insufficient_evidence")));
		String other = signupAndLogin("other@example.com", true);
		long otherCourse = createCourse(other, "자료구조", List.of("트리"));
		long noKeywords = createCourse(token, "네트워크", List.of());
		long noConversations = createCourse(token, "데이터베이스", List.of("정규화"));
		long noMatch = createCourse(token, "알고리즘", List.of("다익스트라"));
		importConversation(token, noMatch, "c-2", "잡담", List.of("user:오늘 날씨 좋다", "assistant:그러네요"));

		run(token, otherCourse).andExpect(status().isNotFound());
		run(token, noKeywords).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("과목에 키워드를 먼저 넣어 주세요. 키워드가 분석할 개념이 돼요."));
		run(token, noConversations).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이 과목에 분석할 AI 대화가 없어요. 대화를 먼저 가져와 주세요."));
		run(token, noMatch).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("과목 키워드가 들어간 대화가 없어 분석할 수 없어요."));
		assertThat(count("analysis_run")).isZero();
		assertThat(models.get(LlmProvider.GPT).requests()).isEmpty();
	}

	@Test
	void AI_대화_분석에_동의하지_않으면_403() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(concept("교착 상태", "insufficient_evidence")));
		consent(token, false);
		// 철회로 대화가 지워졌으므로 다시 동의하기 전에는 분석할 수 없다
		run(token, courseId).andExpect(status().isForbidden())
				.andExpect(jsonPath("$.message").value("AI 대화 분석에 동의해야 학습 분석을 할 수 있어요."));
		assertThat(models.get(LlmProvider.GPT).requests()).isEmpty();
	}

	@Test
	void 이미_분석_중이거나_24시간_횟수를_넘으면_막는다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(concept("교착 상태", "insufficient_evidence")));
		Long userId = userId("me@example.com");
		jdbcTemplate.update("insert into analysis_run (user_id, course_id, status, prompt_version, conversation_count,"
				+ " message_count, truncated_message_count, input_chars, created_at) values (?, ?, 'RUNNING', 'v', 1, 1, 0,"
				+ " 1, current_timestamp)", userId, courseId);
		run(token, courseId).andExpect(status().isConflict());

		jdbcTemplate.update("update analysis_run set status = 'COMPLETED'");
		for (int i = 0; i < 9; i++) {
			jdbcTemplate.update("insert into analysis_run (user_id, course_id, status, prompt_version,"
					+ " conversation_count, message_count, truncated_message_count, input_chars, created_at)"
					+ " values (?, ?, 'COMPLETED', 'v', 1, 1, 0, 1, current_timestamp)", userId, courseId);
		}
		run(token, courseId).andExpect(status().isTooManyRequests());
	}

	@Test
	void 남의_분석_결과는_볼_수_없다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(concept("교착 상태", "insufficient_evidence")));
		String other = signupAndLogin("other@example.com", true);
		mockMvc.perform(get("/api/analysis/courses/" + courseId + "/runs/latest")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("아직 이 과목을 분석한 결과가 없어요."));

		long runId = runId(run(token, courseId).andExpect(status().isCreated()));

		mockMvc.perform(get("/api/analysis/runs/" + runId).header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/analysis/courses/" + courseId + "/runs/latest")
						.header(HttpHeaders.AUTHORIZATION, bearer(other)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/analysis/courses/" + courseId + "/runs/latest")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(runId));
	}

	// ---------- 삭제 정책 ----------

	@Test
	void 동의를_철회하면_분석_결과를_지우고_사용자_피드백은_남긴다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(concept("교착 상태", "insufficient_evidence")));
		long runId = runId(run(token, courseId));
		saveFeedback(token, courseId, "교착 상태", "already_know", null, runId).andExpect(status().isOk());

		consent(token, false);

		assertThat(count("analysis_run")).isZero();
		assertThat(count("analysis_model_result")).isZero();
		assertThat(count("analysis_concept_judgment")).isZero();
		assertThat(count("analysis_concept_consensus")).isZero();
		assertThat(count("concept_feedback")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select analysis_run_id from concept_feedback", Long.class)).isNull();
	}

	@Test
	void 대화를_지우면_그_대화를_쓴_분석도_지운다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(
				concept("교착 상태", "understood", evidence(m.get(0), "user_explanation"))));
		run(token, courseId).andExpect(status().isCreated());
		long conversationId = jdbcTemplate.queryForObject("select id from conversation", Long.class);

		mockMvc.perform(delete("/api/conversations/" + conversationId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());

		assertThat(count("analysis_run")).isZero();
		assertThat(count("analysis_judgment_evidence")).isZero();
	}

	// ---------- 학습 피드백 ----------

	@Test
	void 피드백은_남기고_바꾸고_취소할_수_있고_AI_판단은_그대로다() throws Exception {
		models.get(LlmProvider.GPT).answer(answer(
				concept("교착 상태", "understood", evidence(m.get(0), "user_explanation")),
				concept("페이징", "not_understood", evidence(m.get(2), "user_question"))));
		long runId = runId(run(token, courseId));

		String created = saveFeedback(token, courseId, " 교착  상태 ", "already_know", "시험 범위라 이미 봤어요", runId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.concept").value("교착 상태"))
				.andExpect(jsonPath("$.data.feedback").value("already_know"))
				.andExpect(jsonPath("$.data.nextAction").value("confirm_quiz"))
				.andExpect(jsonPath("$.data.analysisRunId").value(runId))
				.andReturn().getResponse().getContentAsString();
		long feedbackId = ((Number) JsonPath.read(created, "$.data.id")).longValue();

		mockMvc.perform(get("/api/analysis/runs/" + runId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].recommendConfirmQuiz").value(true))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].feedback.comment")
						.value("시험 범위라 이미 봤어요"))
				.andExpect(jsonPath("$.data.concepts[?(@.concept == '교착 상태')].comparison.status").value("understood"));

		// 더 공부할래요로 바꾸면 같은 피드백이 바뀌고 추천 순서가 맨 앞으로 온다. AI 판단은 그대로다
		saveFeedback(token, courseId, "교착 상태", "want_to_study", null, null)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(feedbackId))
				.andExpect(jsonPath("$.data.nextAction").value("prioritize_study"))
				.andExpect(jsonPath("$.data.comment").doesNotExist());
		mockMvc.perform(get("/api/analysis/runs/" + runId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.concepts[0].concept").value("교착 상태"))
				.andExpect(jsonPath("$.data.concepts[0].studyOrder").value(1))
				.andExpect(jsonPath("$.data.concepts[0].comparison.status").value("understood"))
				.andExpect(jsonPath("$.data.concepts[0].judgments[0].status").value("understood"))
				.andExpect(jsonPath("$.data.concepts[0].recommendConfirmQuiz").value(false));
		mockMvc.perform(get("/api/analysis/courses/" + courseId + "/feedback")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data", hasSize(1)));

		mockMvc.perform(delete("/api/analysis/feedback/" + feedbackId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
		assertThat(count("concept_feedback")).isZero();
		mockMvc.perform(get("/api/analysis/runs/" + runId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.concepts[0].concept").value("페이징"));
	}

	@Test
	void 피드백_입력과_소유를_확인한다() throws Exception {
		String other = signupAndLogin("other@example.com", true);
		long otherCourse = createCourse(other, "자료구조", List.of("트리"));
		long feedbackId = ((Number) JsonPath.read(saveFeedback(other, otherCourse, "트리", "want_to_study", null, null)
				.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();

		saveFeedback(token, courseId, "세마포어", "already_know", null, null)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("과목 키워드에 있는 개념만 고를 수 있어요."));
		saveFeedback(token, courseId, "교착 상태", "maybe", null, null).andExpect(status().isBadRequest());
		saveFeedback(token, courseId, "교착 상태", "already_know", "가".repeat(201), null)
				.andExpect(status().isBadRequest());
		saveFeedback(token, otherCourse, "트리", "already_know", null, null).andExpect(status().isNotFound());
		saveFeedback(token, courseId, "교착 상태", "already_know", null, 9_999_999L).andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/analysis/feedback/" + feedbackId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());
		assertThat(count("concept_feedback")).isEqualTo(1);
	}

	@Test
	void 토큰이_없으면_401() throws Exception {
		mockMvc.perform(get("/api/analysis/providers")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/analysis/courses/1/runs")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/analysis/feedback/1")).andExpect(status().isUnauthorized());
	}

	// ---------- 도우미 ----------

	private static String answer(Map<?, ?>... concepts) {
		try {
			return new ObjectMapper().writeValueAsString(Map.of("concepts", List.of(concepts)));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@SafeVarargs
	private static Map<String, Object> concept(String name, String status, Map<String, Object>... evidence) {
		Map<String, Object> concept = new HashMap<>();
		concept.put("concept", name);
		concept.put("status", status);
		concept.put("evidence", List.of(evidence));
		concept.put("reason", name + " 판단 이유");
		concept.put("needsCheck", "");
		concept.put("recommendedActivity", name + " 다시 정리하기");
		return concept;
	}

	private static Map<String, Object> evidence(long messageId, String kind) {
		return Map.of("messageId", messageId, "kind", kind, "note", "근거 설명");
	}

	private ResultActions run(String accessToken, long course) throws Exception {
		return mockMvc.perform(post("/api/analysis/courses/" + course + "/runs")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)));
	}

	private long runId(ResultActions result) throws Exception {
		return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.id")).longValue();
	}

	private ResultActions saveFeedback(String accessToken, long course, String concept, String feedback,
			String comment, Long runId) throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("concept", concept);
		body.put("feedback", feedback);
		body.put("comment", comment);
		body.put("analysisRunId", runId);
		return mockMvc.perform(put("/api/analysis/courses/" + course + "/feedback")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(body)));
	}

	private void consent(String accessToken, boolean value) throws Exception {
		mockMvc.perform(patch("/api/users/me/consents").header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("aiAnalysis", value))))
				.andExpect(status().isOk());
	}

	private void importConversation(String accessToken, long course, String clientId, String title,
			List<String> messages) throws Exception {
		Map<String, Object> conversation = new HashMap<>();
		conversation.put("clientId", clientId);
		conversation.put("source", "chatgpt");
		conversation.put("title", title);
		conversation.put("messages", messages.stream().map(line -> {
			int split = line.indexOf(':');
			return Map.of("role", line.substring(0, split), "text", line.substring(split + 1));
		}).toList());
		mockMvc.perform(post("/api/conversations")
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
								"importId", UUID.randomUUID().toString(), "requestIndex", 1, "requestCount", 1,
								"courseId", course, "conversations", List.of(conversation)))))
				.andExpect(status().isCreated());
	}

	private long createCourse(String accessToken, String name, List<String> keywords) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", name, "semester", "2026-2", "keywords", keywords))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private Long userId(String email) {
		return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
	}

	private static String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private String signupAndLogin(String email, boolean agreedAiAnalysis) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of(
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
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
