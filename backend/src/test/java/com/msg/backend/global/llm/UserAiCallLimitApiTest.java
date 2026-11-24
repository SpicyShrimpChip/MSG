package com.msg.backend.global.llm;

import static com.msg.backend.support.TestApi.PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.quiz.application.QuizFixtures;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.FakeLlmClients;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestApi;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 사용자별 하루 AI 호출 몫 (app.ai-daily-limit.user-calls, user_ai_call_daily). 통합 테스트 설정의 몫은 1,000,000(TestLlm)이라
 * 오늘 줄을 몫 근처로 직접 채워서 확인한다. 분석(모델 3개)의 확인은 AnalysisApiTest에 있다.
 */
@IntegrationTest
class UserAiCallLimitApiTest {

	private static final int LIMIT = 1_000_000;

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
	@Autowired
	private DbAiCallQuota quota;

	private String token;
	private long userId;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		fakeLlm.resetAll();
		jdbcTemplate.update("DELETE FROM user_ai_call_daily");
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		userId = userId("me@example.com");
		courseId = TestApi.createCourse(mockMvc, objectMapper, token, "네트워크 보안", List.of("NAT", "VLAN"));
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("DELETE FROM user_ai_call_daily");
	}

	@Test
	void 모델을_부를_때마다_그_사용자의_오늘_줄에_한_번씩_센다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create(token, courseId).andExpect(status().isCreated());

		assertThat(todayCount(userId)).isEqualTo(fakeLlm.get(LlmProvider.GPT).requests().size()).isPositive();
	}

	@Test
	void 몫을_다_쓰면_퀴즈_만들기는_429이고_모델을_부르지_않는다_다른_사용자는_그대로_쓴다() throws Exception {
		fill(userId, LIMIT);
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create(token, courseId).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value(AiCallQuota.USER_EXHAUSTED_MESSAGE));
		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();

		String other = signupAndLogin("other@example.com");
		long otherCourse = TestApi.createCourse(mockMvc, objectMapper, other, "네트워크 보안", List.of("NAT", "VLAN"));
		create(other, otherCourse).andExpect(status().isCreated());
		assertThat(todayCount(userId)).isEqualTo(LIMIT);
	}

	@Test
	void 몫_직전이면_한_번만_받아_가고_그다음은_막는다() {
		fill(userId, LIMIT - 1);

		assertThat(quota.hasUserRemaining(userId, 1)).isTrue();
		assertThat(quota.hasUserRemaining(userId, 2)).isFalse();
		assertThat(quota.tryAcquireForUser(userId)).isTrue();
		assertThat(quota.tryAcquireForUser(userId)).isFalse();
		assertThat(todayCount(userId)).isEqualTo(LIMIT);
	}

	@Test
	void 날짜가_바뀌면_다시_쓰고_지난_줄은_주기_작업이_지운다() {
		fill(userId, LIMIT);
		assertThat(quota.tryAcquireForUser(userId)).isFalse();

		clock.advance(Duration.ofDays(2));

		assertThat(quota.tryAcquireForUser(userId)).isTrue();
		assertThat(quota.purgeOldUserRows()).isEqualTo(1);
		assertThat(todayCount(userId)).isEqualTo(1);
	}

	@Test
	void 과목을_지워도_줄지_않고_탈퇴하면_지운다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		create(token, courseId).andExpect(status().isCreated());
		int before = todayCount(userId);

		mockMvc.perform(delete("/api/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, TestApi.bearer(token)))
				.andExpect(status().is2xxSuccessful());
		assertThat(todayCount(userId)).isEqualTo(before).isPositive();

		mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, TestApi.bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))))
				.andExpect(status().is2xxSuccessful());
		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_ai_call_daily WHERE user_id = ?",
				Integer.class, userId)).isZero();
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
	}

	private void fill(long user, int count) {
		jdbcTemplate.update("DELETE FROM user_ai_call_daily WHERE user_id = ?", user);
		jdbcTemplate.update("INSERT INTO user_ai_call_daily (user_id, usage_date, call_count) VALUES (?, ?, ?)",
				user, today(), count);
	}

	private int todayCount(long user) {
		List<Integer> counts = jdbcTemplate.queryForList(
				"SELECT call_count FROM user_ai_call_daily WHERE user_id = ? AND usage_date = ?", Integer.class, user,
				today());
		return counts.isEmpty() ? 0 : counts.get(0);
	}

	private long userId(String email) {
		return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	private ResultActions create(String accessToken, long course) throws Exception {
		return mockMvc.perform(post("/api/quizzes")
				.header(HttpHeaders.AUTHORIZATION, TestApi.bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"requestKey", UUID.randomUUID().toString(),
						"courseId", course,
						"mode", "exam_prep",
						"questionTypes", List.of("multiple_choice", "short_answer", "essay"),
						"questionCount", 3))));
	}

	private String signupAndLogin(String email) throws Exception {
		return TestApi.signupAndLogin(mockMvc, objectMapper, email,
				Map.of("agreedAiAnalysis", true, "agreedAiQuiz", true));
	}
}
