package com.ailms.backend.domain.material.api;

import com.ailms.backend.domain.material.application.MaterialService;
import com.ailms.backend.domain.material.dto.MaterialDetailResponse;
import com.ailms.backend.domain.material.dto.MaterialSummaryResponse;
import com.ailms.backend.domain.material.dto.MaterialUploadResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// 로그인한 사용자 본인의 자료만 다룬다. 남의 자료 id로 요청하면 404. 올린 자료는 다른 사용자와 공유하지 않는다
@Tag(name = "학습자료")
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

	private final MaterialService materialService;

	@Operation(summary = "자료 올리기", description = "multipart/form-data. PDF·PPTX·TXT·MD, 20MB까지. "
			+ "확장자와 파일 내용이 함께 맞아야 한다. 성공하면 status는 uploaded(AI 분석 전)다.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ApiResponse<MaterialUploadResponse>> upload(
			@AuthenticationPrincipal AuthUser authUser,
			@Parameter(description = "연결할 내 과목 id. 비우면 과목 없이 올린다") @RequestParam(required = false) Long courseId,
			@Parameter(description = "자료 제목 1~60자") @RequestParam(required = false) String title,
			@RequestPart MultipartFile file) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(materialService.upload(authUser.userId(), courseId, title, file)));
	}

	@Operation(summary = "내 학습자료 목록", description = "최신 자료가 위로 온다. 요약·개념 본문은 싣지 않는다.")
	@GetMapping
	public ApiResponse<List<MaterialSummaryResponse>> getMaterials(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.success(materialService.getMaterials(authUser.userId()));
	}

	@Operation(summary = "자료 상세", description = "AI 분석 전이라 aiSummary는 null, concepts는 빈 목록이다.")
	@GetMapping("/{id}")
	public ApiResponse<MaterialDetailResponse> getMaterial(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long id) {
		return ApiResponse.success(materialService.getMaterial(authUser.userId(), id));
	}

	@Operation(summary = "자료 삭제", description = "자료 정보와 원본 파일을 함께 지운다. 되돌릴 수 없다.")
	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@AuthenticationPrincipal AuthUser authUser, @PathVariable Long id) {
		materialService.delete(authUser.userId(), id);
		return ApiResponse.success(null);
	}
}
