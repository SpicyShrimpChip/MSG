package com.ailms.backend.domain.course.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 내 과목 API. 회원을 지우면 과목·키워드도 함께 지워지므로(ON DELETE CASCADE) 매번 회원부터 새로 만든다.
 */
@IntegrationTest
class CourseApiTest {

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

	private String token;
	private String otherToken;

	@BeforeEach
	void setUp() throws Exception {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		otherToken = signupAndLogin("other@example.com");
	}

	// ---------- 인증·소유권 ----------

	@Test
	void 토큰이_없으면_모두_401() throws Exception {
		mockMvc.perform(get("/api/courses")).andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON)
				.content(body("운영체제", "2026-2", List.of()))).andExpect(status().isUnauthorized());
		mockMvc.perform(patch("/api/courses/1").contentType(MediaType.APPLICATION_JSON)
				.content(body("운영체제", "2026-2", List.of()))).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/courses/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void 목록에는_내_과목만_나온다() throws Exception {
		create(token, "운영체제", "2026-2", List.of("프로세스")).andExpect(status().isCreated());
		create(otherToken, "정보보호개론", "2026-2", List.of()).andExpect(status().isCreated());

		mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data", hasSize(1)))
				.andExpect(jsonPath("$.data[0].name").value("운영체제"))
				.andExpect(jsonPath("$.data[0].keywords", contains("프로세스")));
	}

	@Test
	void 남의_과목은_수정도_삭제도_404이고_그대로_남는다() throws Exception {
		long id = createdId(create(otherToken, "정보보호개론", "2026-2", List.of("암호")));

		update(token, id, "바꾼 이름", "2026-2", List.of())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("과목을 찾을 수 없어요."));
		mockMvc.perform(delete("/api/courses/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(jsonPath("$.data[0].name").value("정보보호개론"))
				.andExpect(jsonPath("$.data[0].keywords", contains("암호")));
	}

	// ---------- 추가 ----------

	@Test
	void 추가하면_201과_정리한_이름_키워드를_돌려준다() throws Exception {
		create(token, "  운영  체제 ", "2026-2", List.of(" 프로세스 ", "스케줄링", "프로세스", "  ", "Paging", "paging"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.name").value("운영 체제"))
				.andExpect(jsonPath("$.data.semester").value("2026-2"))
				.andExpect(jsonPath("$.data.keywords", contains("프로세스", "스케줄링", "Paging")));
	}

	@Test
	void 키워드가_없어도_추가할_수_있다() throws Exception {
		create(token, "운영체제", "2026-summer", List.of())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.keywords", hasSize(0)));
	}

	@Test
	void 입력_규칙을_어기면_400과_안내_문구() throws Exception {
		create(token, "   ", "2026-2", List.of())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("과목 이름을 입력해 주세요."));
		create(token, "가".repeat(41), "2026-2", List.of())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("과목 이름은 40자까지 넣을 수 있어요."));
		create(token, "운영체제", "2026-3", List.of())
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("학기를 다시 골라 주세요."));
		create(token, "운영체제", "2026-2", Collections.nCopies(21, "키워드"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("키워드는 20개까지 넣을 수 있어요."));
		create(token, "운영체제", "2026-2", List.of("가".repeat(21)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("키워드는 20자까지 넣을 수 있어요."));
		mockMvc.perform(post("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"운영체제\",\"semester\":\"2026-2\"}"))
				.andExpect(status().isBadRequest());

		assertThat(courseCount()).isZero();
	}

	@Test
	void 같은_학기에_같은_이름은_대소문자가_달라도_409() throws Exception {
		create(token, "Database", "2026-2", List.of()).andExpect(status().isCreated());

		create(token, "database", "2026-2", List.of())
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("이 학기에 같은 이름의 과목이 이미 있어요."));
		create(token, "Database", "2026-1", List.of()).andExpect(status().isCreated());
		create(otherToken, "Database", "2026-2", List.of()).andExpect(status().isCreated());
	}

	// ---------- 수정 ----------

	@Test
	void 수정하면_이름_학기_키워드를_모두_바꾼다() throws Exception {
		long id = createdId(create(token, "운영체제", "2026-2", List.of("프로세스", "스케줄링")));

		update(token, id, "운영체제론", "2026-winter", List.of("페이징", "프로세스"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(id))
				.andExpect(jsonPath("$.data.name").value("운영체제론"))
				.andExpect(jsonPath("$.data.semester").value("2026-winter"))
				.andExpect(jsonPath("$.data.keywords", contains("페이징", "프로세스")));

		mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[0].keywords", contains("페이징", "프로세스")));
		assertThat(jdbcTemplate.queryForObject("select count(*) from course_keyword", Integer.class)).isEqualTo(2);
	}

	@Test
	void 자기_이름_그대로_수정하면_통과하고_다른_과목_이름이면_409() throws Exception {
		long id = createdId(create(token, "운영체제", "2026-2", List.of()));
		create(token, "자료구조", "2026-2", List.of()).andExpect(status().isCreated());

		update(token, id, "운영체제", "2026-2", List.of("커널")).andExpect(status().isOk());
		update(token, id, "자료구조", "2026-2", List.of())
				.andExpect(status().isConflict());
	}

	@Test
	void 없는_과목_수정은_404() throws Exception {
		update(token, 99999, "운영체제", "2026-2", List.of()).andExpect(status().isNotFound());
	}

	// ---------- 삭제 ----------

	@Test
	void 삭제하면_목록에서_사라지고_키워드도_지워진다() throws Exception {
		long id = createdId(create(token, "운영체제", "2026-2", List.of("프로세스")));

		mockMvc.perform(delete("/api/courses/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success").value(true));

		mockMvc.perform(get("/api/courses").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data", hasSize(0)));
		assertThat(jdbcTemplate.queryForObject("select count(*) from course_keyword", Integer.class)).isZero();
	}

	// ---------- 도우미 ----------

	private ResultActions create(String token, String name, String semester, List<String> keywords) throws Exception {
		return mockMvc.perform(post("/api/courses")
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(name, semester, keywords)));
	}

	private ResultActions update(String token, long id, String name, String semester, List<String> keywords)
			throws Exception {
		return mockMvc.perform(patch("/api/courses/" + id)
				.header(HttpHeaders.AUTHORIZATION, bearer(token))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body(name, semester, keywords)));
	}

	private static long createdId(ResultActions result) throws Exception {
		String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private String body(String name, String semester, List<String> keywords) throws Exception {
		return objectMapper.writeValueAsString(Map.of("name", name, "semester", semester, "keywords", keywords));
	}

	private int courseCount() {
		return jdbcTemplate.queryForObject("select count(*) from course", Integer.class);
	}

	private static String bearer(String token) {
		return "Bearer " + token;
	}

	private String signupAndLogin(String email) throws Exception {
		mockMvc.perform(post("/api/auth/signup")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
						"email", email,
						"name", "테스터",
						"password", PASSWORD,
						"passwordConfirm", PASSWORD,
						"agreedTerms", true,
						"agreedPrivacy", true,
						"agreedAiAnalysis", false))))
				.andExpect(status().isCreated());
		String response = mockMvc.perform(post("/api/auth/login")
				.header("X-Requested-With", "XMLHttpRequest")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(response, "$.data.accessToken");
	}
}
