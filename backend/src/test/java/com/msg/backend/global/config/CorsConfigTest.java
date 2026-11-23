package com.msg.backend.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

// 개발 서버 주소(5173, Vite가 자리를 옮긴 5174)에서 온 요청은 받고, 다른 출처는 막는다
@IntegrationTest
class CorsConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@ParameterizedTest
	@ValueSource(strings = {"http://localhost:5173", "http://localhost:5174"})
	void 개발_서버_출처의_요청은_받는다(String origin) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
						.header(HttpHeaders.ORIGIN, origin)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{}"))
				// 출처는 통과하고 입력 검증까지 도달한다
				.andExpect(status().isBadRequest())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
	}

	@Test
	void 허용하지_않은_출처는_막는다() throws Exception {
		mockMvc.perform(options("/api/auth/login")
						.header(HttpHeaders.ORIGIN, "http://evil.example")
						.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
				.andExpect(status().isForbidden());
	}
}
