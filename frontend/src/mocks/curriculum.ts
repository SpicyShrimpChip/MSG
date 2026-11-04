import type {
  ConceptStudy,
  Curriculum,
  CurriculumFlow,
  FlowAssessment,
  FlowConcept,
  StudyProgress,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockGetCourses } from './courses';
import { mockDelay } from './delay';
import { mockMaterialsSnapshot } from './materials';
import { mockStudyOf } from './study';

// 예시 사전 테스트 결과 (정보보호개론 예시 자료의 개념, 개념마다 3점 → 합계 7/12점). 실제 값은 서버가 제출·채점 기록으로 센다
const SAMPLE_PRE: Record<string, number> = {
  'public-key': 33,
  'digital-signature': 100,
  pki: 67,
  hash: 33,
};

const NO_TEST: FlowAssessment = {
  status: 'none',
  quizId: null,
  score: null,
  maxScore: null,
  rate: null,
  pendingCount: 0,
  submittedAt: null,
};

type Need = 'study' | 'quiz' | 'ok';

// 서버 CurriculumFlowBuilder.need와 같은 규칙
function need(concept: FlowConcept, practiceStarted: boolean): Need {
  if (concept.study) {
    if (['not_studied', 'review_needed'].includes(concept.study.status))
      return 'study';
    return concept.study.status === 'studied' ? 'quiz' : 'ok';
  }
  if (concept.practiceGradedCount > 0) return 'ok';
  return practiceStarted ? 'quiz' : 'study';
}

function progressOf(concepts: FlowConcept[]): StudyProgress {
  const studies = concepts
    .map((concept) => concept.study)
    .filter((study): study is ConceptStudy => study !== null);
  const studied = studies.filter((study) => study.studied).length;
  const last = studies
    .filter((study) => study.lastStudiedAt)
    .sort((a, b) =>
      (b.lastStudiedAt ?? '').localeCompare(a.lastStudiedAt ?? ''),
    )[0];
  const count = (status: ConceptStudy['status']) =>
    studies.filter((study) => study.status === status).length;
  return {
    totalConcepts: concepts.length,
    studiableConcepts: studies.length,
    studiedConcepts: studied,
    percent:
      studies.length === 0
        ? null
        : Math.round((studied * 1000) / studies.length) / 10,
    checkedConcepts: studies.filter((study) => study.latestCheck).length,
    understoodConcepts: count('understood'),
    reviewNeededConcepts: count('review_needed'),
    needsCheckConcepts: count('needs_check'),
    lastStudiedAt: last?.lastStudiedAt ?? null,
    lastStudiedConcept: last?.name ?? null,
  };
}

// 서버 CurriculumFlowBuilder와 같은 규칙으로 예시 흐름을 만든다. 학습 기록은 체험 모드 기록(mocks/study.ts)을 쓴다.
// 정보보호개론(예시 과목 1)은 사전 테스트를 본 상태, 다른 과목은 처음 상태다
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
          study: mockStudyOf(material, concept.conceptId, concept.name),
        }))
      : keywords.map((keyword) => ({
          conceptId: keyword.toLowerCase(),
          name: keyword,
          materialId: materials[0]?.id ?? null,
          materialTitle: materials[0]?.title ?? null,
          hasStudyNote: false,
          study: null,
        }))
  ).map((base) => {
    const preRate = sample ? (SAMPLE_PRE[base.conceptId] ?? null) : null;
    const preStatus: FlowConcept['preStatus'] =
      preRate === null ? 'not_taken' : preRate < 60 ? 'weak' : 'ok';
    const check = base.study?.latestCheck ?? null;
    const graded = check?.gradedCount ?? 0;
    return {
      ...base,
      preRate,
      preStatus,
      practiceGradedCount: graded,
      practiceScore: check?.score ?? 0,
      practiceMaxScore: check?.maxScore ?? 0,
      practiceStatus:
        graded === 0 ? 'not_started' : (check?.rate ?? 0) < 60 ? 'weak' : 'ok',
      postRate: null,
      delta: null,
      postStatus: null,
      priority: sample && preStatus !== 'ok',
    };
  });

  const priority = concepts.filter((concept) => concept.priority);
  const targets = priority.length > 0 ? priority : concepts;
  const practiceStarted = concepts.some((c) => c.practiceGradedCount > 0);
  const needs = targets.map((concept) => need(concept, practiceStarted));
  const remaining = needs.filter((item) => item !== 'ok').length;
  const progress = progressOf(concepts);
  const weakNames = priority.map((concept) => concept.name);
  const currentStep = !sample
    ? 'pre_test'
    : needs.includes('study')
      ? 'concept_study'
      : needs.includes('quiz')
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
  const studyHead =
    progress.studiableConcepts > 0
      ? `총 ${progress.studiableConcepts}개 개념 중 ${progress.studiedConcepts}개 학습 완료 (${progress.percent}%). `
      : '';

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
          studyHead +
          (sample
            ? `먼저 공부할 개념 ${weakNames.length}개: ${weakNames.join(', ')}.`
            : `개념 ${concepts.length}개를 자료로 공부해요.`) +
          (progress.reviewNeededConcepts > 0
            ? ` 복습이 필요한 개념 ${progress.reviewNeededConcepts}개.`
            : ''),
      },
      {
        key: 'concept_quiz',
        order: 3,
        title: '개념별 확인 퀴즈',
        status: status(2),
        summary:
          progress.studiableConcepts > 0
            ? `확인 퀴즈를 푼 개념 ${progress.checkedConcepts}/${progress.studiableConcepts}개. 이해 확인 ${progress.understoodConcepts}개 · 복습 필요 ${progress.reviewNeededConcepts}개 · 추가 확인 필요 ${progress.needsCheckConcepts}개.`
            : '확인 퀴즈를 푼 개념이 아직 없어요.',
      },
      {
        key: 'post_test',
        order: 4,
        title: '사후 평가',
        status: status(3),
        summary: sample
          ? '아직 사후 평가를 보지 않았어요. 사전 테스트와 같은 기준으로 만들어 비교해요.' +
            (remaining > 0
              ? ` 아직 학습하거나 확인하지 않은 개념이 ${remaining}개 있지만 사후 평가는 볼 수 있어요.`
              : '')
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
    progress,
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
