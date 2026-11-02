package com.ailms.backend.domain.material.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ailms.backend.domain.auth.dao.RefreshTokenRepository;
import com.ailms.backend.domain.course.application.CourseService;
import com.ailms.backend.domain.material.application.MaterialService;
import com.ailms.backend.domain.quiz.dao.QuizRepository;
import com.ailms.backend.domain.quiz.entity.Quiz;
import com.ailms.backend.domain.user.dao.UserRepository;
import com.ailms.backend.global.storage.FileCleanupService;
import com.ailms.backend.global.storage.FileStorage;
import com.ailms.backend.global.storage.StorageProperties;
import com.ailms.backend.support.FaultyFileStorage;
import com.ailms.backend.support.IntegrationTest;
import com.ailms.backend.support.MutableClock;
import com.ailms.backend.support.TestFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 학습자료 올리기·조회·삭제와 과목 삭제 때 자료 정리. 파일은 TestStorage의 임시 폴더에만 저장하고,
 * DB는 H2 또는 ai_lms_test에서만 돈다(TestDatabaseGuard). 회원을 지우면 과목·자료 행도 함께 지워진다.
 */
@IntegrationTest
class MaterialApiTest {

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
	private FileStorage fileStorage;
	@Autowired
	private FaultyFileStorage faultyFileStorage;
	@Autowired
	private FileCleanupService fileCleanupService;
	@Autowired
	private MaterialService materialService;
	@Autowired
	private CourseService courseService;
	@Autowired
	private StorageProperties storageProperties;
	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private MutableClock clock;
	@Autowired
	private QuizRepository quizRepository;

	private String token;
	private String otherToken;
	private long courseId;

	@BeforeEach
	void setUp() throws Exception {
		faultyFileStorage.reset();
		jdbcTemplate.update("delete from file_deletion_task");
		refreshTokenRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
		token = signupAndLogin("me@example.com");
		otherToken = signupAndLogin("other@example.com");
		courseId = createCourse(token, "운영체제");
	}

	@AfterEach
	void tearDown() {
		faultyFileStorage.reset();
		clock.reset();
	}

	// ---------- 인증 ----------

