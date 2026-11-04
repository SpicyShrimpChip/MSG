import type {
  JobField,
  LearningPurpose,
  QuestionType,
  QuizDifficulty,
  QuizMode,
} from '@/types';

// 학습 목적 (가입·마이페이지). 값은 백엔드 LearningPurpose와 같다
export const LEARNING_PURPOSES: {
  value: LearningPurpose;
  label: string;
  description: string;
}[] = [
  {
    value: 'exam_prep',
    label: '시험 대비',
    description: '시험 범위의 핵심 개념을 예상 문제로 복습해요',
  },
  {
    value: 'self_development',
    label: '자기개발',
    description:
      '개념을 제대로 이해했는지, 다음 단계로 갈 준비가 됐는지 확인해요',
  },
  {
    value: 'work',
    label: '업무 활용',
    description: '실제 업무 상황에서 지식을 어떻게 쓰는지 연습해요',
  },
  {
    value: 'undecided',
    label: '아직 정하지 않음',
    description: '퀴즈를 만들 때마다 골라도 돼요',
  },
];

// 관심 직무. 분야를 늘릴 때는 백엔드 JobField에도 같은 값을 넣는다
export const JOB_FIELDS: { value: JobField; label: string }[] = [
  { value: 'it_consulting', label: 'IT 컨설팅' },
  { value: 'network_security', label: '네트워크·보안' },
  { value: 'software_development', label: '소프트웨어 개발' },
  { value: 'data_analysis', label: '데이터 분석' },
  { value: 'system_cloud_operations', label: '시스템·클라우드 운영' },
  { value: 'other', label: '기타(직접 입력)' },
];

export const JOB_FIELD_OTHER_MAX = 30;

export const QUIZ_MODES: {
  value: QuizMode;
  label: string;
  description: string;
}[] = [
  {
    value: 'exam_prep',
    label: '시험 대비',
    description:
      '시험 범위의 핵심 개념으로 예상 문제를 만들어요. 출제 가능성은 추정이고 실제 출제를 보장하지 않아요.',
  },
  {
    value: 'self_development',
    label: '자기개발',
    description:
      '자신의 말로 설명하기·비슷한 개념 비교·새 상황에 적용하기로 이해를 확인하고, 다음에 공부할 개념을 제안해요.',
  },
  {
    value: 'work',
    label: '업무 활용',
    description:
      '관심 직무의 업무 상황을 주고 해결 방법을 설명하게 해요. 여러 해결 방법을 평가 기준별로 봐요.',
  },
];

export const QUESTION_TYPE_LABELS: Record<QuestionType, string> = {
  multiple_choice: '객관식',
  short_answer: '주관식',
  essay: '서술형',
};

// 문제 유형 고르기 (7가지 조합)
export const QUESTION_TYPE_SETS: { label: string; types: QuestionType[] }[] = [
  { label: '객관식만', types: ['multiple_choice'] },
  { label: '주관식만', types: ['short_answer'] },
  { label: '서술형만', types: ['essay'] },
  { label: '객관식 + 주관식', types: ['multiple_choice', 'short_answer'] },
  { label: '객관식 + 서술형', types: ['multiple_choice', 'essay'] },
  { label: '주관식 + 서술형', types: ['short_answer', 'essay'] },
  { label: '전체 혼합', types: ['multiple_choice', 'short_answer', 'essay'] },
];

export const DIFFICULTIES: { value: QuizDifficulty; label: string }[] = [
  { value: 'easy', label: '쉬움' },
  { value: 'medium', label: '보통' },
  { value: 'hard', label: '어려움' },
];

// 백엔드 app.quiz.max-question-count와 같다
export const QUIZ_MAX_QUESTIONS = 10;

export function quizModeLabel(mode: QuizMode | null): string | null {
  return QUIZ_MODES.find((item) => item.value === mode)?.label ?? null;
}

export function jobFieldLabel(
  jobField: string | null,
  other: string | null,
): string | null {
  if (!jobField) return null;
  if (jobField === 'other') return other ?? '기타';
  return JOB_FIELDS.find((item) => item.value === jobField)?.label ?? null;
}
