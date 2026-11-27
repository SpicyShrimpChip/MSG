package com.msg.backend.domain.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestKakao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

// 서버에 카카오 키가 없으면(로컬·CI·PR 미리보기) 카카오 로그인을 숨기고, 시작 주소로 와도 로그인 화면으로 돌려보낸다
@IntegrationTest
@TestPropertySource(properties = "app.oauth.kakao.client-id=")
class SocialAuthUnavailableTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 키가_없으면_providers는_비어_있다() throws Exception {
		mockMvc.perform(get("/api/auth/social/providers"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void 키가_없으면_start는_로그인_화면으로_돌려보낸다() throws Exception {
		mockMvc.perform(get("/api/auth/kakao/start"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", TestKakao.FRONTEND + "/login?socialError=unavailable"));
	}

	@Test
	void 키가_없어도_돌아갈_화면은_넘긴다() throws Exception {
		mockMvc.perform(get("/api/auth/kakao/start").param("returnTo", "/analysis"))
				.andExpect(header().string("Location",
						TestKakao.FRONTEND + "/login?socialError=unavailable&returnTo=%2Fanalysis"));
	}
}
