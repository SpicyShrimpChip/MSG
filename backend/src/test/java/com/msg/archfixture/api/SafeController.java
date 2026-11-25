package com.msg.archfixture.api;

import com.msg.archfixture.entity.FixtureAccount;
import com.msg.archfixture.entity.FixtureStatus;
import com.msg.backend.global.common.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구조 규칙 자체 검사용 허용 예시. DTO·enum 응답, Entity를 받기만 하는 private 도우미는 막지 않아야 한다.
 */
@RestController
public class SafeController {

	public record AccountResponse(Long id) {
	}

	@GetMapping("/fixture/safe")
	public ResponseEntity<ApiResponse<List<AccountResponse>>> list() {
		return ResponseEntity.ok(ApiResponse.success(List.of(toResponse(load()))));
	}

	@GetMapping("/fixture/statuses")
	public ApiResponse<List<FixtureStatus>> statuses() {
		return ApiResponse.success(List.of(FixtureStatus.values()));
	}

	@GetMapping("/fixture/void")
	public ApiResponse<Void> nothing() {
		return ApiResponse.success(null);
	}

	private FixtureAccount load() {
		return new FixtureAccount();
	}

	private AccountResponse toResponse(FixtureAccount account) {
		return new AccountResponse(1L);
	}
}
