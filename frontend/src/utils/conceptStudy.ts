import type { CheckAttempt, FlowConcept, StudyStatus } from '@/types';

// 개념 학습 상태 규칙. 실제 모드에서는 서버(ConceptStudyRules)가 정한 값을 그대로 보여 주고,
// 이 함수는 목업(체험 모드)이 같은 규칙으로 예시 상태를 만들 때만 쓴다. 기준은 서버 설정 기본값과 같다
const STUDY_MIN_RATE = 0.6;
const STUDY_MIN_GRADED = 2;

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

// 개념 하나로 퀴즈 만들기 화면 주소 (과목 키워드 개념, 기본은 자기개발 모드)
export function conceptQuizLink(
  courseId: number,
  concept: string,
  mode = 'self_development',
): string {
  const params = new URLSearchParams({
    courseId: String(courseId),
    mode,
    concept,
  });
  return `/quizzes/new?${params.toString()}`;
}

// 자료 상세 "핵심 개념" 탭의 그 개념 (펼쳐서 보여 준다)
export function conceptStudyLink(materialId: number, conceptId: string) {
  return `/materials/${materialId}?tab=concepts&concept=${encodeURIComponent(conceptId)}`;
}

// 과목 키워드 개념(학습 기록이 없는 개념) 중 채점된 퀴즈로 확인한 개념 수. 채점 대기만 있으면 확인으로 세지 않는다
export function keywordQuizProgress(
  concepts: Pick<FlowConcept, 'study' | 'practiceGradedCount'>[],
): { total: number; checked: number; percent: number | null } {
  const keywords = concepts.filter((c) => c.study === null);
  const total = keywords.length;
  const checked = keywords.filter((c) => c.practiceGradedCount > 0).length;
  return {
    total,
    checked,
    percent: total === 0 ? null : Math.round((checked * 100) / total),
  };
}
