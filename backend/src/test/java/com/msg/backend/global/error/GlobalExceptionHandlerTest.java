package com.msg.backend.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

// 전역 예외 처리가 상황별로 상태 코드와 사용자용 문구를 맞게 돌려주는지 확인한다
class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void 업무_오류는_던질_때_정한_상태와_문구로_돌려준다() throws Exception {
		mockMvc.perform(get("/test/business"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.message").value("이 학기에 같은 이름의 과목이 이미 있어요."))
				.andExpect(jsonPath("$.data").value(nullValue()));
	}

	@Test
	void 업무_오류에_목록을_담으면_data로_함께_돌려준다() throws Exception {
		mockMvc.perform(get("/test/business-with-data"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.success").value(false))
				.andExpect(jsonPath("$.data.rejected[0]").value("c-1"));
	}

	@Test
	void UNIQUE_제약_위반은_저장하려던_값을_빼고_409를_돌려준다() throws Exception {
		mockMvc.perform(get("/test/unique-violation"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(GlobalExceptionHandler.CONFLICT_MESSAGE))
				.andExpect(content().string(not(containsString("비밀 대화 본문"))));
		mockMvc.perform(get("/test/duplicate-key"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value(GlobalExceptionHandler.CONFLICT_MESSAGE));
	}

	// 다시 시도해도 같은 결과인 오류에 "잠시 뒤 다시 시도"를 보여 주지 않는다 (#215)
	@Test
	void 그_밖의_DB_제약_위반은_다시_시도를_권하지_않고_400을_돌려준다() throws Exception {
		mockMvc.perform(get("/test/data-error"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNSAVABLE_DATA_MESSAGE))
				.andExpect(content().string(not(containsString("비밀 대화 본문"))))
				.andExpect(content().string(not(containsString("0x00"))));
		mockMvc.perform(get("/test/data-integrity-without-state"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value(GlobalExceptionHandler.UNSAVABLE_DATA_MESSAGE));
	}

	@Test
	void 입력_검증_실패는_DTO의_검증_문구로_400을_돌려준다() throws Exception {
		mockMvc.perform(post("/test/validation")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("이름을 입력해 주세요."));
	}

	@Test
	void 읽을_수_없는_요청_본문은_400을_돌려준다() throws Exception {
		mockMvc.perform(post("/test/validation")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{잘못된 JSON"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("요청 형식이 올바르지 않아요."));
	}

	@Test
	void 예상하지_못한_오류는_내부_정보_없이_500을_돌려준다() throws Exception {
		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.message").value("서버에서 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요."))
				.andExpect(content().string(not(containsString("내부 비밀 정보"))));
	}

	@Test
	void 업로드_크기_한도를_넘으면_413을_돌려준다() throws Exception {
		mockMvc.perform(get("/test/too-large"))
				.andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.message").value("파일이 너무 커요. 올릴 수 있는 크기를 확인해 주세요."));
	}

	@Test
	void 파일_칸이_빠진_multipart_요청은_400을_돌려준다() throws Exception {
		mockMvc.perform(multipart("/test/upload").param("title", "제목"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("올릴 파일을 골라 주세요."));
	}

	@Test
	void 파일_올리기에_JSON을_보내면_415를_돌려준다() throws Exception {
		mockMvc.perform(post("/test/upload").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.message").value("지원하지 않는 요청 형식이에요."));
	}

	record TestRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
	}

	@RestController
	static class TestController {

		@GetMapping("/test/business")
		void business() {
			throw new BusinessException(HttpStatus.CONFLICT, "이 학기에 같은 이름의 과목이 이미 있어요.");
		}

		@GetMapping("/test/business-with-data")
		void businessWithData() {
			throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "저장하지 않았어요.",
					Map.of("rejected", List.of("c-1")));
		}

		// JPA가 감싼 모양처럼 원인 사슬 안쪽에 SQL 상태 코드가 있다
		@GetMapping("/test/unique-violation")
		void uniqueViolation() {
			throw new DataIntegrityViolationException("could not execute statement",
					new RuntimeException("wrapped",
							new SQLException("duplicate key: 비밀 대화 본문", "23505")));
		}

		@GetMapping("/test/duplicate-key")
		void duplicateKey() {
			throw new DuplicateKeyException("duplicate key");
		}

		// PostgreSQL이 NUL 문자를 거절할 때의 상태 코드(22021)
		@GetMapping("/test/data-error")
		void dataError() {
			throw new DataIntegrityViolationException("could not execute statement",
					new SQLException("invalid byte sequence for encoding \"UTF8\": 0x00 비밀 대화 본문", "22021"));
		}

		@GetMapping("/test/data-integrity-without-state")
		void dataIntegrityWithoutState() {
			throw new DataIntegrityViolationException("비밀 대화 본문");
		}

		@PostMapping("/test/validation")
		void validation(@Valid @RequestBody TestRequest request) {
		}

		@GetMapping("/test/too-large")
		void tooLarge() {
			throw new MaxUploadSizeExceededException(20L * 1024 * 1024);
		}

		@PostMapping(path = "/test/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
		void upload(@RequestPart MultipartFile file) {
		}

		@GetMapping("/test/unexpected")
		void unexpected() {
			throw new IllegalStateException("내부 비밀 정보");
		}
	}
}
