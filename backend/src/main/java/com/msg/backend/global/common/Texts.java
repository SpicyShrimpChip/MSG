package com.msg.backend.global.common;

/**
 * 글자 수 제한에 맞춰 자르는 도우미. 두 방식은 저장되는 값이 달라 섞어 쓰지 않는다.
 * <ul>
 * <li>{@link #cut}: 넘는 부분을 그대로 잘라 낸다(말줄임표 없음).</li>
 * <li>{@link #clip}: 넘으면 maxLength - 1자에 말줄임표(…)를 붙여 maxLength자로 맞춘다.</li>
 * </ul>
 * 둘 다 null은 null로 돌려주고, 앞뒤 공백은 건드리지 않는다.
 */
public final class Texts {

	private Texts() {
	}

	public static String cut(String text, int maxLength) {
		return text == null || text.length() <= maxLength ? text : text.substring(0, maxLength);
	}

	public static String clip(String text, int maxLength) {
		return text == null || text.length() <= maxLength ? text : text.substring(0, maxLength - 1) + "…";
	}
}
