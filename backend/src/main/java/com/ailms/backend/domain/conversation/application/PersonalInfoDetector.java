package com.ailms.backend.domain.conversation.application;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 프론트가 가렸어야 할 개인정보가 글에 그대로 남아 있는지 검사한다. 고치지 않고 찾기만 한다.
 * 규칙은 frontend/src/utils/conversation/mask.ts와 같다(바꾸면 두 곳을 함께 고친다).
 * 이름·사용자가 넣은 단어는 서버가 원래 단어를 모르므로 검사하지 않는다. 대체 표시([이메일] 등)는 통과한다.
 *
 * <p>JS의 \b와 Java의 \b는 한글 옆에서 다르게 동작해서, 단어 경계는 영문·숫자·_ 기준으로 직접 적었다.
 */
@Component
public class PersonalInfoDetector {

	private static final String WORD_START = "(?<![A-Za-z0-9_])";
	private static final String WORD_END = "(?![A-Za-z0-9_])";
	private static final String OCTET = "(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)";

	private record Rule(Pattern pattern, Predicate<String> validate) {

		Rule(String regex) {
			this(Pattern.compile(regex), matched -> true);
		}
	}

	private static final List<Rule> RULES = List.of(
			// OpenAI·Anthropic 키, GitHub 토큰, AWS 액세스 키, Google API 키, Slack 토큰, JWT, Bearer 토큰
			new Rule(WORD_START + "(?:sk-(?:ant-)?[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{30,}"
					+ "|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{35}|xox[baprs]-[A-Za-z0-9-]{10,}"
					+ "|eyJ[A-Za-z0-9_-]{8,}\\.eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,})" + WORD_END
					+ "|Bearer\\s+[A-Za-z0-9._~+/-]{20,}=*"),
			// 이메일. 프론트와 같은 것을 찾되, 앞부분을 글자 덩어리의 맨 앞에서만 시작하고 되돌아가지 않게(++) 했다.
			// 그냥 쓰면 '@' 없는 긴 영문·숫자 덩어리(코드, 붙여 넣은 키 등)에서 시작 위치마다 끝까지 읽어 시간이 제곱으로 는다
			new Rule("(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]++@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}"),
			// 주민등록번호 (월·일이 맞는 것만)
			new Rule(Pattern.compile("(?<!\\d)\\d{6}[-\\s]?[1-4]\\d{6}(?!\\d)"), PersonalInfoDetector::isValidRrn),
			// 카드번호 (Luhn 검사를 통과한 것만)
			new Rule(Pattern.compile("(?<!\\d)\\d(?:[ -]?\\d){12,18}(?!\\d)"), PersonalInfoDetector::passesLuhn),
			// 휴대폰(010 등)·지역번호·070, 국가번호 +82 형태
			new Rule("(?<![\\d-])(?:\\+82[-.\\s]?0?|0)(?:10|1[16789]|2|[3-6][1-5]|70)[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}"
					+ "(?![\\d-])"),
			// IPv4
			new Rule("(?<![\\d.])(?:" + OCTET + "\\.){3}" + OCTET + "(?![\\d.])"));

	public boolean containsPersonalInfo(String text) {
		if (text == null || text.isEmpty()) {
			return false;
		}
		for (Rule rule : RULES) {
			Matcher matcher = rule.pattern().matcher(text);
			while (matcher.find()) {
				if (rule.validate().test(matcher.group())) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isValidRrn(String matched) {
		String digits = matched.replaceAll("\\D", "");
		if (digits.length() != 13) {
			return false;
		}
		int month = Integer.parseInt(digits.substring(2, 4));
		int day = Integer.parseInt(digits.substring(4, 6));
		return month >= 1 && month <= 12 && day >= 1 && day <= 31;
	}

	private static boolean passesLuhn(String matched) {
		String digits = matched.replaceAll("\\D", "");
		if (digits.length() < 13 || digits.length() > 19) {
			return false;
		}
		int sum = 0;
		boolean doubled = false;
		for (int i = digits.length() - 1; i >= 0; i--) {
			int n = digits.charAt(i) - '0';
			if (doubled) {
				n *= 2;
				if (n > 9) {
					n -= 9;
				}
			}
			sum += n;
			doubled = !doubled;
		}
		return sum % 10 == 0;
	}
}
