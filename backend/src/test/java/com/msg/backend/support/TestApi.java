package com.msg.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 통합 테스트 여러 곳에서 같이 쓰는 API 도우미: 가입·로그인, 과목 만들기, 인증 헤더.
 * 테스트마다 다른 값(선택 동의, 과목 이름·키워드)만 인자로 받는다.
 */
public final class TestApi {

	public static final String PASSWORD = "Passw0rd!";

	private TestApi() {
	}

	public static String bearer(String token) {
		return "Bearer " + token;
	}

	/**
	 * 필수 약관에 동의해 가입하고 로그인해 access token을 돌려준다.
	 *
	 * @param consents 선택 동의만 담는다 (agreedAiAnalysis, agreedAiQuiz). 넣지 않은 항목은 요청에서 빠진다
	 */
	public static String signupAndLogin(MockMvc mockMvc, ObjectMapper objectMapper, String email,
			Map<String, Object> consents) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("email", email);
		body.put("name", "테스터");
		body.put("password", PASSWORD);
		body.put("passwordConfirm", PASSWORD);
		body.put("agreedTerms", true);
		body.put("agreedPrivacy", true);
		body.putAll(consents);
		mockMvc.perform(post("/api/auth/signup")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(body)))
				.andExpect(status().isCreated());
		return login(mockMvc, objectMapper, email);
	}

	// 이미 가입한 이메일로 비밀번호(PASSWORD) 로그인해 access token을 돌려준다
	public static String login(MockMvc mockMvc, ObjectMapper objectMapper, String email) throws Exception {
		String response = mockMvc.perform(post("/api/auth/login")
						.header("X-Requested-With", "XMLHttpRequest")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}

	// 2026-2 학기 과목을 만들고 id를 돌려준다
	public static long createCourse(MockMvc mockMvc, ObjectMapper objectMapper, String token, String name,
			List<String> keywords) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", name, "semester", "2026-2", "keywords", keywords))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}
}
