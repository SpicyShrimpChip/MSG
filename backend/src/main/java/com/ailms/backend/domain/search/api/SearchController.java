package com.ailms.backend.domain.search.api;

import com.ailms.backend.domain.search.application.SearchService;
import com.ailms.backend.domain.search.dto.SearchResponse;
import com.ailms.backend.global.common.ApiResponse;
import com.ailms.backend.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 로그인한 사용자 본인의 과목·자료·개념만 찾는다 (공지사항은 모두에게 공개된 글)
@Tag(name = "검색")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

	private final SearchService searchService;

	@Operation(summary = "통합 검색", description = "내 과목(이름·키워드), 내 자료(제목·파일 이름), 자료 핵심 개념(이름), 공지사항(제목)에서 "
			+ "검색어가 들어 있는 것을 찾는다. 대소문자·띄어쓰기는 무시한다. 과목 이름이 맞으면 그 과목에 올린 자료도 함께 준다"
			+ "(matchedBy=course). 자료 본문은 검색하지 않는다. 빈 검색어·50자 초과는 400.")
	@GetMapping
	public ApiResponse<SearchResponse> search(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(name = "q", required = false) String query) {
		return ApiResponse.success(searchService.search(authUser.userId(), query));
	}
}
