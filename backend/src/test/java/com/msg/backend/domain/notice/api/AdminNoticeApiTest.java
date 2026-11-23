package com.msg.backend.domain.notice.api;

import static com.msg.backend.support.TestApi.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
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
 * 관리자 공지 작성·수정·삭제. 샘플 공지(id 1~3)는 NoticeApiTest가 목록으로 확인하므로,
 * 이 테스트가 만든 공지(id 4~)는 끝날 때마다 지워 DB를 시드 상태로 돌려놓는다.
 */
@IntegrationTest
class AdminNoticeApiTest {

	private static final long LAST_SEED_ID = 3;

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

	private String adminToken;
	private String userToken;

	@BeforeEach
	void setUp() throws Exception {
		jdbcTemplate.update("delete from notice where id > ?", LAST_SEED_ID);
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		adminToken = signupAndLogin("admin@example.com");
		jdbcTemplate.update("update users set role = 'ADMIN' where email = ?", "admin@example.com");
		userToken = signupAndLogin("user@example.com");
	}

	@AfterEach
	void tearDown() {
		jdbcTemplate.update("delete from notice where id > ?", LAST_SEED_ID);
	}

	// ---------- 권한 ----------

	@Test
	void 일반_사용자는_작성도_수정도_삭제도_403이고_공지는_그대로다() throws Exception {
		write(userToken, "해킹 공지", "본문").andExpect(status().isForbidden());
		mockMvc.perform(put("/api/admin/notices/1").header(HttpHeaders.AUTHORIZATION, bearer(userToken))
				.contentType(MediaType.APPLICATION_JSON).content(body("바꾼 제목", "바꾼 본문")))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/admin/notices/1").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/notices/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("서비스를 시작했어요"));
		assertThat(noticeCount()).isEqualTo(3);
	}

	// ---------- 작성 ----------

	@Test
	void 관리자가_작성하면_201과_본문을_돌려주고_작성자와_오늘_날짜를_저장한다() throws Exception {
		write(adminToken, "  새 공지  ", "  안녕하세요.\n두 번째 줄  ")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.title").value("새 공지"))
				.andExpect(jsonPath("$.data.content").value("안녕하세요.\n두 번째 줄"))
				.andExpect(jsonPath("$.data.createdAt").isString())
				.andExpect(jsonPath("$.data.updatedAt").value(nullValue()))
				.andExpect(jsonPath("$.data.authorId").doesNotExist());

		Long authorId = jdbcTemplate.queryForObject(
				"select author_id from notice where title = ?", Long.class, "새 공지");
		Long adminId = jdbcTemplate.queryForObject(
				"select id from users where email = ?", Long.class, "admin@example.com");
		assertThat(authorId).isEqualTo(adminId);
	}

	@Test
	void 작성한_공지는_목록_맨_위에_공개_상세로_보인다() throws Exception {
		String response = write(adminToken, "맨 위 공지", "본문").andReturn().getResponse().getContentAsString();
		int id = JsonPath.read(response, "$.data.id");

		mockMvc.perform(get("/api/notices"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(4)))
				.andExpect(jsonPath("$.data[0].id").value(id))
				.andExpect(jsonPath("$.data[0].title").value("맨 위 공지"))
				.andExpect(jsonPath("$.data[0].content").doesNotExist());
		mockMvc.perform(get("/api/notices/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.content").value("본문"));
	}

	@Test
	void 제목이나_본문이_비었거나_100자_5000자를_넘으면_400이고_저장하지_않는다() throws Exception {
		write(adminToken, "   ", "본문")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("제목을 입력해 주세요."));
		write(adminToken, "제목", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("본문을 입력해 주세요."));
		write(adminToken, "가".repeat(101), "본문")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("제목은 100자까지 입력할 수 있어요."));
		write(adminToken, "제목", "가".repeat(5001))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("본문은 5000자까지 입력할 수 있어요."));
		assertThat(noticeCount()).isEqualTo(3);

		write(adminToken, "가".repeat(100), "나".repeat(5000)).andExpect(status().isCreated());
	}

	@Test
	void 본문의_HTML은_해석하지_않고_글자_그대로_저장해_돌려준다() throws Exception {
		String html = "<script>alert(1)</script> <b>굵게</b>";

		write(adminToken, "HTML 공지", html)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.content").value(html));
	}

	// ---------- 수정·삭제 ----------

	@Test
	void 수정하면_내용이_바뀌고_수정_시각이_채워진다() throws Exception {
		String response = write(adminToken, "고치기 전", "원래 본문").andReturn().getResponse().getContentAsString();
		int id = JsonPath.read(response, "$.data.id");

		mockMvc.perform(put("/api/admin/notices/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(body("고친 제목", "고친 본문")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.title").value("고친 제목"))
				.andExpect(jsonPath("$.data.content").value("고친 본문"))
				.andExpect(jsonPath("$.data.updatedAt").isString());

		mockMvc.perform(get("/api/notices/" + id))
				.andExpect(jsonPath("$.data.title").value("고친 제목"))
				.andExpect(jsonPath("$.data.updatedAt").isString());
	}

	@Test
	void 없는_공지를_수정하거나_지우면_404() throws Exception {
		mockMvc.perform(put("/api/admin/notices/99999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
				.contentType(MediaType.APPLICATION_JSON).content(body("제목", "본문")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("공지사항을 찾을 수 없어요."));
		mockMvc.perform(delete("/api/admin/notices/99999").header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
				.andExpect(status().isNotFound());
	}

	@Test
	void 삭제하면_목록과_상세에서_사라진다() throws Exception {
		String response = write(adminToken, "지울 공지", "본문").andReturn().getResponse().getContentAsString();
		int id = JsonPath.read(response, "$.data.id");

		mockMvc.perform(delete("/api/admin/notices/" + id).header(HttpHeaders.AUTHORIZATION, bearer(adminToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		mockMvc.perform(get("/api/notices/" + id)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/notices")).andExpect(jsonPath("$.data", hasSize(3)));
	}

	@Test
	void 작성한_관리자가_탈퇴해도_공지는_남는다() throws Exception {
		write(adminToken, "남는 공지", "본문").andExpect(status().isCreated());

		refreshTokenRepository.deleteAllInBatch();
		jdbcTemplate.update("delete from users where email = ?", "admin@example.com");

		Integer count = jdbcTemplate.queryForObject(
				"select count(*) from notice where title = ? and author_id is null", Integer.class, "남는 공지");
		assertThat(count).isEqualTo(1);
	}

	// ---------- 도우미 ----------

	private ResultActions write(String token, String title, String content) throws Exception {
		return mockMvc.perform(post("/api/admin/notices")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(title, content)));
	}

	private String body(String title, String content) throws Exception {
		return objectMapper.writeValueAsString(Map.of("title", title, "content", content));
	}

	private int noticeCount() {
		return jdbcTemplate.queryForObject("select count(*) from notice", Integer.class);
	}

	private String signupAndLogin(String email) throws Exception {
		return TestApi.signupAndLogin(mockMvc, objectMapper, email, Map.of("agreedAiAnalysis", false));
	}
}
