package com.msg.backend.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 서비스 코드(src/main)가 구조 규칙을 지키는지 검사한다. 스프링을 띄우지 않고 컴파일된 클래스만 읽는다.
 * 규칙 내용과 이유는 {@link ArchitectureRules}.
 */
@AnalyzeClasses(packages = "com.msg.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

	@ArchTest
	static final ArchRule modelCallsGoThroughModelInvoker = ArchitectureRules.MODEL_CALLS_GO_THROUGH_MODEL_INVOKER;

	@ArchTest
	static final ArchRule controllersDoNotReturnEntities = ArchitectureRules.CONTROLLERS_DO_NOT_RETURN_ENTITIES;
}
