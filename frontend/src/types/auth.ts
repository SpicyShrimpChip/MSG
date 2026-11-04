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
}

export interface LoginInput {
  email: string;
  password: string;
}

// Refresh Token은 응답 본문이 아니라 HttpOnly 쿠키로 온다
export interface LoginResult {
  accessToken: string;
  user: User;
}
