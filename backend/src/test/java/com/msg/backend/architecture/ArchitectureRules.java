package com.msg.backend.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.msg.backend.global.llm.LlmClient;
import com.msg.backend.global.llm.ModelInvoker;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaGenericArrayType;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * backend/CLAUDE.md에 글로 적힌 구조 규칙 중 어기면 돈·보안 사고가 되는 것을 코드로 검사한다.
 * 실제 코드 검사는 {@link ArchitectureTest}, 규칙이 실제로 위반을 잡는지는 {@link ArchitectureRulesSelfTest}가 확인한다.
 */
final class ArchitectureRules {

	private ArchitectureRules() {
	}

	/**
	 * 모델 호출(LlmClient.complete)은 ModelInvoker만 한다. LlmClient를 타입으로 들고 provider()·isConfigured() 등을 읽는 것은 괜찮다.
	 * 구현 클래스(OpenAiClient 등)로 부르는 것과 메서드 참조(LlmClient::complete)도 막는다.
	 * 예외는 LlmClient 구현의 complete 안에서 상위 구현의 complete를 부르는 것뿐이다(그 complete도 결국 ModelInvoker만 부른다).
	 */
	static final ArchRule MODEL_CALLS_GO_THROUGH_MODEL_INVOKER = noClasses()
			.that().doNotBelongToAnyOf(ModelInvoker.class)
			.should().accessTargetWhere(llmCompleteCall())
			.because("모델을 직접 부르면 ModelInvoker의 하루 AI 호출 상한(사용자 몫·서비스 전체, app.ai-daily-limit)과 "
					+ "다시 시도·시간 예산을 피해 가서 비용 상한이 깨진다. "
					+ "대신 ModelInvoker.invokeAsync(userId, client, request, lane, deadline)에 사용자 id를 넘겨 부른다 "
					+ "(backend/CLAUDE.md \"하루 AI 호출 상한\")");

	/**
	 * @RestController의 핸들러(public 또는 @GetMapping 등) 반환 타입에 Entity가 들어가면 안 된다.
	 * ApiResponse&lt;T&gt;·ResponseEntity&lt;ApiResponse&lt;List&lt;T&gt;&gt;&gt; 같은 제네릭 인자까지 재귀로 본다.
	 * Entity = ..entity.. 패키지의 클래스(enum 제외) 또는 @Entity·@Embeddable·@MappedSuperclass가 붙은 클래스.
	 * enum(QuizMode 등)은 칸이 없는 값이라 응답에 써도 새는 정보가 없어 제외한다.
	 */
	static final ArchRule CONTROLLERS_DO_NOT_RETURN_ENTITIES = methods()
			.that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
			.and(handlerMethods())
			.should(notExposeEntitiesInReturnType())
			.because("Entity를 그대로 응답하면 비밀번호 해시·내부 id 같은 칸이 응답에 섞이고, 연관 관계를 따라 다른 사용자 데이터까지 "
					+ "직렬화될 수 있다. dto 패키지의 응답 DTO로 바꿔서 돌려준다 (backend/CLAUDE.md API 규칙 "
					+ "\"Entity를 직접 응답하지 않는다\")");

	private static DescribedPredicate<JavaAccess<?>> llmCompleteCall() {
		return DescribedPredicate.describe("LlmClient(또는 그 구현).complete(...)를 직접 호출",
				access -> isLlmComplete(access.getTarget().getName(), access.getTarget().getOwner())
						// 구현 클래스의 complete가 상위 구현의 complete를 부르는 것은 같은 호출의 일부다.
						// (HttpLlmClient가 package-private이라 javac가 OpenAiClient 등에 super.complete를 부르는 다리 메서드를 만든다)
						&& !isLlmComplete(access.getOrigin().getName(), access.getOriginOwner()));
	}

	private static boolean isLlmComplete(String methodName, JavaClass owner) {
		return methodName.equals("complete") && owner.isAssignableTo(LlmClient.class);
	}

	private static DescribedPredicate<JavaMethod> handlerMethods() {
		return DescribedPredicate.describe("핸들러 메서드(public 또는 @RequestMapping 계열)",
				method -> !method.getModifiers().contains(JavaModifier.SYNTHETIC)
						&& (method.getModifiers().contains(JavaModifier.PUBLIC)
								|| method.isMetaAnnotatedWith(RequestMapping.class)));
	}

	private static ArchCondition<JavaMethod> notExposeEntitiesInReturnType() {
		return new ArchCondition<>("반환 타입(제네릭 인자 포함)에 Entity를 넣지 않는다") {
			@Override
			public void check(JavaMethod method, ConditionEvents events) {
				Set<JavaClass> found = new LinkedHashSet<>();
				collectEntities(method.getReturnType(), found, new HashSet<>());
				boolean violated = !found.isEmpty();
				String message = violated
						? method.getFullName() + "의 반환 타입 " + method.getReturnType().getName() + "에 Entity "
								+ found.stream().map(JavaClass::getSimpleName).toList() + "가 들어 있다 "
								+ method.getSourceCodeLocation()
						: method.getFullName() + "는 Entity를 반환하지 않는다";
				events.add(new SimpleConditionEvent(method, !violated, message));
			}
		};
	}

	// 매개변수화 타입·와일드카드·타입 변수·배열을 따라 들어가며 Entity 클래스를 모은다
	static void collectEntities(JavaType type, Set<JavaClass> found, Set<JavaType> visited) {
		if (!visited.add(type)) {
			return;
		}
		if (type instanceof JavaParameterizedType parameterized) {
			collectEntities(parameterized.toErasure(), found, visited);
			parameterized.getActualTypeArguments().forEach(argument -> collectEntities(argument, found, visited));
		} else if (type instanceof JavaWildcardType wildcard) {
			wildcard.getUpperBounds().forEach(bound -> collectEntities(bound, found, visited));
			wildcard.getLowerBounds().forEach(bound -> collectEntities(bound, found, visited));
		} else if (type instanceof JavaTypeVariable<?> variable) {
			variable.getUpperBounds().forEach(bound -> collectEntities(bound, found, visited));
		} else if (type instanceof JavaGenericArrayType array) {
			collectEntities(array.getComponentType(), found, visited);
		} else if (type instanceof JavaClass javaClass) {
			if (javaClass.isArray()) {
				collectEntities(javaClass.getComponentType(), found, visited);
			} else if (isEntity(javaClass)) {
				found.add(javaClass);
			}
		}
	}

	private static boolean isEntity(JavaClass javaClass) {
		boolean inEntityPackage = JavaClass.Predicates.resideInAPackage("..entity..").test(javaClass)
				&& !javaClass.isEnum();
		return inEntityPackage
				|| javaClass.isAnnotatedWith(Entity.class)
				|| javaClass.isAnnotatedWith(Embeddable.class)
				|| javaClass.isAnnotatedWith(MappedSuperclass.class);
	}
}
