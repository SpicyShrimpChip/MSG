package com.ailms.backend.domain.notice.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

// 실제 설정(Flyway 마이그레이션 + 샘플 공지)으로 띄워 공통 응답 형식과 공지 목록을 확인한다
@IntegrationTest
class NoticeApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void 공지_목록을_최신순으로_공통_응답에_담아_돌려준다() throws Exception {
		mockMvc.perform(get("/api/notices"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.message").value(""))
				.andExpect(jsonPath("$.data", hasSize(3)))
				.andExpect(jsonPath("$.data[0].id").value(3))
				.andExpect(jsonPath("$.data[0].title").value("AI 대화 가져오기를 열었어요"))
				.andExpect(jsonPath("$.data[0].createdAt").value("2026-10-24"))
				.andExpect(jsonPath("$.data[1].id").value(2))
				.andExpect(jsonPath("$.data[2].id").value(1))
				.andExpect(jsonPath("$.data[2].createdAt").value("2026-10-15"));
	}

	@Test
	void 공지_상세는_로그인_없이_본문까지_돌려준다() throws Exception {
		mockMvc.perform(get("/api/notices/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").value(1))
				.andExpect(jsonPath("$.data.title").value("서비스를 시작했어요"))
				.andExpect(jsonPath("$.data.content").isNotEmpty())
				.andExpect(jsonPath("$.data.createdAt").value("2026-10-15"))
				.andExpect(jsonPath("$.data.updatedAt").value(nullValue()));
	}

	@Test
	void 없는_공지_상세는_404와_안내_문구를_돌려준다() throws Exception {
		mockMvc.perform(get("/api/notices/99999"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("공지사항을 찾을 수 없어요."));
	}

	// 로그인하지 않으면 없는 주소도 401로 막힌다 (주소 존재 여부를 알려 주지 않는다). 그래서 로그인한 상태로 확인한다
	@Test
	@WithMockUser
	void 없는_주소는_404와_공통_오류_형식으로_돌려준다() throws Exception {
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("요청한 주소를 찾을 수 없어요."))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	void 로그인하지_않고_없는_주소에_접근하면_401을_돌려준다() throws Exception {
		mockMvc.perform(get("/api/does-not-exist"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("로그인이 필요해요. 다시 로그인해 주세요."));
	}

	@Test
	@WithMockUser
	void 지원하지_않는_요청_방식은_405로_돌려준다() throws Exception {
		mockMvc.perform(delete("/api/notices"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("지원하지 않는 요청 방식이에요."));
	}

	@Test
	void Swagger_API_문서를_제공한다() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/notices']").exists())
				.andExpect(jsonPath("$.paths['/api/auth/login']").exists());
	}
}
