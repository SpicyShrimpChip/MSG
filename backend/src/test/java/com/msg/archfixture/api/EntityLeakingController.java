package com.msg.archfixture.api;

import com.msg.archfixture.entity.FixtureAccount;
import com.msg.backend.global.common.ApiResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구조 규칙 자체 검사용 위반 예시. 반환 타입의 여러 깊이에 Entity가 들어 있다.
 * 서비스 패키지(com.msg.backend) 밖이라 스프링이 컨트롤러로 등록하지 않는다.
 */
@RestController
public class EntityLeakingController {

	@GetMapping("/fixture/direct")
	public FixtureAccount direct() {
		return null;
	}

	@GetMapping("/fixture/wrapped")
	public ApiResponse<FixtureAccount> wrapped() {
		return null;
	}

	@GetMapping("/fixture/nested")
	public ResponseEntity<ApiResponse<List<FixtureAccount>>> nested() {
		return null;
	}

	@GetMapping("/fixture/wildcard")
	public ApiResponse<List<? extends FixtureAccount>> wildcard() {
		return null;
	}

	@GetMapping("/fixture/array")
	public ApiResponse<FixtureAccount[]> array() {
		return null;
	}

	// public이 아니어도 @GetMapping이면 핸들러다
	@GetMapping("/fixture/package-private")
	ApiResponse<FixtureAccount> packagePrivate() {
		return null;
	}
}
