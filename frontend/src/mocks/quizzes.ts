import type { Quiz, QuizAnswer, QuizResult, QuizSummary } from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';

// 목업 전용. 실제 서비스에서는 서버가 자료로 퀴즈를 만들고, 채점도 서버 코드가 한다 (프론트는 채점하지 않음).
// 문제는 샘플 자료(mocks/materials.ts)를 바탕으로 직접 쓴 예시이고 시험 문제 원문이 아니다
interface MockQuestion {
  id: number;
  text: string;
  choices: string[];
  answerIndex: number;
  conceptIds: string[];
  // AI가 쓴 해설이라고 가정한 글
  aiExplanation: string;
}

interface MockQuiz extends Omit<Quiz, 'questions'> {
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
const STORAGE_KEY = 'ai-lms-mock-quiz-results';

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

export function mockGetQuizzes(): Promise<QuizSummary[]> {
  const results = getResults();
  return mockDelay(
    QUIZZES.map(({ id, title, courseId, materialId, questions }) => ({
      id,
      title,
      courseId,
      materialId,
      questionCount: questions.length,
      lastCorrectCount: results[id]?.correctCount ?? null,
    })),
  );
}

// 풀기 화면에는 정답·해설을 보내지 않는다
export function mockGetQuiz(id: number): Promise<Quiz> {
  try {
    const { questions, ...quiz } = findQuiz(id);
    return mockDelay({
      ...quiz,
      questions: questions.map(({ id: questionId, text, choices }) => ({
        id: questionId,
        text,
        choices,
      })),
    });
  } catch (error) {
    return Promise.reject(error);
  }
}

// 서버가 할 채점을 흉내 낸다
export function mockSubmitQuiz(
  id: number,
  answers: QuizAnswer[],
): Promise<QuizResult> {
  try {
    const quiz = findQuiz(id);
    const results = quiz.questions.map((question) => {
      const choiceIndex =
        answers.find((answer) => answer.questionId === question.id)
          ?.choiceIndex ?? -1;
      return {
        questionId: question.id,
        choiceIndex,
        correct: choiceIndex === question.answerIndex,
        correctChoiceIndex: question.answerIndex,
        concepts: question.conceptIds.map((conceptId) => ({
          conceptId,
          name: CONCEPT_NAMES[conceptId] ?? conceptId,
        })),
        aiExplanation: question.aiExplanation,
      };
    });
    const result: QuizResult = {
      submissionId: Date.now(),
      submittedAt: new Date().toISOString(),
      correctCount: results.filter((item) => item.correct).length,
      totalCount: results.length,
      results,
    };
    setResult(id, result);
    return mockDelay(result, 600);
  } catch (error) {
    return Promise.reject(error);
  }
}

export function mockGetLatestQuizResult(
  id: number,
): Promise<QuizResult | null> {
  return mockDelay(getResults()[id] ?? null);
}
