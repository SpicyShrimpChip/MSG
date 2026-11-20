import type {
  AssessmentComparison,
  AssessmentCreateInput,
  Quiz,
  QuizAnswer,
  QuizCreateInput,
  QuizResult,
  QuizResultItem,
  QuizSummary,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';
import { mockMaterialsSnapshot } from './materials';

// 목업 전용. 실제 서비스에서는 서버가 AI로 퀴즈를 만들고, 채점도 서버 코드가 한다 (프론트는 채점하지 않음).
// 문제는 샘플 자료(mocks/materials.ts)를 바탕으로 직접 쓴 예시이고 시험 문제 원문이 아니다.
// 목업은 AI를 부르지 않는다: 만들기는 예시 문제를 돌려주고(generatedBy: null), 서술형·일치하지 않는 주관식은 "채점 대기"로 둔다
interface MockQuestion {
  id: number;
  type?: 'multiple_choice' | 'short_answer' | 'essay';
  text: string;
  choices: string[];
  answerIndex: number;
  acceptedAnswers?: string[];
  rubric?: { criterion: string; points: number }[];
  conceptIds: string[];
  // AI가 쓴 해설이라고 가정한 글
  aiExplanation: string;
}

interface MockQuiz extends Omit<
  Quiz,
  | 'questions'
  | 'mode'
  | 'difficulty'
  | 'jobField'
  | 'jobFieldOther'
  | 'examScope'
  | 'generatedBy'
  | 'materialContentUsed'
  | 'mockExam'
  | 'assessmentKind'
  | 'preAssessmentId'
  | 'materialContentNote'
  | 'topicSource'
  | 'checkTopic'
> {
  mode?: Quiz['mode'];
  checkTopic?: Quiz['checkTopic'];
  questions: MockQuestion[];
}

// 개념 id·이름은 자료 목업(mocks/materials.ts)과 같게 맞춘다
const CONCEPT_NAMES: Record<string, string> = {
  'public-key': '공개키 암호',
  'digital-signature': '전자서명',
  pki: '인증서(PKI)',
  hash: '해시 함수',
  paging: '페이징',
};

const QUIZZES: MockQuiz[] = [
  {
    id: 1,
    title: '공개키 암호와 전자서명',
    courseId: 1,
    materialId: 1,
    questions: [
      {
        id: 11,
        text: '친구에게 비밀 메시지를 보내려고 해요. 어떤 키로 암호화해야 친구만 열 수 있을까요?',
        choices: ['내 개인키', '내 공개키', '친구의 공개키', '친구의 개인키'],
        answerIndex: 2,
        conceptIds: ['public-key'],
        aiExplanation:
          '받는 사람의 공개키로 잠그면, 그 짝인 개인키를 가진 친구만 열 수 있어요. 친구의 개인키는 나에게 없어요.',
      },
      {
        id: 12,
        text: '전자서명을 만들 때 쓰는 키는 무엇일까요?',
        choices: [
          '보내는 사람의 개인키',
          '보내는 사람의 공개키',
          '받는 사람의 공개키',
          '인증 기관의 공개키',
        ],
        answerIndex: 0,
        conceptIds: ['digital-signature', 'public-key'],
        aiExplanation:
          '서명은 나만 만들 수 있어야 하니 내 개인키로 만들고, 다른 사람은 내 공개키로 검증해요. 암호화와 쓰는 방향이 반대예요.',
      },
      {
        id: 13,
        text: '받은 공개키가 정말 그 사람의 것인지 확인할 때 쓰는 것은 무엇일까요?',
        choices: ['솔트', '인증서', '세션 키', '해시값'],
        answerIndex: 1,
        conceptIds: ['pki'],
        aiExplanation:
          '인증 기관이 "이 공개키는 이 사람 것"이라고 서명한 인증서로 공개키의 주인을 확인해요.',
      },
    ],
  },
  {
    id: 2,
    title: '해시 함수의 성질',
    courseId: 1,
    materialId: 2,
    questions: [
      {
        id: 21,
        text: '비밀번호를 해시로 저장할 때 솔트를 붙이는 가장 큰 이유는 무엇일까요?',
        choices: [
          '해시 계산을 빠르게 하려고',
          '같은 비밀번호도 서로 다른 값으로 저장하려고',
          '해시값에서 비밀번호를 되돌리려고',
          '저장 공간을 줄이려고',
        ],
        answerIndex: 1,
        conceptIds: ['hash'],
        aiExplanation:
          '솔트가 다르면 같은 비밀번호도 다른 해시값이 돼서, 미리 계산해 둔 표로 한꺼번에 알아내기 어려워져요.',
      },
      {
        id: 22,
        text: '서로 다른 두 입력이 같은 해시값을 갖는 쌍을 찾기 어려워야 한다는 성질은 무엇일까요?',
        choices: ['일방향성', '충돌 저항성', '가변 길이 출력', '가역성'],
        answerIndex: 1,
        conceptIds: ['hash'],
        aiExplanation:
          '같은 값이 나오는 두 입력(충돌)을 찾기 어려운 성질이 충돌 저항성이에요. 일방향성은 값에서 입력을 되돌리기 어려운 성질이에요.',
      },
    ],
  },
  {
    id: 3,
    title: '페이징 기초',
    courseId: 2,
    materialId: 3,
    questions: [
      {
        id: 31,
        text: '페이지 번호를 실제 메모리의 프레임 번호로 바꿔 주는 것은 무엇일까요?',
        choices: ['페이지 테이블', '스케줄러', '캐시', '세마포어'],
        answerIndex: 0,
        conceptIds: ['paging'],
        aiExplanation:
          '페이지 테이블에 페이지 번호마다 어느 프레임에 들어 있는지 적혀 있어요.',
      },
      {
        id: 32,
        text: '가장 오랫동안 쓰이지 않은 페이지를 내보내는 교체 알고리즘은 무엇일까요?',
        choices: ['FIFO', 'LRU', '라운드 로빈', 'SJF'],
        answerIndex: 1,
        conceptIds: ['paging'],
        aiExplanation:
          'LRU(Least Recently Used)는 최근에 가장 오래 안 쓴 페이지를 내보내요. FIFO는 먼저 들어온 페이지를 내보내고, 라운드 로빈·SJF는 CPU 스케줄링 방식이에요.',
      },
    ],
  },
];

// 제출 결과는 새로고침해도 남도록 브라우저에 저장한다 (퀴즈 id → 가장 최근 결과)
const STORAGE_KEY = 'ai-lms-mock-quiz-results-v2';

function loadResults(): Record<number, QuizResult> {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved ? (JSON.parse(saved) as Record<number, QuizResult>) : {};
  } catch {
    return {};
  }
}

