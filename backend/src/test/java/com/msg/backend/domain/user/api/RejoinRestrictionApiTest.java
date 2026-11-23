package com.msg.backend.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.user.application.RejoinRestriction;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.domain.user.dao.WithdrawnAccountRepository;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.MutableClock;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 탈퇴 뒤 30일 재가입 제한: 이메일 원문 없이 키 해시만 남기고, 기간이 지나면 가입되고 기록은 지워진다
@IntegrationTest
@TestPropertySource(properties = "app.rejoin.hash-secret=test-rejoin-secret")
class RejoinRestrictionApiTest {

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
	private WithdrawnAccountRepository withdrawnAccountRepository;
	@Autowired
	private RejoinRestriction rejoinRestriction;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		withdrawnAccountRepository.deleteAllInBatch();
	}

	@AfterEach
	void tearDown() {
		clock.reset();
	}

	@Test
	void 탈퇴한_이메일은_30일_동안_다시_가입할_수_없고_기간이_지나면_가입된다() throws Exception {
		withdraw(signupAndLogin("me@example.com")).andExpect(status().isOk());

		signup("Me@Example.com").andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(containsString("탈퇴한 이메일은 30일 동안 다시 가입할 수 없어요")))
				.andExpect(jsonPath("$.message").value(containsString("이후에 가입할 수 있어요")));
		assertThat(userRepository.count()).isZero();

		clock.advance(Duration.ofDays(29));
		signup("me@example.com").andExpect(status().isConflict());

		clock.advance(Duration.ofDays(1).plusMinutes(1));
		signup("me@example.com").andExpect(status().isCreated());
	}

	@Test
	void 이메일_원문은_남기지_않고_다른_이메일의_가입은_막지_않는다() throws Exception {
		withdraw(signupAndLogin("me@example.com")).andExpect(status().isOk());

		List<String> hashes = jdbcTemplate.queryForList("select email_hash from withdrawn_account", String.class);
		assertThat(hashes).hasSize(1);
		assertThat(hashes.get(0)).hasSize(64).doesNotContain("example");

		signup("other@example.com").andExpect(status().isCreated());
	}

	@Test
	void 기간이_지난_기록만_정리_작업이_지운다() throws Exception {
		withdraw(signupAndLogin("old@example.com")).andExpect(status().isOk());
		clock.advance(Duration.ofDays(20));
		withdraw(signupAndLogin("new@example.com")).andExpect(status().isOk());

		clock.advance(Duration.ofDays(11));
		assertThat(rejoinRestriction.purgeExpired()).isEqualTo(1);
		assertThat(withdrawnAccountRepository.count()).isEqualTo(1);
		signup("new@example.com").andExpect(status().isConflict());
	}

	@Test
	void 기간이_지나_다시_가입했다가_또_탈퇴하면_기간이_새로_시작된다() throws Exception {
		withdraw(signupAndLogin("me@example.com")).andExpect(status().isOk());
		clock.advance(Duration.ofDays(31));

		withdraw(signupAndLogin("me@example.com")).andExpect(status().isOk());

		assertThat(withdrawnAccountRepository.count()).isEqualTo(1);
		signup("me@example.com").andExpect(status().isConflict());
	}

	private ResultActions signup(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "name", "테스터", "password", PASSWORD,
						"passwordConfirm", PASSWORD, "agreedTerms", true, "agreedPrivacy", true, "agreedAiAnalysis",
						true, "agreedAiQuiz", true))));
	}

	private String signupAndLogin(String email) throws Exception {
		signup(email).andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}

	private ResultActions withdraw(String token) throws Exception {
		return mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("password", PASSWORD))));
	}
}
