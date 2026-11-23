// 백엔드 UserResponse와 같은 모양. 비밀번호·토큰은 들어오지 않는다
export interface User {
  id: number;
  email: string;
  name: string;
  aiAnalysisConsent: boolean;
  // 관리자 메뉴를 보일지 정하는 데만 쓴다. 권한은 서버가 매번 확인한다
  role: 'USER' | 'ADMIN';
  // AI 퀴즈 처리 동의(선택, 대화 분석 동의와 별도). 퀴즈 만들기·주관식·서술형 AI 채점에 필요
  aiQuizConsent: boolean;
  // 주된 학습 목적·관심 직무 (마이페이지에서 바꾼다)
  learningPurpose: LearningPurpose;
  jobField: JobField | null;
  jobFieldOther: string | null;
  // 비밀번호로 로그인할 수 있는지. 카카오로만 가입한 계정은 false (탈퇴 때 비밀번호 대신 "탈퇴"를 입력한다)
  hasPassword: boolean;
}

export type LearningPurpose =
  'exam_prep' | 'self_development' | 'work' | 'undecided';

// 목록은 constants/learning.ts(JOB_FIELDS)와 백엔드 JobField에 같이 둔다
export type JobField =
  | 'it_consulting'
  | 'network_security'
  | 'software_development'
  | 'data_analysis'
  | 'system_cloud_operations'
  | 'other';

// 학습 목적·관심 직무 바꾸기 (PATCH /api/users/me/profile)
export interface LearningProfileInput {
  learningPurpose: LearningPurpose;
  jobField: JobField | null;
  jobFieldOther: string | null;
}

// 회원가입 요청 (POST /api/auth/signup)
export interface SignupInput {
  email: string;
  name: string;
  password: string;
  passwordConfirm: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
  // 선택 동의
  agreedAiAnalysis: boolean;
  // 선택 동의 (빠지면 서버가 false)
  agreedAiQuiz?: boolean;
  // 선택. 빠지면 서버가 '아직 정하지 않음'으로 둔다
  learningPurpose?: LearningPurpose;
  jobField?: JobField | null;
  jobFieldOther?: string | null;
  // 자동 가입 방지 확인 토큰 (Turnstile을 켰을 때만)
  turnstileToken?: string;
}

// 카카오에서 처음 온 사용자의 가입 마무리 정보 (GET /api/auth/social/pending, 서버 DB에는 아직 없다)
export interface PendingSocialSignup {
  provider: 'kakao';
  // 카카오가 확인한 이메일. 가입하면 이 이메일로 계정이 생긴다
  email: string;
  // 카카오 닉네임(선택 동의). 이름 칸의 처음 값으로 쓴다
  nickname: string | null;
}

// 카카오 가입 마무리 (POST /api/auth/social/signup). 일반 가입에서 이메일·비밀번호·자동 가입 방지 확인만 뺐다
export interface SocialSignupInput {
  name: string;
  agreedTerms: boolean;
  agreedPrivacy: boolean;
  agreedAiAnalysis: boolean;
  agreedAiQuiz: boolean;
  learningPurpose?: LearningPurpose;
  jobField?: JobField | null;
  jobFieldOther?: string | null;
}

// 탈퇴 확인 (DELETE /api/users/me). 비밀번호가 있는 계정은 password, 없는 계정(카카오로만 가입)은 confirmText "탈퇴"
export type DeleteAccountInput = { password: string } | { confirmText: string };

export interface LoginInput {
  email: string;
  password: string;
}

// Refresh Token은 응답 본문이 아니라 HttpOnly 쿠키로 온다
export interface LoginResult {
  accessToken: string;
  user: User;
}