let resultCache: Record<number, QuizResult> | null = null;
const getResults = () => (resultCache ??= loadResults());
const setResult = (quizId: number, result: QuizResult) => {
  resultCache = { ...getResults(), [quizId]: result };
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(resultCache));
  } catch {
    // 저장소를 쓸 수 없으면 이번 화면에서만 유지한다
  }
};

// 체험 중 만든 퀴즈도 새로고침해도 남도록 브라우저에 저장한다 (최근 것이 앞).
// 체험을 시작·끝낼 때 결과와 같이 지워진다(store/useDemoStore.ts의 `ai-lms-mock-*`)
const CREATED_KEY = 'ai-lms-mock-created-quizzes';
// 저장소를 쓸 수 없을 때만 쓰는 이번 화면 목록
let createdFallback: MockQuiz[] = [];

function loadCreated(): MockQuiz[] {
  try {
    const saved = localStorage.getItem(CREATED_KEY);
    return saved ? (JSON.parse(saved) as MockQuiz[]) : [];
  } catch {
    return createdFallback;
  }
}

function saveCreated(list: MockQuiz[]) {
  createdFallback = list;
  try {
    localStorage.setItem(CREATED_KEY, JSON.stringify(list));
  } catch {
    // 저장소를 쓸 수 없으면 이번 화면에서만 유지한다
  }
}

