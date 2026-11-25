package com.msg.backend.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.msg.archfixture.api.EntityLeakingController;
import com.msg.archfixture.api.SafeController;
import com.msg.archfixture.entity.FixtureAccount;
import com.msg.archfixture.llm.AllowedLlmClientUse;
import com.msg.archfixture.llm.DirectLlmCalls;
import com.msg.backend.global.llm.ModelInvoker;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 구조 규칙이 실제로 위반을 잡는지 확인한다("아무것도 안 잡는데 통과"를 막는다).
 * 위반·허용 예시는 com.msg.archfixture(서비스 패키지 밖, 테스트 소스)에 있어 본 규칙 검사와 스프링에는 들어가지 않는다.
 */
class ArchitectureRulesSelfTest {

	private static final ClassFileImporter IMPORTER = new ClassFileImporter();

	@Test
	void 모델을_직접_부르는_세_가지_방법을_모두_잡는다() {
		JavaClasses classes = IMPORTER.importClasses(DirectLlmCalls.class);
		ArchRule rule = ArchitectureRules.MODEL_CALLS_GO_THROUGH_MODEL_INVOKER;

		assertThatThrownBy(() -> rule.check(classes))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("ModelInvoker.invokeAsync(userId");
		List<String> details = rule.evaluate(classes).getFailureReport().getDetails();
		assertThat(details).anyMatch(line -> line.contains("viaInterface") && line.contains("LlmClient.complete"));
		assertThat(details).anyMatch(line -> line.contains("viaImplementation") && line.contains("OpenAiClient.complete"));
		assertThat(details).anyMatch(line -> line.contains("viaMethodReference"));
	}

	@Test
	void LlmClient의_상태만_읽는_코드와_ModelInvoker는_막지_않는다() {
		ArchRule rule = ArchitectureRules.MODEL_CALLS_GO_THROUGH_MODEL_INVOKER;

		// ModelInvoker는 실제로 client.complete를 부른다. 예외가 빠지면 이 검사가 깨진다
		JavaClasses classes = IMPORTER.importClasses(AllowedLlmClientUse.class, ModelInvoker.class);

		assertThat(rule.evaluate(classes).hasViolation()).isFalse();
	}

	@Test
	void 반환_타입의_어느_깊이에_있는_Entity도_잡는다() {
		JavaClasses classes = IMPORTER.importClasses(EntityLeakingController.class, FixtureAccount.class);
		ArchRule rule = ArchitectureRules.CONTROLLERS_DO_NOT_RETURN_ENTITIES;

		assertThatThrownBy(() -> rule.check(classes))
				.isInstanceOf(AssertionError.class)
				.hasMessageContaining("Entity를 직접 응답하지 않는다");
		EvaluationResult result = rule.evaluate(classes);
		List<String> details = result.getFailureReport().getDetails();
		for (String method : List.of("direct()", "wrapped()", "nested()", "wildcard()", "array()", "packagePrivate()")) {
			assertThat(details).as(method).anyMatch(line -> line.contains("EntityLeakingController." + method)
					&& line.contains("FixtureAccount"));
		}
	}

	@Test
	void DTO와_enum_응답과_private_도우미는_막지_않는다() {
		JavaClasses classes = IMPORTER.importClasses(SafeController.class, SafeController.AccountResponse.class);

		assertThat(ArchitectureRules.CONTROLLERS_DO_NOT_RETURN_ENTITIES.evaluate(classes).hasViolation()).isFalse();
	}
}
