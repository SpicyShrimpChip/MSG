//! JS 문자열·정규식과 같은 뜻을 맞추는 작은 도우미.

/// JS `String.prototype.trim`과 `\s`가 공백으로 보는 문자 (WhiteSpace + LineTerminator).
/// 러스트의 `char::is_whitespace`와 달리 U+FEFF를 포함하고 U+0085를 빼야 JS와 같다.
pub fn is_js_space(c: char) -> bool {
    matches!(
        c,
        '\t' | '\n' | '\u{0B}' | '\u{0C}' | '\r' | ' ' | '\u{A0}' | '\u{1680}'
            | '\u{2000}'..='\u{200A}'
            | '\u{2028}' | '\u{2029}' | '\u{202F}' | '\u{205F}' | '\u{3000}' | '\u{FEFF}'
    )
}

/// 정규식 문자 집합 안에 넣는 JS `\s`
pub const JS_SPACE_CLASS: &str =
    r"\t\n\x0B\x0C\r \x{A0}\x{1680}\x{2000}-\x{200A}\x{2028}\x{2029}\x{202F}\x{205F}\x{3000}\x{FEFF}";

pub fn js_trim(text: &str) -> &str {
    text.trim_matches(is_js_space)
}

/// JS 문자열 길이 (UTF-16 단위)
pub fn utf16_len(text: &str) -> usize {
    text.chars().map(char::len_utf16).sum()
}