// 만든 퀴즈 + 예시 퀴즈. 저장소를 매번 읽어서 체험을 새로 시작해 지운 뒤에도 옛 목록이 남지 않는다
const allQuizzes = (): MockQuiz[] => [...loadCreated(), ...QUIZZES];

// 새 퀴즈 id는 지금 있는 가장 큰 id 다음 (100부터). 새로고침 뒤에도 앞서 만든 퀴즈의 결과를 이어받지 않는다
export function nextCreatedQuizId(existing: { id: number }[]): number {
  return Math.max(99, ...existing.map((quiz) => quiz.id)) + 1;
}

function findQuiz(id: number): MockQuiz {
  const quiz = allQuizzes().find((item) => item.id === id);
  if (!quiz) throw new ApiError('퀴즈를 찾을 수 없어요.', 404);
  return quiz;
}

const typeOf = (question: MockQuestion) => question.type ?? 'multiple_choice';
const maxScoreOf = (question: MockQuestion) =>
  question.rubric?.reduce((sum, item) => sum + item.points, 0) ?? 1;

export function mockGetQuizzes(): Promise<QuizSummary[]> {
  const results = getResults();
  return mockDelay(
    allQuizzes().map(
      ({ id, title, courseId, materialId, questions, mode }) => ({
        id,
        title,
        courseId,
        materialId,
        questionCount: questions.length,
        lastCorrectCount: results[id]?.correctCount ?? null,
        mode: mode ?? null,
        difficulty: null,
        aiGenerated: false,
        lastScore: results[id]?.score ?? null,
        lastMaxScore: results[id]?.maxScore ?? null,
        lastPendingCount: results[id]?.pendingCount ?? null,
        lastReviewCount: results[id]?.reviewCount ?? null,
        mockExam: false,
        assessmentKind: null,
      }),
    ),
  );
}

// 풀기 화면에는 정답·해설을 보내지 않는다
export function mockGetQuiz(id: number): Promise<Quiz> {
  try {
    const { questions, ...quiz } = findQuiz(id);
    return mockDelay({
      ...quiz,
      mode: quiz.mode ?? null,
      difficulty: null,
      jobField: null,
      jobFieldOther: null,
      examScope: null,
      generatedBy: null,
      materialContentUsed: false,
      mockExam: false,
      assessmentKind: null,
      preAssessmentId: null,
      materialContentNote: null,
      topicSource: null,
      checkTopic: quiz.checkTopic ?? null,
      questions: questions.map((question) => ({
        id: question.id,
        type: typeOf(question),
        text: question.text,
        choices: question.choices,
        scenario: null,
        role: null,
        guide: null,
        criteria: [],
        maxScore: maxScoreOf(question),
      })),
    });
  } catch (error) {
    return Promise.reject(error);
  }
}