	@Test
	void 토큰이_없으면_모두_401() throws Exception {
		mockMvc.perform(multipart("/api/materials").file(pdf("노트.pdf")).param("title", "노트"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/materials")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/materials/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(delete("/api/materials/1")).andExpect(status().isUnauthorized());
		assertThat(materialCount()).isZero();
	}

	// ---------- 올리기 ----------

	@Test
	void PDF를_올리면_201과_함께_정보와_원본을_저장한다() throws Exception {
		upload(token, courseId, "  공개키   정리 ", pdf("공개키_정리.pdf"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success").value(true))
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.courseId").value(courseId))
				.andExpect(jsonPath("$.data.title").value("공개키 정리"));

		Map<String, Object> row = jdbcTemplate.queryForMap("select * from material");
		Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, "me@example.com");
		assertThat(row.get("user_id")).isEqualTo(userId);
		assertThat(row.get("original_file_name")).isEqualTo("공개키_정리.pdf");
		assertThat(row.get("file_format")).isEqualTo("PDF");
		assertThat(row.get("content_type")).isEqualTo("application/pdf");
		assertThat(row.get("file_size")).isEqualTo((long) TestFiles.pdf().length);
		assertThat(row.get("status")).isEqualTo("UPLOADED");
		String key = (String) row.get("storage_key");
		assertThat(key).matches("materials/" + userId + "/[0-9a-f-]{36}\\.pdf");
		try (InputStream in = fileStorage.open(key)) {
			assertThat(in.readAllBytes()).isEqualTo(TestFiles.pdf());
		}
	}

	@Test
	void PPTX_TXT_MD도_올릴_수_있다() throws Exception {
		upload(token, courseId, "발표", file("발표.pptx", TestFiles.pptx())).andExpect(status().isCreated());
		upload(token, courseId, "필기", file("필기.txt", TestFiles.utf8("페이징 필기"))).andExpect(status().isCreated());
		upload(token, courseId, "정리", file("정리.md", TestFiles.utf8("# 스케줄링"))).andExpect(status().isCreated());

		assertThat(jdbcTemplate.queryForList("select file_format from material order by id", String.class))
				.containsExactly("PPTX", "TXT", "MD");
	}

	@Test
	void 과목_없이_올릴_수_있다() throws Exception {
		mockMvc.perform(multipart("/api/materials").file(pdf("노트.pdf")).param("title", "노트")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.courseId").value(nullValue()));
	}

	@Test
	void 남의_과목이나_없는_과목에는_올릴_수_없고_파일도_남지_않는다() throws Exception {
		long otherCourseId = createCourse(otherToken, "정보보호개론");
		long filesBefore = storedFileCount();

		upload(token, otherCourseId, "노트", pdf("노트.pdf"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("과목을 찾을 수 없어요."));
		upload(token, 99999L, "노트", pdf("노트.pdf")).andExpect(status().isNotFound());

		assertThat(materialCount()).isZero();
		assertThat(storedFileCount()).isEqualTo(filesBefore);
	}

	@Test
	void 제목이_비었거나_60자를_넘으면_400() throws Exception {
		upload(token, courseId, "   ", pdf("노트.pdf"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("자료 제목을 입력해 주세요."));
		upload(token, courseId, "가".repeat(61), pdf("노트.pdf"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("자료 제목은 60자까지 넣을 수 있어요."));
		mockMvc.perform(multipart("/api/materials").file(pdf("노트.pdf")).param("courseId", String.valueOf(courseId))
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isBadRequest());
		assertThat(materialCount()).isZero();
	}

	@Test
	void 형식이_틀리거나_내용이_확장자와_다르면_400이고_아무것도_남지_않는다() throws Exception {
		long filesBefore = storedFileCount();

		upload(token, courseId, "한글", file("과제.hwp", TestFiles.utf8("x")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("PDF, PPTX, TXT, Markdown 파일만 올릴 수 있어요."));
		upload(token, courseId, "가짜", file("가짜.pdf", TestFiles.utf8("PDF 아님")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("PDF 파일이 아니거나 손상된 파일이에요. 파일을 확인해 주세요."));
		upload(token, courseId, "워드", file("워드.pptx", TestFiles.docx()))
				.andExpect(status().isBadRequest());
		upload(token, courseId, "빈 파일", file("빈.txt", new byte[0]))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("빈 파일은 올릴 수 없어요."));

		assertThat(materialCount()).isZero();
		assertThat(storedFileCount()).isEqualTo(filesBefore);
	}

	@Test
	void 파일_칸이_없거나_multipart가_아니면_400_또는_415() throws Exception {
		mockMvc.perform(multipart("/api/materials").param("title", "노트")
						.header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("올릴 파일을 골라 주세요."));
		mockMvc.perform(post("/api/materials").header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"노트\"}"))
				.andExpect(status().isUnsupportedMediaType());
	}

	@Test
	void 파일이_20MB를_넘으면_413() throws Exception {
		byte[] big = new byte[20 * 1024 * 1024 + 1];
		upload(token, courseId, "큰 파일", file("큰.txt", big))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.message").value("파일은 20MB까지 올릴 수 있어요."));
		assertThat(materialCount()).isZero();
	}

	@Test
	void 파일_이름의_경로는_떼고_저장_위치는_서버가_정한다() throws Exception {
		upload(token, courseId, "노트", pdf("../../../etc/노트.pdf")).andExpect(status().isCreated());

		Map<String, Object> row = jdbcTemplate.queryForMap("select original_file_name, storage_key from material");
		assertThat(row.get("original_file_name")).isEqualTo("노트.pdf");
		assertThat((String) row.get("storage_key")).doesNotContain("..").doesNotContain("노트");
		assertThat(fileStorage.exists((String) row.get("storage_key"))).isTrue();
	}

	@Test
	void 저장소에_저장하지_못하면_500이고_DB에_남지_않는다() throws Exception {
		faultyFileStorage.failStore(true);

		upload(token, courseId, "노트", pdf("노트.pdf"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("파일을 저장하지 못했어요. 잠시 뒤 다시 시도해 주세요."));
		assertThat(materialCount()).isZero();
	}

	@Test
	void 파일을_저장한_뒤_DB_트랜잭션이_롤백되면_저장한_파일도_지운다() {
		long filesBefore = storedFileCount();
		Long userId = userId("me@example.com");
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			materialService.upload(userId, courseId, "노트", pdf("노트.pdf"));
			throw new IllegalStateException("DB 저장 뒤 실패를 흉내 낸다");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(materialCount()).isZero();
		assertThat(storedFileCount()).isEqualTo(filesBefore);
		assertThat(taskCount()).isZero();
	}

	@Test
	void 롤백된_업로드의_파일을_지우지_못하면_정리_작업으로_남겼다가_지운다() {
		long filesBefore = storedFileCount();
		Long userId = userId("me@example.com");
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);
		faultyFileStorage.failDelete(true);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			materialService.upload(userId, courseId, "노트", pdf("노트.pdf"));
			throw new IllegalStateException("DB 저장 뒤 실패를 흉내 낸다");
		})).isInstanceOf(IllegalStateException.class);
		assertThat(storedFileCount()).isEqualTo(filesBefore + 1);
		assertThat(taskCount()).isEqualTo(1);

		faultyFileStorage.failDelete(false);
		assertThat(fileCleanupService.retryPending()).isEqualTo(1);
		assertThat(storedFileCount()).isEqualTo(filesBefore);
		assertThat(taskCount()).isZero();
	}

	// ---------- 목록·상세 ----------

	@Test
	void 목록은_내_자료만_최신순으로_돌려준다() throws Exception {
		upload(token, courseId, "먼저 올린 자료", pdf("a.pdf")).andExpect(status().isCreated());
		clock.advance(Duration.ofMinutes(1));
		upload(token, courseId, "나중에 올린 자료", file("b.md", TestFiles.utf8("# b"))).andExpect(status().isCreated());
		upload(otherToken, null, "남의 자료", pdf("c.pdf")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/materials").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data", hasSize(2)))
				.andExpect(jsonPath("$.data[*].title", contains("나중에 올린 자료", "먼저 올린 자료")))
				.andExpect(jsonPath("$.data[0].courseId").value(courseId))
				.andExpect(jsonPath("$.data[0].fileName").value("b.md"))
				.andExpect(jsonPath("$.data[0].createdAt").isString())
				.andExpect(jsonPath("$.data[0].status").value("uploaded"))
				.andExpect(jsonPath("$.data[0].quizCount").value(0))
				.andExpect(jsonPath("$.data[0].aiSummary").doesNotExist())
				.andExpect(jsonPath("$.data[0].storageKey").doesNotExist());
	}

	@Test
	void 퀴즈_수는_분석_전_자료도_그_자료에_연결된_내_퀴즈만_센다() throws Exception {
		long noteId = uploadedId(upload(token, courseId, "퀴즈 있는 노트", pdf("a.pdf")));
		long emptyId = uploadedId(upload(token, courseId, "퀴즈 없는 노트", pdf("b.pdf")));
		long myUserId = userRepository.findByEmail("me@example.com").orElseThrow().getId();
		long otherUserId = userRepository.findByEmail("other@example.com").orElseThrow().getId();
		LocalDateTime now = LocalDateTime.now();
		quizRepository.save(Quiz.create(myUserId, courseId, noteId, "퀴즈 1", now));
		quizRepository.save(Quiz.create(myUserId, courseId, noteId, "퀴즈 2", now));
		// 자료 없이 만든 퀴즈와 남의 퀴즈는 세지 않는다 (남의 퀴즈가 내 자료 id를 가리키는 일은 없지만 소유자 조건도 확인)
		quizRepository.save(Quiz.create(myUserId, courseId, null, "과목 퀴즈", now));
		long otherCourseId = createCourse(otherToken, "남의 과목");
		quizRepository.save(Quiz.create(otherUserId, otherCourseId, noteId, "남의 퀴즈", now));

		mockMvc.perform(get("/api/materials").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data[?(@.id == " + noteId + ")].quizCount", contains(2)))
				.andExpect(jsonPath("$.data[?(@.id == " + emptyId + ")].quizCount", contains(0)))
				.andExpect(jsonPath("$.data[?(@.id == " + noteId + ")].status", contains("uploaded")));

		mockMvc.perform(get("/api/materials/" + noteId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.quizCount").value(2));
	}

	@Test
	void 상세는_AI_분석_전_기본값을_돌려주고_남의_자료는_404() throws Exception {
		long id = uploadedId(upload(token, courseId, "노트", pdf("노트.pdf")));

		mockMvc.perform(get("/api/materials/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id").value(id))
				.andExpect(jsonPath("$.data.title").value("노트"))
				.andExpect(jsonPath("$.data.fileName").value("노트.pdf"))
				.andExpect(jsonPath("$.data.status").value("uploaded"))
				.andExpect(jsonPath("$.data.quizCount").value(0))
				.andExpect(jsonPath("$.data.aiSummary").value(nullValue()))
				.andExpect(jsonPath("$.data.concepts", hasSize(0)));

		mockMvc.perform(get("/api/materials/" + id).header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("자료를 찾을 수 없어요."));
		mockMvc.perform(get("/api/materials/99999").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());
	}

	// ---------- 자료 삭제 ----------

	@Test
	void 자료를_지우면_행과_원본_파일이_함께_지워진다() throws Exception {
		long id = uploadedId(upload(token, courseId, "노트", pdf("노트.pdf")));
		String key = storageKey(id);

		deleteMaterial(token, id).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

		assertThat(materialCount()).isZero();
		assertThat(fileStorage.exists(key)).isFalse();
		assertThat(taskCount()).isZero();
		mockMvc.perform(get("/api/materials/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());
	}

	@Test
	void 남의_자료는_지울_수_없고_파일도_그대로다() throws Exception {
		long id = uploadedId(upload(otherToken, null, "남의 노트", pdf("노트.pdf")));
		String key = storageKey(id);

		deleteMaterial(token, id).andExpect(status().isNotFound());

		assertThat(materialCount()).isEqualTo(1);
		assertThat(fileStorage.exists(key)).isTrue();
	}

	@Test
	void 파일을_지우지_못해도_자료는_지워지고_정리_작업이_나중에_파일을_지운다() throws Exception {
		long id = uploadedId(upload(token, courseId, "노트", pdf("노트.pdf")));
		String key = storageKey(id);
		faultyFileStorage.failDelete(true);

		deleteMaterial(token, id).andExpect(status().isOk());

		assertThat(materialCount()).isZero();
		assertThat(fileStorage.exists(key)).isTrue();
		assertThat(jdbcTemplate.queryForObject(
				"select attempt_count from file_deletion_task where storage_key = ?", Integer.class, key)).isEqualTo(1);

		faultyFileStorage.failDelete(false);
		assertThat(fileCleanupService.retryPending()).isEqualTo(1);
		assertThat(fileStorage.exists(key)).isFalse();
		assertThat(taskCount()).isZero();
	}

	// ---------- 과목 삭제 ----------

	@Test
	void 과목을_지우면_그_과목의_자료와_파일만_함께_지워진다() throws Exception {
		long otherCourseId = createCourse(token, "자료구조");
		long first = uploadedId(upload(token, courseId, "노트 1", pdf("1.pdf")));
		long second = uploadedId(upload(token, courseId, "노트 2", file("2.pptx", TestFiles.pptx())));
		long kept = uploadedId(upload(token, otherCourseId, "다른 과목 노트", pdf("3.pdf")));
		long unlinked = uploadedId(upload(token, null, "과목 없는 노트", pdf("4.pdf")));
		long othersOwn = uploadedId(upload(otherToken, null, "남의 노트", pdf("5.pdf")));
		List<String> removedKeys = List.of(storageKey(first), storageKey(second));
		List<String> keptKeys = List.of(storageKey(kept), storageKey(unlinked), storageKey(othersOwn));

		mockMvc.perform(delete("/api/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());

		assertThat(jdbcTemplate.queryForList("select id from material order by id", Long.class))
				.containsExactly(kept, unlinked, othersOwn);
		removedKeys.forEach(key -> assertThat(fileStorage.exists(key)).isFalse());
		keptKeys.forEach(key -> assertThat(fileStorage.exists(key)).isTrue());
		assertThat(taskCount()).isZero();
		mockMvc.perform(get("/api/materials").header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(jsonPath("$.data[*].title", contains("과목 없는 노트", "다른 과목 노트")));
	}

	@Test
	void 남의_과목은_지울_수_없고_그_자료도_그대로다() throws Exception {
		long otherCourseId = createCourse(otherToken, "정보보호개론");
		long id = uploadedId(upload(otherToken, otherCourseId, "남의 노트", pdf("노트.pdf")));
		String key = storageKey(id);

		mockMvc.perform(delete("/api/courses/" + otherCourseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isNotFound());

		assertThat(materialCount()).isEqualTo(1);
		assertThat(fileStorage.exists(key)).isTrue();
	}

	@Test
	void 과목_삭제가_롤백되면_자료_행도_파일도_그대로다() throws Exception {
		long id = uploadedId(upload(token, courseId, "노트", pdf("노트.pdf")));
		String key = storageKey(id);
		Long userId = userId("me@example.com");
		TransactionTemplate transaction = new TransactionTemplate(transactionManager);

		assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
			courseService.delete(userId, courseId);
			throw new IllegalStateException("과목 삭제 중 실패를 흉내 낸다");
		})).isInstanceOf(IllegalStateException.class);

		assertThat(jdbcTemplate.queryForObject("select count(*) from course", Integer.class)).isEqualTo(1);
		assertThat(materialCount()).isEqualTo(1);
		assertThat(fileStorage.exists(key)).isTrue();
		assertThat(taskCount()).isZero();
	}

	@Test
	void 과목_삭제_뒤_파일을_지우지_못하면_정리_작업이_나중에_지운다() throws Exception {
		long id = uploadedId(upload(token, courseId, "노트", pdf("노트.pdf")));
		String key = storageKey(id);
		faultyFileStorage.failDelete(true);

		mockMvc.perform(delete("/api/courses/" + courseId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
				.andExpect(status().isOk());
		assertThat(materialCount()).isZero();
		assertThat(fileStorage.exists(key)).isTrue();
		assertThat(taskCount()).isEqualTo(1);

		faultyFileStorage.failDelete(false);
		fileCleanupService.retryPending();
		assertThat(fileStorage.exists(key)).isFalse();
		assertThat(taskCount()).isZero();
	}

	// ---------- 도우미 ----------

	private ResultActions upload(String token, Long courseId, String title, MockMultipartFile file) throws Exception {
		MockMultipartHttpServletRequestBuilder request = multipart("/api/materials").file(file);
		if (courseId != null) {
			request.param("courseId", String.valueOf(courseId));
		}
		return mockMvc.perform(request.param("title", title).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private ResultActions deleteMaterial(String token, long id) throws Exception {
		return mockMvc.perform(delete("/api/materials/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)));
	}

	private static MockMultipartFile pdf(String name) {
		return file(name, TestFiles.pdf());
	}

	// 브라우저가 보내는 형식(Content-Type)은 믿지 않으므로 일부러 일반 값으로 보낸다
	private static MockMultipartFile file(String name, byte[] content) {
		return new MockMultipartFile("file", name, MediaType.APPLICATION_OCTET_STREAM_VALUE, content);
	}

	private static long uploadedId(ResultActions result) throws Exception {
		String response = result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private long createCourse(String token, String name) throws Exception {
		String response = mockMvc.perform(post("/api/courses")
						.header(HttpHeaders.AUTHORIZATION, bearer(token))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(
								Map.of("name", name, "semester", "2026-2", "keywords", List.of()))))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return ((Number) JsonPath.read(response, "$.data.id")).longValue();
	}

	private String storageKey(long materialId) {
		return jdbcTemplate.queryForObject("select storage_key from material where id = ?", String.class, materialId);
	}

	private Long userId(String email) {
		return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
	}

	private int materialCount() {
		return jdbcTemplate.queryForObject("select count(*) from material", Integer.class);
	}

	private int taskCount() {
		return jdbcTemplate.queryForObject("select count(*) from file_deletion_task", Integer.class);
	}

	// 테스트 저장소(임시 폴더)에 있는 파일 수
	private long storedFileCount() {
		Path root = storageProperties.local().root();
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(Files::isRegularFile).count();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
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
