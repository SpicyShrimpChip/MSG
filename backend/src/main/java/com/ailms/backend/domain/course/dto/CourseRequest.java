package com.ailms.backend.domain.course.dto;

import com.ailms.backend.domain.course.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 과목 추가·수정 요청 (frontend/src/types/course.ts의 CourseInput과 같은 모양).
 * 공백 정리 뒤의 길이·키워드 중복은 CourseService에서 다시 검사한다.
 */
public record CourseRequest(
		@NotBlank(message = "과목 이름을 입력해 주세요.")
		String name,

		// frontend/src/utils/semester.ts의 학기 코드
		@NotBlank(message = "학기를 골라 주세요.")
		@Pattern(regexp = "^\\d{4}-(1|summer|2|winter)$", message = "학기를 다시 골라 주세요.")
		String semester,

		@NotNull(message = "키워드 목록이 빠졌어요.")
		@Size(max = Course.KEYWORD_MAX_COUNT, message = "키워드는 20개까지 넣을 수 있어요.")
		List<String> keywords) {
}
