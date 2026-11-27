import type { JobField, LearningPurpose, SocialSignupInput } from '@/types';

// 소셜(카카오) 로그인 화면 도우미. 서버 계약은 docs/frontend-requests.md 5-11절

const HOME = '/';
const MAX_RETURN_TO_LENGTH = 200;

// 로그인 뒤 돌아갈 경로. 사이트 안의 경로(/로 시작)만 받고 아니면 첫 화면으로 바꾼다 (열린 리다이렉트 방지).
// 백엔드 ReturnTo.sanitize와 같은 규칙이다
export function safeReturnTo(raw: string | null | undefined): string {
  if (
    !raw ||
    raw.length > MAX_RETURN_TO_LENGTH ||
    !raw.startsWith('/') ||
    // //evil.com, /\evil.com 은 브라우저가 다른 사이트 주소로 읽는다
    raw[1] === '/' ||
    raw[1] === '\\'
  ) {
    return HOME;
  }
  for (const ch of raw) {
    const code = ch.charCodeAt(0);
    if (ch === '\\' || code < 0x20 || code === 0x7f) return HOME;
  }
  return raw;
}

// 카카오 로그인 시작 주소. axios가 아니라 브라우저 이동(window.location.assign)으로 연다
export function kakaoStartUrl(returnTo?: string): string {
  return `/api/auth/kakao/start?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}

// 카카오 로그인 실패 안내가 뜨는 로그인 화면 주소. 돌아갈 화면이 첫 화면이 아니면 같이 넘긴다 (서버 콜백의 실패 주소와 같은 모양)
export function socialLoginErrorPath(code: string, returnTo?: string): string {
  const path = `/login?socialError=${encodeURIComponent(code)}`;
  const target = safeReturnTo(returnTo);
  return target === HOME
    ? path
    : `${path}&returnTo=${encodeURIComponent(target)}`;
}

const GENERAL_ERROR =
  '카카오 로그인 중 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.';

const SOCIAL_ERROR_MESSAGES: Record<string, string | null> = {
  state: '로그인 확인 시간이 지났어요. 다시 시도해 주세요.',
  // 사용자가 동의 화면에서 취소한 것이므로 조용히 로그인 화면만 보여 준다
  cancelled: null,
  email_required: '카카오 계정의 이메일 제공에 동의해야 가입할 수 있어요.',
  email_unverified: '카카오 계정의 이메일 인증을 마친 뒤 다시 시도해 주세요.',
  rejoin_restricted: '탈퇴한 이메일은 30일 동안 다시 가입할 수 없어요.',
  link_conflict: '이 이메일에는 다른 카카오 계정이 연결되어 있어요.',
};

// 로그인 화면 주소의 ?socialError=코드 를 보여 줄 문구로 바꾼다. 보여 줄 게 없으면 null
export function socialErrorMessage(code: string | null): string | null {
  if (!code) return null;
  return Object.hasOwn(SOCIAL_ERROR_MESSAGES, code)
    ? SOCIAL_ERROR_MESSAGES[code]
    : GENERAL_ERROR;
}

const MAX_NAME_LENGTH = 30;

// 가입 마무리 화면의 이름 칸 처음 값: 카카오 닉네임(선택 동의). 없으면 빈 칸
export function initialSocialName(nickname: string | null): string {
  return (nickname ?? '').trim().slice(0, MAX_NAME_LENGTH);
}

interface SocialSignupForm {
  name: string;
  // 이용약관·개인정보 수집·이용 안내 확인 (일반 가입과 같은 체크 하나)
  acknowledged: boolean;
  aiConsent: boolean;
  aiQuizConsent: boolean;
  profile: {
    learningPurpose: LearningPurpose;
    jobField: JobField | null;
    jobFieldOther: string;
  };
}

// 가입 마무리 화면의 입력을 서버 요청 모양으로 바꾼다 (일반 가입 SignupPage와 같은 규칙)
export function buildSocialSignupInput(
  form: SocialSignupForm,
): SocialSignupInput {
  const { profile } = form;
  return {
    name: form.name.trim(),
    agreedTerms: form.acknowledged,
    agreedPrivacy: form.acknowledged,
    agreedAiAnalysis: form.aiConsent,
    agreedAiQuiz: form.aiQuizConsent,
    learningPurpose: profile.learningPurpose,
    jobField: profile.jobField,
    jobFieldOther:
      profile.jobField === 'other' ? profile.jobFieldOther.trim() : null,
  };
}
