package com.ailms.backend.domain.course.dto;

import com.ailms.backend.domain.course.entity.Course;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

// 과목 한 개 (frontend/src/types/course.ts의 Course와 같은 모양)
public record CourseResponse(
		Long id,
		String name,
		@Schema(example = "2026-2") String semester,
		@Schema(description = "입력한 순서대로") List<String> keywords) {

	public static CourseResponse from(Course course) {
		return new CourseResponse(course.getId(), course.getName(), course.getSemester(),
				List.copyOf(course.getKeywords()));
	}
}
