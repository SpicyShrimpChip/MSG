package com.msg.backend.global.common;

/**
 * 눈에 보이지 않는 NUL 문자(U+0000)를 지운다.
 * PostgreSQL의 text·varchar 칸은 NUL을 받지 않아(invalid byte sequence for encoding "UTF8": 0x00)
 * 그대로 저장하면 요청 전체가 실패한다. 코드 실행 결과를 붙여 넣은 대화처럼 실제 입력에도 들어 있을 수 있다.
 * H2는 NUL을 그대로 저장해서 ./gradlew test로는 드러나지 않는다(docs/wiki/backend.md).
 * 줄바꿈·탭 같은 다른 글자는 내용이라 지우지 않는다.
 */
public final class NulCharacters {

	private NulCharacters() {
	}

	public static String strip(String value) {
		return value == null || value.indexOf('\0') < 0 ? value : value.replace("\0", "");
	}
}
