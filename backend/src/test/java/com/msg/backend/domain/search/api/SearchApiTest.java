package com.msg.backend.domain.search.api;

import static com.msg.backend.support.TestApi.bearer;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.msg.backend.domain.auth.dao.RefreshTokenRepository;
import com.msg.backend.domain.user.dao.UserRepository;
import com.msg.backend.support.IntegrationTest;
import com.msg.backend.support.TestApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 통합 검색: 내 과목·자료·자료 개념과 공지 제목에서 찾는다. 대소문자·띄어쓰기는 무시하고 남의 데이터는 나오지 않는다.
 */
@IntegrationTest
class SearchApiTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private RefreshTokenRepository refreshTokenRepository;

	private String token;

	@BeforeEach
	void setUp() throws Exception {
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
	}

	@Test
	void 과목_이름으로_찾으면_과목과_그_과목의_자료가_나온다() throws Exception {
		long course = createCourse(token, "정보보호개론", List.of("공개키", "해시"));
		createCourse(token, "운영체제", List.of("페이징"));
		upload(token, course, "암호 정리.txt");

		search(token, "정보보호개론")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.query").value("정보보호개론"))
				.andExpect(jsonPath("$.data.courses", hasSize(1)))
				.andExpect(jsonPath("$.data.courses[0].name").value("정보보호개론"))
				.andExpect(jsonPath("$.data.courses[0].nameMatched").value(true))
				.andExpect(jsonPath("$.data.courses[0].materialCount").value(1))
				.andExpect(jsonPath("$.data.materials", hasSize(1)))
				.andExpect(jsonPath("$.data.materials[0].title").value("암호 정리"))
				.andExpect(jsonPath("$.data.materials[0].courseName").value("정보보호개론"))
				.andExpect(jsonPath("$.data.materials[0].matchedBy").value("course"));
	}

	@Test
	void 띄어쓰기와_대소문자를_무시하고_키워드와_자료_제목도_찾는다() throws Exception {
		long course = createCourse(token, "정보보호개론", List.of("PKI", "해시"));
		upload(token, course, "PKI 인증서 노트.txt");

		search(token, "정보 보호").andExpect(jsonPath("$.data.courses", hasSize(1)));
		search(token, "pki")
				.andExpect(jsonPath("$.data.courses[0].nameMatched").value(false))
				.andExpect(jsonPath("$.data.courses[0].matchedKeywords[0]").value("PKI"))
				.andExpect(jsonPath("$.data.materials", hasSize(1)))
				.andExpect(jsonPath("$.data.materials[0].matchedBy").value("title"));
	}

	@Test
	void 남의_과목은_나오지_않고_없으면_빈_목록이다() throws Exception {
		String other = signupAndLogin("other@example.com");
		createCourse(other, "정보보호개론", List.of());

		search(token, "정보보호개론")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.courses", hasSize(0)))
				.andExpect(jsonPath("$.data.materials", hasSize(0)))
				.andExpect(jsonPath("$.data.concepts", hasSize(0)));
	}

	@Test
	void 빈_검색어는_400_토큰이_없으면_401() throws Exception {
		search(token, "  ").andExpect(status().isBadRequest());
		search(token, "가".repeat(51)).andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/search").param("q", "정보")).andExpect(status().isUnauthorized());
	}

	private ResultActions search(String token, String query) throws Exception {
		return mockMvc.perform(get("/api/search").param("q", query).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private void upload(String token, long course, String name) throws Exception {
		mockMvc.perform(multipart("/api/materials")
						.file(new MockMultipartFile("file", name, "text/plain", "노트".getBytes()))
						.param("title", name.substring(0, name.indexOf('.'))).param("courseId", String.valueOf(course))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated());
	}

	private long createCourse(String token, String name, List<String> keywords) throws Exception {
		return TestApi.createCourse(mockMvc, objectMapper, token, name, keywords);
	}

	private String signupAndLogin(String email) throws Exception {
		return TestApi.signupAndLogin(mockMvc, objectMapper, email, Map.of("agreedAiAnalysis", false));
	}
}
