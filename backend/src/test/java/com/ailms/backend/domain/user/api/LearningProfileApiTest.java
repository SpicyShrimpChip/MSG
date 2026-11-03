package com.ailms.backend.domain.user.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// 가입·마이페이지의 학습 목적·관심 직무
@IntegrationTest
class LearningProfileApiTest {

	private static final String PASSWORD = "Passw0rd!";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	@BeforeEach
	void setUp() {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	void 가입할_때_학습_목적과_관심_직무를_저장한다() throws Exception {
		Map<String, Object> body = signup("me@example.com");
		body.put("learningPurpose", "work");
		body.put("jobField", "software_development");

		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.learningPurpose").value("work"))
				.andExpect(jsonPath("$.data.jobField").value("software_development"))
				.andExpect(jsonPath("$.data.jobFieldOther").value(nullValue()));

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(login("me@example.com"))))
				.andExpect(jsonPath("$.data.learningPurpose").value("work"))
				.andExpect(jsonPath("$.data.jobField").value("software_development"));
	}

	@Test
	void 예전_가입_요청처럼_목적을_빼면_아직_정하지_않음이고_로그인할_수_있다() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(signup("old@example.com"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.learningPurpose").value("undecided"))
				.andExpect(jsonPath("$.data.jobField").value(nullValue()));

		login("old@example.com");
	}

	@Test
	void 모르는_목적이나_직무_기타_이름_없음은_400이고_가입되지_않는다() throws Exception {
		Map<String, Object> wrongPurpose = signup("a@example.com");
		wrongPurpose.put("learningPurpose", "hobby");
		Map<String, Object> otherWithoutName = signup("b@example.com");
		otherWithoutName.put("jobField", "other");

		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(wrongPurpose)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("학습 목적을 다시 골라 주세요."));
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(otherWithoutName)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("기타 직무 이름을 적어 주세요."));
		org.assertj.core.api.Assertions.assertThat(userRepository.count()).isZero();
	}

	@Test
	void 마이페이지에서_목적과_직무를_바꾼다_기타는_이름을_받고_다른_직무로_바꾸면_이름을_지운다() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signup("me@example.com")))).andExpect(status().isCreated());
		String token = login("me@example.com");

		updateProfile(token, Map.of("learningPurpose", "work", "jobField", "other", "jobFieldOther", " 보안 감사 "))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.learningPurpose").value("work"))
				.andExpect(jsonPath("$.data.jobField").value("other"))
				.andExpect(jsonPath("$.data.jobFieldOther").value("보안 감사"));

		updateProfile(token, Map.of("learningPurpose", "exam_prep", "jobField", "it_consulting", "jobFieldOther", "무시"))
				.andExpect(jsonPath("$.data.learningPurpose").value("exam_prep"))
				.andExpect(jsonPath("$.data.jobField").value("it_consulting"))
				.andExpect(jsonPath("$.data.jobFieldOther").value(nullValue()));

		updateProfile(token, Map.of("learningPurpose", "self_development"))
				.andExpect(jsonPath("$.data.jobField").value(nullValue()));
	}

	@Test
	void 프로필_수정_검사와_인증() throws Exception {
		mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(signup("me@example.com")))).andExpect(status().isCreated());
		String token = login("me@example.com");

		updateProfile(token, Map.of("jobField", "it_consulting")).andExpect(status().isBadRequest());
		updateProfile(token, Map.of("learningPurpose", "work", "jobField", "pilot")).andExpect(status().isBadRequest());
		updateProfile(token, Map.of("learningPurpose", "work", "jobField", "other", "jobFieldOther", "a".repeat(31)))
				.andExpect(status().isBadRequest());
		updateProfile(token, Map.of("learningPurpose", "work", "jobField", "other", "jobFieldOther", "010-1234-5678"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(patch("/api/users/me/profile").contentType(MediaType.APPLICATION_JSON)
				.content("{\"learningPurpose\":\"work\"}")).andExpect(status().isUnauthorized());
	}

	private ResultActions updateProfile(String token, Map<String, Object> body) throws Exception {
		return mockMvc.perform(patch("/api/users/me/profile").header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
	}

	private static Map<String, Object> signup(String email) {
		Map<String, Object> body = new HashMap<>();
		body.put("email", email);
		body.put("name", "테스터");
		body.put("password", PASSWORD);
		body.put("passwordConfirm", PASSWORD);
		body.put("agreedTerms", true);
		body.put("agreedPrivacy", true);
		body.put("agreedAiAnalysis", false);
		return body;
	}

	private String login(String email) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login")
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}
}
