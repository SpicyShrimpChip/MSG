package com.msg.backend.global.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.msg.backend.global.common.NulCharacters;
import java.io.IOException;

/**
 * JSON 요청 본문의 글자 값을 읽을 때 NUL 문자를 지운다(NulCharacters 참고).
 * 읽는 방법(숫자를 글자로 받기 등)은 Jackson 기본 StringDeserializer에 그대로 맡긴다.
 * JsonNode(readTree)로 읽는 곳은 이 역직렬화기를 거치지 않는다.
 */
class NulStrippingStringDeserializer extends StdScalarDeserializer<String> {

	NulStrippingStringDeserializer() {
		super(String.class);
	}

	@Override
	public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
		return NulCharacters.strip(StringDeserializer.instance.deserialize(parser, context));
	}

	@Override
	public LogicalType logicalType() {
		return LogicalType.Textual;
	}

	@Override
	public boolean isCachable() {
		return true;
	}

	@Override
	public Object getEmptyValue(DeserializationContext context) {
		return "";
	}
}
