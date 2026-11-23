package com.msg.backend.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

// 로그인하지 않았거나 토큰이 잘못되면 보호된 API는 모두 401과 같은 문구 (기능마다 있던 401 확인을 한곳에 모았다)
@IntegrationTest
class UnauthenticatedAccessTest {

	// 기능마다 읽기·쓰기·삭제 대표 경로. 새 기능 API를 만들면 여기에 한 줄 더한다
	private static final List<String> PROTECTED = List.of(
			"GET /api/users/me",
			"PATCH /api/users/me/consents",
			"GET /api/courses", "POST /api/courses", "PATCH /api/courses/1", "DELETE /api/courses/1",
			"GET /api/conversations", "POST /api/conversations", "DELETE /api/conversations/1",
			"GET /api/analysis/providers", "POST /api/analysis/courses/1/runs", "DELETE /api/analysis/feedback/1",
			"POST /api/admin/notices", "PUT /api/admin/notices/1", "DELETE /api/admin/notices/1");

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 토큰이_없거나_잘못되면_보호된_API는_모두_401() throws Exception {
		for (String route : PROTECTED) {
			String[] parts = route.split(" ");
			MockHttpServletResponse response = mockMvc.perform(request(HttpMethod.valueOf(parts[0]), parts[1])
							.contentType(MediaType.APPLICATION_JSON).content("{}"))
					.andReturn().getResponse();
			assertThat(response.getStatus()).as(route).isEqualTo(401);
			assertThat(response.getContentAsString(StandardCharsets.UTF_8)).as(route)
					.contains("\"success\":false")
					.contains(ApiErrorResponder.UNAUTHORIZED_MESSAGE);
		}

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value(ApiErrorResponder.UNAUTHORIZED_MESSAGE));
	}
}
