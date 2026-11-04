import type {
  AssessmentComparison,
  AssessmentCreateInput,
  ConceptQuizStats,
  Quiz,
  QuizAnswer,
  QuizCreateInput,
  QuizResult,
  QuizResultItem,
  QuizSummary,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';

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
> {
  mode?: Quiz['mode'];
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

function findQuiz(id: number): MockQuiz {
  const quiz = QUIZZES.find((item) => item.id === id);
  if (!quiz) throw new ApiError('퀴즈를 찾을 수 없어요.', 404);
  return quiz;
}

const typeOf = (question: MockQuestion) => question.type ?? 'multiple_choice';
const maxScoreOf = (question: MockQuestion) =>
  question.rubric?.reduce((sum, item) => sum + item.points, 0) ?? 1;

export function mockGetQuizzes(): Promise<QuizSummary[]> {
  const results = getResults();
  return mockDelay(
    QUIZZES.map(({ id, title, courseId, materialId, questions, mode }) => ({
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
    })),
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

let nextMockId = 100;

// 목업은 AI를 부르지 않는다. 고른 유형으로 예시 문제를 돌려주고 AI가 만든 것처럼 표시하지 않는다
export function mockCreateQuiz(input: QuizCreateInput): Promise<Quiz> {
  const id = nextMockId++;
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
  QUIZZES.unshift({
    id,
    title: '예시 퀴즈 (목업)',
    courseId: input.courseId,
    materialId: null,
    mode: input.mode,
    questions: input.questionTypes.map((type) => samples[type]),
  });
  return mockGetQuiz(id);
}

export function mockGetConceptQuizStats(
  courseId: number,
): Promise<ConceptQuizStats[]> {
  void courseId;
  return mockDelay([]);
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
export function mockGetAssessmentComparison(target: {
  courseId?: number;
  materialId?: number;
}): Promise<AssessmentComparison> {
  void target;
  return mockDelay({
    basis: '예시 데이터예요. 실제로는 최신 사전평가와 그 사후평가를 비교해요.',
    material: { id: 1, title: '예시 자료' },
    pre: {
      quizId: 1,
      title: '예시 자료 · 사전평가',
      submissionId: 1,
      submittedAt: '2026-11-01T10:00:00',
      questionCount: 6,
      gradedCount: 6,
      score: 3,
      maxScore: 6,
      pendingCount: 0,
      reviewCount: 0,
    },
    post: {
      quizId: 2,
      title: '예시 자료 · 사후평가',
      submissionId: 2,
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
        preScore: 1,
        preMaxScore: 3,
        postScore: 3,
        postMaxScore: 3,
        delta: 67,
        status: 'improved',
      },
      {
        conceptId: 'pki',
        name: '인증서(PKI)',
        preScore: 2,
        preMaxScore: 3,
        postScore: 2,
        postMaxScore: 3,
        delta: 0,
        status: 'unchanged',
      },
    ],
  });
}

export function mockGetQuizAiStatus() {
  return mockDelay({
    available: false,
    generation: null,
    grading: [],
    message: '목업에서는 AI를 부르지 않아요. 예시 문제만 보여 줘요.',
    materialContentEnabled: false,
  });
}
