package com.ailms.backend.domain.course.api;

import com.ailms.backend.domain.course.application.CourseService;
import com.ailms.backend.domain.course.dto.CourseRequest;
import com.ailms.backend.domain.course.dto.CourseResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 과목만 다룬다. 남의 과목 id로 요청하면 404
@Tag(name = "내 과목")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

	private final CourseService courseService;

	@Operation(summary = "내 과목 목록", description = "등록한 순서대로 돌려준다. 화면이 학기·이름 순으로 다시 정렬한다.")
	@GetMapping
	public ApiResponse<List<CourseResponse>> getCourses(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.success(courseService.getCourses(authUser.userId()));
	}

	@Operation(summary = "과목 추가", description = "이름 1~40자, 키워드 20개·각 20자까지. 같은 학기에 같은 이름(대소문자 무시)이 있으면 409.")
	@PostMapping
	public ResponseEntity<ApiResponse<CourseResponse>> create(
			@AuthenticationPrincipal AuthUser authUser, @Valid @RequestBody CourseRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(courseService.create(authUser.userId(), request)));
	}

	@Operation(summary = "과목 수정", description = "이름·학기·키워드를 보낸 값으로 모두 바꾼다. 규칙은 추가와 같다.")
	@PatchMapping("/{id}")
	public ApiResponse<CourseResponse> update(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id,
			@Valid @RequestBody CourseRequest request) {
		return ApiResponse.success(courseService.update(authUser.userId(), id, request));
	}

	@Operation(summary = "과목 삭제", description = "이 과목에 올린 학습자료(원본 파일 포함)도 함께 지운다. 되돌릴 수 없다.")
	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
		courseService.delete(authUser.userId(), id);
		return ApiResponse.success(null);
	}
}
