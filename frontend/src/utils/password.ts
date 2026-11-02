export interface PasswordRule {
  id: string;
  label: string;
  test: (password: string) => boolean;
}

// 비밀번호 4가지 조건. 백엔드 PasswordPolicy와 같은 규칙이라 바꾸면 두 곳을 함께 고친다 (서버는 72바이트 상한도 검사한다)
export const PASSWORD_RULES: PasswordRule[] = [
  { id: 'length', label: '8자 이상', test: (pw) => pw.length >= 8 },
  { id: 'letter', label: '영문 포함', test: (pw) => /[A-Za-z]/.test(pw) },
  { id: 'digit', label: '숫자 포함', test: (pw) => /\d/.test(pw) },
  {
    id: 'special',
    label: '특수문자 포함',
    // 한글 같은 문자는 특수문자로 치지 않고, 키보드의 ASCII 기호만 인정한다
    test: (pw) => /[!-/:-@[-`{-~]/.test(pw),
  },
];

export interface PasswordRuleResult {
  id: string;
  label: string;
  met: boolean;
}

// 조건별 충족 여부 (입력하는 동안 화면에 그대로 보여 준다)
export function checkPassword(password: string): PasswordRuleResult[] {
  return PASSWORD_RULES.map(({ id, label, test }) => ({
    id,
    label,
    met: test(password),
  }));
}

export const isPasswordValid = (password: string) =>
  checkPassword(password).every((rule) => rule.met);