const normalize = (text: string) =>
  text
    .normalize('NFKC')
    .toLowerCase()
    .replace(/[\s\-_.,;:!?'"`()[\]{}·/]/g, '');

// 서버가 할 채점을 흉내 낸다 (AI 채점은 하지 않고 대기로 둔다)
export function mockSubmitQuiz(
  id: number,
  answers: QuizAnswer[],
): Promise<QuizResult> {
  try {
    const quiz = findQuiz(id);
    const results: QuizResultItem[] = quiz.questions.map((question) => {
      const answer = answers.find((item) => item.questionId === question.id);
      const type = typeOf(question);
      const maxScore = maxScoreOf(question);
      const text = answer?.text?.trim() || null;
      let correct = false;
      let pending = false;
      if (type === 'multiple_choice') {
        correct = (answer?.choiceIndex ?? -1) === question.answerIndex;
      } else if (text && type === 'short_answer') {
        correct = (question.acceptedAnswers ?? []).some(
          (item) => normalize(item) === normalize(text),
        );
        pending = !correct;
      } else if (text) {
        pending = true;
      }
      return {
        questionId: question.id,
        type,
        choiceIndex:
          type === 'multiple_choice' ? (answer?.choiceIndex ?? -1) : -1,
        textAnswer: text,
        correct,
        correctChoiceIndex:
          type === 'multiple_choice' ? question.answerIndex : null,
        concepts: question.conceptIds.map((conceptId) => ({
          conceptId,
          name: CONCEPT_NAMES[conceptId] ?? conceptId,
        })),
        aiExplanation: question.aiExplanation,
        score: correct ? maxScore : 0,
        maxScore,
        gradingStatus: pending ? 'pending' : 'graded',
        gradingMethod:
          type === 'multiple_choice' ? 'code' : pending ? 'ai' : 'match',
        gradingMessage: pending ? '목업에서는 AI 채점을 하지 않아요.' : null,
        aiCalledCount: 0,
        aiJudgedCount: 0,
        acceptedAnswers: question.acceptedAnswers ?? [],
        aiModelAnswer: null,
        rubric: (question.rubric ?? []).map((item) => ({
          ...item,
          earned: null,
          level: null,
          agreeCount: 0,
          judgedCount: 0,
          aiReason: null,
        })),
        aiFeedback: null,
      };
    });
    const graded = results.filter((item) => item.gradingStatus === 'graded');
    const pendingCount = results.length - graded.length;
    const score = graded.reduce((sum, item) => sum + item.score, 0);
    const maxScore = graded.reduce((sum, item) => sum + item.maxScore, 0);
    const result: QuizResult = {
      submissionId: Date.now(),
      submittedAt: new Date().toISOString(),
      correctCount: results.filter((item) => item.correct).length,
      totalCount: results.length,
      score,
      maxScore,
      pendingCount,
      reviewCount: 0,
      mode: quiz.mode ?? null,
      results,
      assessment: {
        status:
          pendingCount > 0 || graded.length < 3
            ? 'needs_check'
            : maxScore > 0 && score / maxScore >= 0.8
              ? 'ready'
              : 'not_ready',
        reasons: ['목업 결과예요. 실제 판단은 서버가 해요.'],
        byType: [],
        byConcept: [],
      },
      suggestions: [],
    };
    setResult(id, result);
    return mockDelay(result, 600);
  } catch (error) {
    return Promise.reject(error);
  }
}

export function mockRegradeQuiz(id: number): Promise<QuizResult> {
  const result = getResults()[id];
  return result
    ? mockDelay(result)
    : Promise.reject(new ApiError('제출 기록을 찾을 수 없어요.', 404));
}

export function mockGetLatestQuizResult(
  id: number,
): Promise<QuizResult | null> {
  return mockDelay(getResults()[id] ?? null);
}

// 목업은 AI를 부르지 않는다. 고른 유형으로 예시 문제를 돌려주고 AI가 만든 것처럼 표시하지 않는다
export function mockCreateQuiz(input: QuizCreateInput): Promise<Quiz> {
  const id = nextCreatedQuizId(allQuizzes());
  const samples: Record<string, MockQuestion> = {
    multiple_choice: {
      id: id * 10 + 1,
      text: '받는 사람만 열 수 있게 암호화하려면 어떤 키를 쓸까요?',
      choices: [
        '내 개인키',
        '내 공개키',
        '받는 사람의 공개키',
        '받는 사람의 개인키',
      ],
      answerIndex: 2,
      conceptIds: ['public-key'],
      aiExplanation: '받는 사람의 공개키로 잠그면 그 사람의 개인키로만 열려요.',
    },
    short_answer: {
      id: id * 10 + 2,
      type: 'short_answer',
      text: '서로 다른 두 입력이 같은 해시값을 갖기 어려워야 한다는 성질은?',
      choices: [],
      answerIndex: -1,
      acceptedAnswers: ['충돌 저항성', '충돌 회피성'],
      conceptIds: ['hash'],
      aiExplanation: '같은 값이 나오는 두 입력을 찾기 어려운 성질이에요.',
    },
    essay: {
      id: id * 10 + 3,
      type: 'essay',
      text: '전자서명과 공개키 암호화에서 키를 쓰는 방향이 어떻게 다른지 설명해 보세요.',
      choices: [],
      answerIndex: -1,
      rubric: [
        { criterion: '서명에 쓰는 키 설명', points: 3 },
        { criterion: '암호화와 방향 비교', points: 3 },
      ],
      conceptIds: ['digital-signature', 'public-key'],
      aiExplanation: '',
    },
  };
  // 개념 확인 퀴즈면 그 개념으로 표시만 한다 (목업 문제는 예시 그대로, AI가 만든 것처럼 보이지 않게 제목에 목업을 붙인다)
  const checkName = input.checkConceptId
    ? (mockMaterialsSnapshot()
        .find((material) => material.id === input.materialId)
        ?.concepts.find((concept) => concept.conceptId === input.checkConceptId)
        ?.name ?? input.checkConceptId)
    : null;
  saveCreated([
    {
      id,
      title: checkName ? `${checkName} 확인 퀴즈 (목업)` : '예시 퀴즈 (목업)',
      courseId: input.courseId,
      materialId: input.checkConceptId ? (input.materialId ?? null) : null,
      mode: input.mode,
      checkTopic:
        input.checkConceptId && input.materialId
          ? {
              materialId: input.materialId,
              topicId: input.checkConceptId,
              name: checkName ?? input.checkConceptId,
            }
          : null,
      questions: input.questionTypes.map((type) => samples[type]),
    },
    ...loadCreated(),
  ]);
  return mockGetQuiz(id);
}

// 목업은 AI를 부르지 않으므로 만들기 요청은 바로 끝난 것으로 본다
export function mockGetGenerationStatus(requestKey: string) {
  void requestKey;
  return Promise.reject(
    new ApiError('퀴즈 만들기 요청을 찾을 수 없어요.', 404),
  );
}

// 목업은 AI를 부르지 않는다. 사전·사후평가는 예시 퀴즈를 돌려주고 AI가 만든 것처럼 표시하지 않는다
export function mockCreateAssessment(input: AssessmentCreateInput) {
  return mockCreateQuiz({
    requestKey: input.requestKey,
    courseId: 1,
    mode: 'self_development',
    questionTypes: ['multiple_choice', 'short_answer'],
    questionCount: 2,
    difficulty: 'medium',
  });
}

// 목업 비교 (예시 데이터)
// 예시 사전·사후평가 비교가 있는 과목
const EXAMPLE_ASSESSMENT_COURSE_ID = 1;

export function mockGetAssessmentComparison(target: {
  courseId?: number;
  materialId?: number;
}): Promise<AssessmentComparison> {
  // 예시 비교는 정보보호개론 과목의 것이라 다른 과목·자료에는 "아직 사전평가가 없어요"로 보여 준다
  const courseId =
    target.courseId ??
    mockMaterialsSnapshot().find(
      (material) => material.id === target.materialId,
    )?.courseId;
  if (courseId !== undefined && courseId !== EXAMPLE_ASSESSMENT_COURSE_ID) {
    return mockDelay({
      basis: '최신 사전평가와 그 사후평가를 비교해요.',
      material: null,
      pre: null,
      post: null,
      overallDelta: null,
      concepts: [],
      comparability: null,
    });
  }
  // 공개키 암호 정리 노트(자료 1)는 사전평가만 본 상태다 (커리큘럼 STEP 1 "7/12점", STEP 4 "사후평가 아직"과 같다)
  if (target.materialId === CURRICULUM_MATERIAL_ID) {
    return mockDelay(CURRICULUM_PRE_ONLY);
  }
  // 과목 전체(대시보드)는 가장 최근 사전평가인 해시 함수 연습문제 풀이(자료 2)의 사전·사후 비교를 보여 준다
  return mockDelay(HASH_COMPARISON);
}

// 커리큘럼이 기준으로 삼는 자료 (mocks/curriculum.ts의 첫 자료, 사전평가 11/1)
const CURRICULUM_MATERIAL_ID = 1;

const CURRICULUM_PRE_ONLY: AssessmentComparison = {
  basis: '최신 사전평가와 그 사후평가를 비교해요.',
  material: { id: 1, title: '공개키 암호 정리 노트' },
  pre: {
    quizId: 1,
    title: '공개키 암호 정리 노트 · 사전평가',
    submissionId: 1,
    submittedAt: '2026-11-01T10:00:00',
    questionCount: 6,
    gradedCount: 6,
    score: 7,
    maxScore: 12,
    pendingCount: 0,
    reviewCount: 0,
  },
  post: null,
  overallDelta: null,
  comparability: {
    sameConcepts: true,
    sameQuestionCount: true,
    sameQuestionTypes: true,
    sameDifficulty: true,
    notes: [],
  },
  // 개념마다 3점, 기준 60% (mocks/curriculum.ts SAMPLE_PRE와 같다)
  concepts: [
    preOnly('public-key', '공개키 암호', 1),
    preOnly('digital-signature', '전자서명', 3),
    preOnly('pki', '인증서(PKI)', 2),
    preOnly('hash', '해시 함수', 1),
  ],
};

function preOnly(conceptId: string, name: string, score: number) {
  return {
    conceptId,
    name,
    preScore: score,
    preMaxScore: 3,
    postScore: null,
    postMaxScore: null,
    delta: null,
    status: score / 3 >= 0.6 ? ('ok' as const) : ('weak' as const),
  };
}

// 해시 함수 연습문제 풀이(자료 2): 11/2 사전평가 → 11/3 사후평가. 커리큘럼(자료 1)보다 뒤에 본 평가라 과목 비교의 기준이 된다
const HASH_COMPARISON: AssessmentComparison = {
  basis: '최신 사전평가와 그 사후평가를 비교해요.',
  material: { id: 2, title: '해시 함수 연습문제 풀이' },
  pre: {
    quizId: 1,
    title: '해시 함수 연습문제 풀이 · 사전평가',
    submissionId: 2,
    submittedAt: '2026-11-02T10:00:00',
    questionCount: 6,
    gradedCount: 6,
    score: 3,
    maxScore: 6,
    pendingCount: 0,
    reviewCount: 0,
  },
  post: {
    quizId: 2,
    title: '해시 함수 연습문제 풀이 · 사후평가',
    submissionId: 3,
    submittedAt: '2026-11-03T10:00:00',
    questionCount: 6,
    gradedCount: 6,
    score: 5,
    maxScore: 6,
    pendingCount: 0,
    reviewCount: 0,
  },
  overallDelta: 33,
  comparability: {
    sameConcepts: true,
    sameQuestionCount: true,
    sameQuestionTypes: true,
    sameDifficulty: true,
    notes: [
      '사후평가 문제는 같은 개념으로 새로 만든 문제라 문제 자체는 달라요.',
    ],
  },
  concepts: [
    {
      conceptId: 'hash',
      name: '해시 함수',
      preScore: 3,
      preMaxScore: 6,
      postScore: 5,
      postMaxScore: 6,
      delta: 33,
      status: 'improved',
    },
  ],
};

export function mockGetQuizAiStatus() {
  return mockDelay({
    available: false,
    generation: null,
    grading: [],
    message: '목업에서는 AI를 부르지 않아요. 예시 문제만 보여 줘요.',
    materialContentEnabled: false,
  });
}
