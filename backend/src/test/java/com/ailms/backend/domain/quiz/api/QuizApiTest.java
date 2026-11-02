package com.ailms.backend.domain.quiz.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.quiz.dao.QuizQuestionRepository;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.quiz.entity.QuizConcept;
import com.ailms.backend.domain.quiz.entity.QuizQuestion;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.support.IntegrationTest;
import com.ailms.backend.support.TestFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 퀴즈 풀기·채점 API. 퀴즈를 만드는 API는 아직 없어서 저장소로 직접 넣는다.
 * 회원을 지우면 과목·퀴즈·제출 기록도 함께 지워지므로(ON DELETE CASCADE) 매번 회원부터 새로 만든다.
 */
@IntegrationTest
class QuizApiTest {

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
	private QuizRepository quizRepository;
	@Autowired
	private QuizQuestionRepository questionRepository;
	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String token;
	private String otherToken;
	private long userId;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		otherToken = signupAndLogin("other@example.com");
		userId = userId("me@example.com");
		courseId = createCourse(token, "운영체제");
	}

	// ---------- 인증·소유권 ----------

	@Test
	void 토큰이_없으면_모두_401() throws Exception {
		mockMvc.perform(get("/api/quizzes")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/quizzes/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/quizzes/1/submissions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"answers\":[]}")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/quizzes/1/submissions/latest")).andExpect(status().isUnauthorized());
	}

	@Test
	void 남의_퀴즈는_보기도_제출도_결과도_404이고_기록이_남지_않는다() throws Exception {
		QuizFixture quiz = createQuiz(userId, courseId, null, "페이징");

		mockMvc.perform(get("/api/quizzes/" + quiz.id()).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("퀴즈를 찾을 수 없어요."));
		submit(otherToken, quiz.id(), List.of(answer(quiz.first(), 1))).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/quizzes/" + quiz.id() + "/submissions/latest")
						.header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(jsonPath("$.data", hasSize(0)));

		assertThat(submissionCount()).isZero();
	}

	@Test
	void 없는_퀴즈는_404() throws Exception {
		mockMvc.perform(get("/api/quizzes/99999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());
	}

	// ---------- 목록·풀기 ----------

	@Test
	void 목록은_최근에_만든_퀴즈부터_문제_수와_최근_맞힌_수를_준다() throws Exception {
		QuizFixture first = createQuiz(userId, courseId, null, "페이징");
		QuizFixture second = createQuiz(userId, courseId, null, "스케줄링");
		submit(token, first.id(), List.of(answer(first.first(), 1), answer(first.second(), 0)))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[0].id").value(second.id()))
				.andExpect(jsonPath("$.data[0].title").value("스케줄링 퀴즈"))
				.andExpect(jsonPath("$.data[0].courseId").value(courseId))
				.andExpect(jsonPath("$.data[0].materialId").value(nullValue()))
				.andExpect(jsonPath("$.data[0].questionCount").value(2))
				.andExpect(jsonPath("$.data[0].lastCorrectCount").value(nullValue()))
				.andExpect(jsonPath("$.data[1].id").value(first.id()))
				.andExpect(jsonPath("$.data[1].lastCorrectCount").value(2));
	}

	@Test
	void 풀기_화면에는_정답과_해설이_없다() throws Exception {
		QuizFixture quiz = createQuiz(userId, courseId, null, "페이징");

		String response = mockMvc.perform(get("/api/quizzes/" + quiz.id())
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("페이징 퀴즈"))
				.andExpect(jsonPath("$.data.questions", hasSize(2)))
				.andExpect(jsonPath("$.data.questions[0].id").value(quiz.first()))
				.andExpect(jsonPath("$.data.questions[0].text").value("페이징 문제 1"))
				.andExpect(jsonPath("$.data.questions[0].choices", contains("보기 A", "보기 B", "보기 C")))
				.andReturn().getResponse().getContentAsString();

		assertThat(response).doesNotContain("correct", "aiExplanation", "해설", "concept");
	}

	// ---------- 제출·채점 ----------

	@Test
	void 제출하면_서버가_채점하고_빠진_문제는_틀림으로_친다() throws Exception {
		QuizFixture quiz = createQuiz(userId, courseId, null, "페이징");

		submit(token, quiz.id(), List.of(answer(quiz.first(), 1)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.submissionId").isNumber())
				.andExpect(jsonPath("$.data.submittedAt").isString())
				.andExpect(jsonPath("$.data.correctCount").value(1))
				.andExpect(jsonPath("$.data.totalCount").value(2))
				.andExpect(jsonPath("$.data.results", hasSize(2)))
				.andExpect(jsonPath("$.data.results[0].questionId").value(quiz.first()))
				.andExpect(jsonPath("$.data.results[0].choiceIndex").value(1))
				.andExpect(jsonPath("$.data.results[0].correct").value(true))
				.andExpect(jsonPath("$.data.results[0].correctChoiceIndex").value(1))
				.andExpect(jsonPath("$.data.results[0].concepts[0].conceptId").value("페이징"))
				.andExpect(jsonPath("$.data.results[0].concepts[0].name").value("페이징"))
				.andExpect(jsonPath("$.data.results[0].aiExplanation").value("페이징 해설"))
				.andExpect(jsonPath("$.data.results[1].questionId").value(quiz.second()))
				.andExpect(jsonPath("$.data.results[1].choiceIndex").value(-1))
				.andExpect(jsonPath("$.data.results[1].correct").value(false))
				.andExpect(jsonPath("$.data.results[1].correctChoiceIndex").value(0))
				.andExpect(jsonPath("$.data.results[1].concepts", hasSize(0)))
				.andExpect(jsonPath("$.data.results[1].aiExplanation").value(nullValue()));

		assertThat(submissionCount()).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_answer", Integer.class))
				.isEqualTo(2);
	}

	@Test
	void 안_풀었으면_최근_결과는_null이고_다시_풀면_가장_최근_것을_준다() throws Exception {
		QuizFixture quiz = createQuiz(userId, courseId, null, "페이징");
		String latestUrl = "/api/quizzes/" + quiz.id() + "/submissions/latest";

		mockMvc.perform(get(latestUrl).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data").value(nullValue()));

		submit(token, quiz.id(), List.of(answer(quiz.first(), 0), answer(quiz.second(), 2)))
				.andExpect(jsonPath("$.data.correctCount").value(0));
		long retried = submissionId(submit(token, quiz.id(),
				List.of(answer(quiz.first(), 1), answer(quiz.second(), 0))));

		mockMvc.perform(get(latestUrl).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data.submissionId").value(retried))
				.andExpect(jsonPath("$.data.correctCount").value(2))
				.andExpect(jsonPath("$.data.results[0].choiceIndex").value(1))
				.andExpect(jsonPath("$.data.results[0].aiExplanation").value("페이징 해설"))
				.andExpect(jsonPath("$.data.results[1].correct").value(true));
		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].lastCorrectCount").value(2));
		assertThat(submissionCount()).isEqualTo(2);
	}

	@Test
	void 확인할_수_없는_답은_400이고_기록이_남지_않는다() throws Exception {
		QuizFixture quiz = createQuiz(userId, courseId, null, "페이징");
		QuizFixture other = createQuiz(userId, courseId, null, "스케줄링");

		// 다른 퀴즈의 문제, 같은 문제 두 번, 보기 범위 밖, -1보다 작은 번호
		submit(token, quiz.id(), List.of(answer(other.first(), 0)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("제출한 답을 확인할 수 없어요. 퀴즈를 다시 열어 주세요."));
		submit(token, quiz.id(), List.of(answer(quiz.first(), 0), answer(quiz.first(), 1)))
				.andExpect(status().isBadRequest());
		submit(token, quiz.id(), List.of(answer(quiz.first(), 3))).andExpect(status().isBadRequest());
		submit(token, quiz.id(), List.of(answer(quiz.first(), -2))).andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/quizzes/" + quiz.id() + "/submissions")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/quizzes/" + quiz.id() + "/submissions")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"answers\":[{\"questionId\":1}]}"))
				.andExpect(status().isBadRequest());

		assertThat(submissionCount()).isZero();
	}

	// ---------- 함께 지우기 ----------

	@Test
	void 과목을_지우면_퀴즈와_제출_기록도_지워진다() throws Exception {
		QuizFixture quiz = createQuiz(userId, courseId, null, "페이징");
		long otherCourseId = createCourse(token, "자료구조");
		QuizFixture kept = createQuiz(userId, otherCourseId, null, "트리");
		submit(token, quiz.id(), List.of(answer(quiz.first(), 1))).andExpect(status().isCreated());

		mockMvc.perform(delete("/api/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].id").value(kept.id()));
		assertThat(submissionCount()).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_question", Integer.class)).isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject("select count(*) from quiz_choice", Integer.class)).isEqualTo(6);
	}

	@Test
	void 자료를_지우면_그_자료로_만든_퀴즈가_지워진다() throws Exception {
		long materialId = uploadMaterial(token, courseId);
		QuizFixture fromMaterial = createQuiz(userId, courseId, materialId, "페이징");
		QuizFixture kept = createQuiz(userId, courseId, null, "스케줄링");

		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[1].id").value(fromMaterial.id()))
				.andExpect(jsonPath("$.data[1].materialId").value(materialId));

		mockMvc.perform(delete("/api/materials/" + materialId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/quizzes").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].id").value(kept.id()));
	}

	// ---------- 도우미 ----------

	private record QuizFixture(long id, long first, long second) {
	}

	// 문제 두 개: 첫 문제는 정답 1번·개념 하나·해설 있음, 둘째 문제는 정답 0번·개념 없음·해설 없음
	private QuizFixture createQuiz(long userId, long courseId, Long materialId, String topic) {
		Quiz quiz = quizRepository.save(Quiz.create(userId, courseId, materialId, topic + " 퀴즈", LocalDateTime.now()));
		List<String> choices = List.of("보기 A", "보기 B", "보기 C");
		QuizQuestion first = questionRepository.save(QuizQuestion.create(quiz.getId(), 0, topic + " 문제 1", choices,
				1, topic + " 해설", List.of(QuizConcept.of(topic.toLowerCase(), topic))));
		QuizQuestion second = questionRepository.save(QuizQuestion.create(quiz.getId(), 1, topic + " 문제 2", choices,
				0, null, List.of()));
		return new QuizFixture(quiz.getId(), first.getId(), second.getId());
	}

	private static Map<String, Object> answer(long questionId, int choiceIndex) {
		return Map.of("questionId", questionId, "choiceIndex", choiceIndex);
	}

	private ResultActions submit(String token, long quizId, List<Map<String, Object>> answers) throws Exception {
		return mockMvc.perform(post("/api/quizzes/" + quizId + "/submissions")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("answers", answers))));
	}

	private static long submissionId(ResultActions result) throws Exception {
		String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.submissionId")).longValue();
	}

	private int submissionCount() {
		return jdbcTemplate.queryForObject("select count(*) from quiz_submission", Integer.class);
	}

	private long uploadMaterial(String token, long courseId) throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "필기.txt", MediaType.APPLICATION_OCTET_STREAM_VALUE,
				TestFiles.utf8("페이징 필기"));
		String response = mockMvc.perform(multipart("/api/materials").file(file)
						.param("courseId", String.valueOf(courseId))
						.param("title", "필기")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private long createCourse(String token, String name) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", name, "semester", "2026-2", "keywords", List.of()))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private long userId(String email) {
		return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private String signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"email", email,
						"name", "테스터",
						"password", PASSWORD,
						"passwordConfirm", PASSWORD,
						"agreedTerms", true,
						"agreedPrivacy", true,
						"agreedAiAnalysis", false))))
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
