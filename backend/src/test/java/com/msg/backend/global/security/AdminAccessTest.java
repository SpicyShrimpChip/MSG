package com.msg.backend.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * /api/admin/** 은 관리자만 열린다. 역할은 토큰이 아니라 요청마다 DB에서 읽으므로 역할을 바꾸면 같은 토큰도 바로 달라진다.
 * 아직 /api/admin 아래 API가 없어서, 권한을 통과하면 404, 못 통과하면 401/403이 나온다.
 */
@IntegrationTest
class AdminAccessTest {

	private static final String ADMIN_PATH = "/api/admin/members";

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

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 일반_사용자는_관리자_경로가_403() throws Exception {
		String token = signupAndLogin("user@example.com");

		mockMvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void 역할을_바꾸면_같은_토큰도_바로_막힌다() throws Exception {
		String token = signupAndLogin("demoted@example.com");
		setRole("demoted@example.com", "ADMIN");
		mockMvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isNotFound());

		setRole("demoted@example.com", "USER");

		mockMvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void 내_정보_응답에_역할이_들어_있다() throws Exception {
		String userToken = signupAndLogin("me-user@example.com");
		String adminToken = signupAndLogin("me-admin@example.com");
		setRole("me-admin@example.com", "ADMIN");

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
				.andExpect(status().isOk())
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.role").value("USER"));
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
				.andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.role").value("ADMIN"));
	}

	private void setRole(String email, String role) {
		jdbcTemplate.update("update users set role = ? where email = ?", role, email);
	}

	private String signupAndLogin(String email) throws Exception {
		return TestApi.signupAndLogin(mockMvc, objectMapper, email, Map.of("agreedAiAnalysis", false));
	}
}
