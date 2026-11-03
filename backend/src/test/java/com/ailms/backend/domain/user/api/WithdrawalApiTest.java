package com.ailms.backend.domain.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.quiz.application.QuizFixtures;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.global.llm.LlmProvider;
import com.ailms.backend.global.llm.LlmResponse;
import com.ailms.backend.global.storage.FileStorage;
import com.ailms.backend.support.FakeLlmClients;
import com.ailms.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MockMvc;

// 회원 탈퇴: 본인·비밀번호 확인, 내 데이터와 자료 파일 삭제, 다른 회원 데이터는 그대로
@IntegrationTest
class WithdrawalApiTest {

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
	private FileStorage fileStorage;

	private String token;
	private String otherToken;

	@BeforeEach
	void setUp() throws Exception {
		fakeLlm.resetAll();
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		otherToken = signupAndLogin("other@example.com");
	}

	@Test
	void 비밀번호가_틀리면_400이고_아무것도_지우지_않는다() throws Exception {
		withdraw(token, "Wrong0pass!").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(containsString("비밀번호가 맞지 않아요")));
		withdraw(token, "").andExpect(status().isBadRequest());
		mockMvc.perform(delete("/api/users/me").contentType(MediaType.APPLICATION_JSON)
				.content("{\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isUnauthorized());

		assertThat(userRepository.count()).isEqualTo(2);
	}

	@Test
	void 탈퇴하면_내_과목_자료_파일_퀴즈_기록이_모두_지워지고_다른_회원은_그대로다() throws Exception {
		long course = createCourse(token, List.of("NAT", "VLAN"));
		long otherCourse = createCourse(otherToken, List.of("NAT"));
		String key = upload(token, course);
		upload(otherToken, otherCourse);
		fakeLlm.get(LlmProvider.GPT).respond(request -> new LlmResponse(QuizFixtures.forRequest(request.user()), 1, 1));
		mockMvc.perform(post("/api/quizzes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("requestKey", UUID.randomUUID().toString(),
								"courseId", course, "mode", "exam_prep", "questionTypes", List.of("multiple_choice"),
								"questionCount", 2))))
				.andExpect(status().isCreated());
		long myId = id("me@example.com");
		assertThat(fileStorage.exists(key)).isTrue();

		withdraw(token, PASSWORD)
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

		assertThat(userRepository.findById(myId)).isEmpty();
		for (String table : List.of("course", "material", "quiz", "quiz_generation", "quiz_ai_call", "refresh_token",
				"concept_feedback", "conversation", "analysis_run")) {
			assertThat(jdbcTemplate.queryForObject("select count(*) from " + table + " where user_id = ?",
					Integer.class, myId)).as(table).isZero();
		}
		assertThat(fileStorage.exists(key)).isFalse();
		assertThat(jdbcTemplate.queryForObject("select count(*) from material", Integer.class)).isEqualTo(1);
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
				.andExpect(jsonPath("$.data[0].id").value(otherCourse));
	}

	@Test
	void 탈퇴하면_로그인_유지_토큰으로도_다시_들어올_수_없고_같은_비밀번호로_로그인도_안_된다() throws Exception {
		String refresh = mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", "me@example.com", "password", PASSWORD))))
				.andReturn().getResponse().getCookie("refresh_token").getValue();

		withdraw(token, PASSWORD).andExpect(status().isOk());

		mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
						.cookie(new jakarta.servlet.http.Cookie("refresh_token", refresh)))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", "me@example.com", "password", PASSWORD))))
				.andExpect(status().isUnauthorized());
		assertThat(jdbcTemplate.queryForObject("select count(*) from refresh_token", Integer.class)).isEqualTo(1);
	}

	@Test
	void 마지막_관리자는_탈퇴할_수_없다() throws Exception {
		jdbcTemplate.update("update users set role = 'ADMIN' where email = 'me@example.com'");

		withdraw(token, PASSWORD).andExpect(status().isConflict());

		jdbcTemplate.update("update users set role = 'ADMIN' where email = 'other@example.com'");
		withdraw(token, PASSWORD).andExpect(status().isOk());
	}

	private org.springframework.test.web.servlet.ResultActions withdraw(String token, String password)
			throws Exception {
		return mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("password", password))));
	}

	private String upload(String token, long courseId) throws Exception {
		String response = mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", "노트.txt", "text/plain", "NAT 정리 노트".getBytes()))
						.param("title", "노트").param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		long materialId = ((Number) JsonPath.read(response, "$.data.id")).longValue();
		return jdbcTemplate.queryForObject("select storage_key from material where id = ?", String.class, materialId);
	}

	private long id(String email) {
		return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
	}

	private long createCourse(String token, List<String> keywords) throws Exception {
		String response = mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", "네트워크", "semester", "2026-2", "keywords", keywords))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private String signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "name", "테스터", "password",
								PASSWORD, "passwordConfirm", PASSWORD, "agreedTerms", true, "agreedPrivacy", true,
								"agreedAiAnalysis", true, "agreedAiQuiz", true))))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/auth/login").header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
