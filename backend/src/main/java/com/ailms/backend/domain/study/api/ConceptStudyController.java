package com.ailms.backend.domain.study.api;

import com.ailms.backend.domain.study.application.ConceptStudyService;
import com.ailms.backend.domain.study.dto.ConceptStudyCompleteRequest;
import com.ailms.backend.domain.study.dto.ConceptStudyResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 본인 자료의 개념만. 남의 자료는 404
@Tag(name = "개념 학습 기록")
@RestController
@RequestMapping("/api/materials/{materialId}/concepts/progress")
@RequiredArgsConstructor
public class ConceptStudyController {

	private final ConceptStudyService studyService;

	@Operation(summary = "자료 개념별 학습 상태", description = "자료에서 뽑은 개념마다 학습 완료 기록과 확인 퀴즈 결과(최근·이력)를 따로 담고, "
			+ "둘을 합친 상태(not_studied·studied·understood·review_needed·needs_check)를 코드 규칙으로 준다. "
			+ "채점 대기·확인 필요 답은 틀린 답으로 세지 않는다. 숙련도 점수가 아니다.")
	@GetMapping
	public ApiResponse<List<ConceptStudyResponse>> list(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId) {
		return ApiResponse.success(studyService.listForMaterial(authUser.userId(), materialId));
	}

	@Operation(summary = "개념 학습 완료", description = "공부를 마쳤다는 기록을 남긴다(이해했다는 뜻이 아니고 퀴즈·분석 결과를 바꾸지 않는다). "
			+ "같은 개념을 여러 번 보내도 기록은 하나다. '복습 필요' 상태에서 다시 완료하면 최근 학습 시각과 횟수만 바뀐다. "
			+ "과목에 연결되지 않은 자료 400, 남의 자료·없는 개념 404.")
	@PostMapping
	public ApiResponse<ConceptStudyResponse> complete(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable Long materialId, @Valid @RequestBody ConceptStudyCompleteRequest request) {
		return ApiResponse.success(studyService.complete(authUser.userId(), materialId, request.conceptId()));
	}
}
