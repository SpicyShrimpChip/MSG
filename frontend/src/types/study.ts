// 개념 학습 상태 (GET·POST /api/materials/{id}/concepts/progress). 백엔드 ConceptStudyResponse와 같은 모양.
// 학습 기록(학습 완료)과 확인 퀴즈 결과는 따로 담고, status만 둘을 합친 서버 코드 규칙 결과다(숙련도 점수 아님)
export type StudyStatus =
  | 'not_studied' // 학습 전
  | 'studied' // 학습 완료, 확인 퀴즈 전 (또는 복습 뒤 다시 풀 차례)
  | 'understood' // 확인 퀴즈가 기준 이상
  | 'review_needed' // 확인 퀴즈가 기준 미만 → 다시 공부
  | 'needs_check'; // 채점 대기·AI 판단 불일치·문항 부족

export interface CheckAttempt {
  quizId: number;
  submittedAt: string;
  // 채점이 끝난 문제 수
  gradedCount: number;
  score: number;
  maxScore: number;
  rate: number | null;
  // 채점 대기·확인 필요 답 수 (틀린 답으로 세지 않음)
  waitingCount: number;
  result: 'understood' | 'review_needed' | 'needs_check';
}

export interface ConceptStudy {
  materialId: number;
  courseId: number | null;
  conceptId: string;
  name: string;
  status: StudyStatus;
  // 학습 완료 기록이 있는지 (공부했다는 기록일 뿐 이해했다는 뜻은 아니다)
  studied: boolean;
  firstCompletedAt: string | null;
  lastStudiedAt: string | null;
  // 학습 완료 횟수 (복습 뒤 다시 완료하면 늘어난다)
  studyCount: number;
  latestCheck: CheckAttempt | null;
  // 제출한 확인 퀴즈 이력 (최신 순, 5개까지)
  checkHistory: CheckAttempt[];
  // 만들었지만 아직 제출하지 않은 가장 최근 확인 퀴즈
  openCheckQuizId: number | null;
  // 상태를 설명하는 문장 (코드가 센 사실)
  reason: string;
}
