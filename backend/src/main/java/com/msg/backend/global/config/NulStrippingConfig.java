package com.msg.backend.global.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.msg.backend.global.common.NulCharacters;
import java.beans.PropertyEditorSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * 들어오는 모든 글자 입력에서 NUL 문자를 저장 전에 지운다(#215, NulCharacters 참고).
 * DTO마다 지우면 새 입력 칸을 만들 때 빠뜨리기 쉬워서 입구 두 곳에서 한 번에 지운다.
 * - JSON 요청 본문: 앱 ObjectMapper에 String 역직렬화기를 바꿔 끼운다(스프링 부트가 Module 빈을 자동 등록).
 *   레코드·목록·배열 안의 글자도 같은 역직렬화기를 쓴다. 맵의 키는 지우지 않는다.
 * - 요청 파라미터(자료 올리기의 title 같은 multipart 글자 칸, 검색어, 경로 변수): 아래 @InitBinder.
 * 모델 답변은 JSON 요청 본문이 아니라 readTree로 읽어 여기를 거치지 않고, LlmResponse가 따로 지운다.
 */
@Configuration
public class NulStrippingConfig {

	@Bean
	public Module nulStrippingModule() {
		SimpleModule module = new SimpleModule("nul-stripping");
		module.addDeserializer(String.class, new NulStrippingStringDeserializer());
		return module;
	}

	@ControllerAdvice
	public static class RequestParameterAdvice {

		// PropertyEditor는 스레드 안전하지 않아 요청마다 새로 만든다
		@InitBinder
		public void stripNul(WebDataBinder binder) {
			binder.registerCustomEditor(String.class, new PropertyEditorSupport() {
				@Override
				public void setAsText(String text) {
					setValue(NulCharacters.strip(text));
				}
			});
		}
	}
}
