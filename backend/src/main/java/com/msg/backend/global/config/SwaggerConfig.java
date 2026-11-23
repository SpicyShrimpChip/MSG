package com.msg.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui/index.html. 프론트와 API를 맞출 때 이 문서를 기준으로 한다.
 * 로그인이 필요한 API는 오른쪽 위 Authorize에 로그인 응답의 accessToken을 넣고 호출한다.
 */
@Configuration
public class SwaggerConfig {

	private static final String BEARER = "bearerAuth";

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("AI LMS API")
						.description("개인 맞춤형 AI LMS 백엔드 API. 모든 응답은 { success, message, data } 형식이다.")
						.version("v0"))
				.components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER));
	}
}
