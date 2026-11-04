import type { CheckAttempt, StudyStatus } from '@/types';

// 개념 학습 상태 규칙. 실제 모드에서는 서버(ConceptStudyRules)가 정한 값을 그대로 보여 주고,
// 이 함수는 목업(체험 모드)이 같은 규칙으로 예시 상태를 만들 때만 쓴다. 기준은 서버 설정 기본값과 같다
export const STUDY_MIN_RATE = 0.6;
export const STUDY_MIN_GRADED = 2;

// 확인 퀴즈 결과 하나의 판정: 대기·확인 필요 답이 있거나 채점 문제가 적으면 추가 확인 필요(틀린 답으로 세지 않음)
export function judgeCheck(
  attempt: Pick<
    CheckAttempt,
    'gradedCount' | 'score' | 'maxScore' | 'waitingCount'
  >,
): CheckAttempt['result'] {
  if (
    attempt.waitingCount > 0 ||
    attempt.gradedCount < STUDY_MIN_GRADED ||
    attempt.maxScore === 0
  ) {
    return 'needs_check';
  }
  return attempt.score / attempt.maxScore >= STUDY_MIN_RATE
    ? 'understood'
    : 'review_needed';
}

// 학습 기록과 가장 최근 확인 퀴즈를 합친 상태. 확인 퀴즈가 마지막 학습 완료보다 나중이면 그 결과로 정한다
export function resolveStudyStatus(
  lastStudiedAt: string | null,
  latest: CheckAttempt | null,
): StudyStatus {
  if (latest && (!lastStudiedAt || latest.submittedAt >= lastStudiedAt)) {
    return latest.result;
  }
  return lastStudiedAt ? 'studied' : 'not_studied';
}

export const STUDY_STATUS_LABEL: Record<StudyStatus, string> = {
  not_studied: '학습 전',
  studied: '학습 완료',
  understood: '이해 확인',
  review_needed: '복습 필요',
  needs_check: '추가 확인 필요',
};

// 확인 퀴즈 만들기 화면 주소 (자료의 개념 하나만 다루는 확인 퀴즈)
export function checkQuizLink(
  courseId: number,
  materialId: number,
  conceptId: string,
  name: string,
): string {
  const params = new URLSearchParams({
    courseId: String(courseId),
    mode: 'self_development',
    checkMaterialId: String(materialId),
    checkConceptId: conceptId,
    concept: name,
  });
  return `/quizzes/new?${params.toString()}`;
}

// 자료 상세 "핵심 개념" 탭의 그 개념 (펼쳐서 보여 준다)
export function conceptStudyLink(materialId: number, conceptId: string) {
  return `/materials/${materialId}?tab=concepts&concept=${encodeURIComponent(conceptId)}`;
}
