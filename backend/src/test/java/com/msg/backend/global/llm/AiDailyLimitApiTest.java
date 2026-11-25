package com.msg.backend.global.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.quiz.application.QuizFixtures;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.FakeLlmClients;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.MutableClock;
import com.msg.backend.support.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 서비스 전체 하루 AI 호출 상한 (app.ai-daily-limit). 통합 테스트 설정의 상한은 1,000,000(TestLlm)이라
 * 오늘 줄을 상한 근처로 직접 채워서 확인한다.
 */
@IntegrationTest
class AiDailyLimitApiTest {

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
	private AiCallQuota quota;
	@Autowired
	private DbAiCallQuota dbQuota;

	private String token;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		clock.reset();
		fakeLlm.resetAll();
		jdbcTemplate.update("DELETE FROM ai_daily_usage");
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("limit@example.com");
		courseId = createCourse("네트워크 보안", List.of("NAT", "VLAN"));
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("DELETE FROM ai_daily_usage");
	}

	@Test
	void 모델을_부를_때마다_오늘_줄에_한_번씩_센다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create().andExpect(status().isCreated());

		assertThat(todayCount()).isEqualTo(fakeLlm.get(LlmProvider.GPT).requests().size()).isPositive();
	}

	@Test
	void 오늘_상한에_닿으면_퀴즈_만들기는_429이고_모델을_부르지_않는다() throws Exception {
		fillToday(LIMIT);
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));

		create().andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.message").value(AiCallQuota.EXHAUSTED_MESSAGE));

		assertThat(fakeLlm.get(LlmProvider.GPT).requests()).isEmpty();
		assertThat(todayCount()).isEqualTo(LIMIT);
	}

	@Test
	void 상한_직전이면_한_번만_받아_가고_그다음은_막는다() {
		fillToday(LIMIT - 1);

		assertThat(quota.hasRemaining()).isTrue();
		assertThat(quota.tryAcquire()).isTrue();
		assertThat(quota.tryAcquire()).isFalse();
		assertThat(quota.hasRemaining()).isFalse();
		assertThat(todayCount()).isEqualTo(LIMIT);
	}

	@Test
	void 날짜가_바뀌면_다시_부를_수_있다() {
		fillToday(LIMIT);
		assertThat(quota.tryAcquire()).isFalse();

		clock.advance(Duration.ofDays(1));

		assertThat(quota.tryAcquire()).isTrue();
		assertThat(todayCount()).isEqualTo(1);
	}

	// 동시 요청 재현: 다른 요청이 "줄 없음"을 본 뒤 이 요청이 줄을 만들고 수를 올린 상태에서, 그 요청이 줄 만들기를 부른다
	@Test
	void 줄_만들기는_이미_있는_줄의_수를_덮어쓰지_않는다() {
		fillToday(5);

		dbQuota.createRow(today());

		assertThat(todayCount()).isEqualTo(5);
		assertThat(quota.tryAcquire()).isTrue();
		assertThat(todayCount()).isEqualTo(6);
	}

	@Test
	void 과목을_지워도_오늘_센_수는_줄지_않는다() throws Exception {
		fakeLlm.get(LlmProvider.GPT).answer(QuizFixtures.threeTypes(false));
		create().andExpect(status().isCreated());
		int before = todayCount();

		mockMvc.perform(delete("/api/courses/" + courseId)
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().is2xxSuccessful());

		assertThat(todayCount()).isEqualTo(before).isPositive();
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
	}

	private void fillToday(int count) {
		jdbcTemplate.update("DELETE FROM ai_daily_usage");
		jdbcTemplate.update("INSERT INTO ai_daily_usage (usage_date, call_count) VALUES (?, ?)", today(), count);
	}

	private int todayCount() {
		List<Integer> counts = jdbcTemplate.queryForList(
				"SELECT call_count FROM ai_daily_usage WHERE usage_date = ?", Integer.class, today());
		return counts.isEmpty() ? 0 : counts.get(0);
	}

	private ResultActions create() throws Exception {
		return mockMvc.perform(post("/api/quizzes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"requestKey", UUID.randomUUID().toString(),
						"courseId", courseId,
						"mode", "exam_prep",
						"questionTypes", List.of("multiple_choice", "short_answer", "essay"),
						"questionCount", 3))));
	}

	private long createCourse(String name, List<String> keywords) throws Exception {
		return TestApi.createCourse(mockMvc, objectMapper, token, name, keywords);
	}

	private String signupAndLogin(String email) throws Exception {
		return TestApi.signupAndLogin(mockMvc, objectMapper, email,
				Map.of("agreedAiAnalysis", true, "agreedAiQuiz", true));
	}
}
