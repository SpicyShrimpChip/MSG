import type {
  Curriculum,
  CurriculumFlow,
  FlowAssessment,
  FlowConcept,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockGetCourses } from './courses';
import { mockDelay } from './delay';
import { mockMaterialsSnapshot } from './materials';

// 예시 사전 테스트 결과 (정보보호개론 예시 자료의 개념, 개념마다 3점 → 합계 7/12점). 실제 값은 서버가 제출·채점 기록으로 센다
const SAMPLE_PRE: Record<string, number> = {
  'public-key': 33,
  'digital-signature': 100,
  pki: 67,
  hash: 33,
};
// 예시 확인 퀴즈 결과 (사전 테스트 뒤에 푼 것): 해시 함수만 풀었다
const SAMPLE_PRACTICE: Record<string, [number, number]> = { hash: [2, 2] };

const NO_TEST: FlowAssessment = {
  status: 'none',
  quizId: null,
  score: null,
  maxScore: null,
  rate: null,
  pendingCount: 0,
  submittedAt: null,
};

// 서버 CurriculumFlowBuilder와 같은 규칙으로 예시 흐름을 만든다.
// 정보보호개론(예시 과목 1)은 사전 테스트를 본 뒤 확인 퀴즈를 푸는 중, 다른 과목은 처음 상태다
function buildFlow(courseId: number, keywords: string[]): CurriculumFlow {
  const materials = mockMaterialsSnapshot().filter(
    (material) => material.courseId === courseId,
  );
  const fromMaterials = materials.flatMap((material) =>
    material.concepts.map((concept) => ({ material, concept })),
  );
  const sample = courseId === 1 && fromMaterials.length > 0;
  const concepts: FlowConcept[] = (
    fromMaterials.length > 0
      ? fromMaterials.map(({ material, concept }) => ({
          conceptId: concept.conceptId,
          name: concept.name,
          materialId: material.id,
          materialTitle: material.title,
          hasStudyNote: concept.studyNote !== null,
        }))
      : keywords.map((keyword) => ({
          conceptId: keyword.toLowerCase(),
          name: keyword,
          materialId: materials[0]?.id ?? null,
          materialTitle: materials[0]?.title ?? null,
          hasStudyNote: false,
        }))
  ).map((base) => {
    const preRate = sample ? (SAMPLE_PRE[base.conceptId] ?? null) : null;
    const preStatus: FlowConcept['preStatus'] =
      preRate === null ? 'not_taken' : preRate < 60 ? 'weak' : 'ok';
    const [score, max] = (sample && SAMPLE_PRACTICE[base.conceptId]) || [0, 0];
    return {
      ...base,
      preRate,
      preStatus,
      practiceGradedCount: max,
      practiceScore: score,
      practiceMaxScore: max,
      practiceStatus:
        max === 0 ? 'not_started' : score / max < 0.6 ? 'weak' : 'ok',
      postRate: null,
      delta: null,
      postStatus: null,
      priority: sample && preStatus !== 'ok',
    };
  });

  const priority = concepts.filter((concept) => concept.priority);
  const targets = priority.length > 0 ? priority : concepts;
  const practiced = targets.filter((c) => c.practiceGradedCount > 0).length;
  const weakNames = priority.map((concept) => concept.name);
  const currentStep = !sample
    ? 'pre_test'
    : practiced === 0
      ? 'concept_study'
      : practiced < targets.length
        ? 'concept_quiz'
        : 'post_test';
  const order = ['pre_test', 'concept_study', 'concept_quiz', 'post_test'];
  const currentIndex = order.indexOf(currentStep);
  const status = (index: number) =>
    index < currentIndex
      ? 'done'
      : index === currentIndex
        ? 'current'
        : 'upcoming';
  const material = materials[0] ?? null;

  return {
    currentStep,
    stages: [
      {
        key: 'pre_test',
        order: 1,
        title: '사전 테스트',
        status: status(0),
        summary: sample
          ? `예시 데이터예요. 사전 테스트 7/12점. 기준(60%)보다 낮은 개념 ${weakNames.length}개.`
          : material
            ? '예시 데이터예요. 아직 사전 테스트를 보지 않았어요. 공부하기 전에 지금 이해 수준을 먼저 확인해요.'
            : '이 과목에 올린 자료가 없어요. 자료를 올리면 그 자료로 사전 테스트를 볼 수 있어요.',
      },
      {
        key: 'concept_study',
        order: 2,
        title: '핵심 개념 학습',
        status: status(1),
        summary:
          (sample
            ? `먼저 공부할 개념 ${weakNames.length}개: ${weakNames.join(', ')}.`
            : `개념 ${concepts.length}개를 자료로 공부해요.`) +
          ` 자세한 설명이 있는 개념 ${concepts.filter((c) => c.hasStudyNote).length}개.`,
      },
      {
        key: 'concept_quiz',
        order: 3,
        title: '개념별 확인 퀴즈',
        status: status(2),
        summary: `${sample ? '사전 테스트 뒤 ' : ''}확인 퀴즈를 푼 개념 ${practiced}/${targets.length}개.`,
      },
      {
        key: 'post_test',
        order: 4,
        title: '사후 평가',
        status: status(3),
        summary: sample
          ? '아직 사후 평가를 보지 않았어요. 사전 테스트와 같은 기준으로 만들어 비교해요.'
          : '사전 테스트를 본 뒤에 같은 개념·문항 수·난이도로 사후 평가를 봐요.',
      },
    ],
    conceptSource: fromMaterials.length > 0 ? 'material' : 'course',
    assessmentMaterial: material
      ? { id: material.id, title: material.title }
      : null,
    preTest: sample
      ? {
          status: 'submitted',
          quizId: 1,
          score: 7,
          maxScore: 12,
          rate: 58,
          pendingCount: 0,
          submittedAt: '2026-11-01T10:00:00',
        }
      : NO_TEST,
    concepts,
    postTest: NO_TEST,
    overallDelta: null,
  };
}

// 목업 학습 순서 (예시 데이터. 실제 순서는 서버가 퀴즈 결과·대화 분석·피드백·평가 기록으로 정한다)
export async function mockGetCurriculum(courseId: number): Promise<Curriculum> {
  const course = (await mockGetCourses()).find((item) => item.id === courseId);
  if (!course) throw new ApiError('과목을 찾을 수 없어요.', 404);
  return mockDelay({
    courseId,
    courseName: course.name,
    learningPurpose: 'undecided',
    recommendedMode: 'self_development',
    steps: [
      {
        order: 1,
        conceptId: 'hash',
        concept: '해시 함수',
        stage: 'review',
        reasons: [
          '예시 데이터예요. 퀴즈에서 채점이 끝난 2문제의 득점은 0/2점(0%)이에요.',
          '득점률이 보완 기준(60%)보다 낮아요.',
        ],
        evidence: {
          questionCount: 2,
          gradedCount: 2,
          correctCount: 0,
          score: 0,
          maxScore: 2,
          pendingCount: 0,
          reviewCount: 0,
          analysisStatus: null,
          analysisOutcome: null,
          feedback: null,
          confirmation: null,
        },
        actions: [
          {
            type: 'review_quiz',
            label: '복습 퀴즈 풀기',
            quizMode: 'self_development',
          },
        ],
      },
    ],
    materials: mockMaterialsSnapshot()
      .filter((material) => material.courseId === courseId)
      .map((material) => ({ id: material.id, title: material.title })),
    aiSuggestions: [],
    flow: buildFlow(courseId, course.keywords),
  });
}
