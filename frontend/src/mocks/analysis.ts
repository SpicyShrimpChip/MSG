import type {
  AnalysisRun,
  ConceptFeedback,
  ConceptFeedbackInput,
  ConceptJudgment,
  ConceptResult,
  JudgmentEvidence,
  ProviderStatus,
} from '@/types';
import { mockDelay } from './delay';

// 시연 과목(정보보호개론)에 맞춘 샘플 분석 결과. 화면 확인용 예시이고 실제 모델이 판단한 값이 아니다.
// 강의자료 문장은 넣지 않는다. 핵심 차별점(세 모델이 따로 판단하고 비교)을 보여 주려고 세 모델 모두 판단한 상태로 둔다.
// 개념 이름은 자료·커리큘럼·퀴즈 목업(공개키 암호·전자서명·인증서(PKI)·해시 함수)과 같게 맞추고,
// 대칭키 암호는 자료 없이 대화에만 나온 개념이다 (대화만으로는 숙련도를 정하지 않는다는 예시)
const PROVIDERS: ProviderStatus[] = [
  { provider: 'gpt', model: null, configured: true },
  { provider: 'claude', model: null, configured: true },
  { provider: 'gemini', model: null, configured: true },
];

const question = (
  messageId: number,
  excerpt: string,
  conversationTitle = '정보보호 3주차 복습',
): JudgmentEvidence => ({
  messageId,
  conversationId: 11,
  conversationTitle,
  role: 'user',
  kind: 'user_question',
  aiNote: null,
  excerpt,
});

const explanation = (
  messageId: number,
  excerpt: string,
  conversationTitle = '정보보호 4주차 정리',
): JudgmentEvidence => ({
  messageId,
  conversationId: 12,
  conversationTitle,
  role: 'user',
  kind: 'user_explanation',
  aiNote: null,
  excerpt,
});

const judgment = (
  provider: ConceptJudgment['provider'],
  status: ConceptJudgment['status'],
  aiReason: string,
  evidence: JudgmentEvidence[],
  extra: Partial<ConceptJudgment> = {},
): ConceptJudgment => ({
  provider,
  model: null,
  status,
  reportedStatus: status,
  adjustedReason: null,
  aiReason,
  aiNeedsCheck: null,
  aiRecommendedActivity: null,
  evidence,
  ...extra,
});

type Mastery = NonNullable<ConceptResult['mastery']>;
type MasteryRecord = Mastery['history'][number];

// 숙련도 기록 한 줄 (서버 concept_mastery 모양)
const record = (
  recordedAt: string,
  level: MasteryRecord['level'],
  quizGradedCount: number,
  quizRate: number | null,
  conversationCount = 0,
  conversationStatus: MasteryRecord['conversationStatus'] = null,
): MasteryRecord => ({
  recordedAt,
  level,
  quizGradedCount,
  quizRate,
  conversationCount,
  conversationStatus,
  formulaVersion: '1',
});

// 숙련도 예시. 값은 서버 MasteryCalculator 규칙(퀴즈 1문제 1.0, 대화 1건 0.5, 점수 = (Σw·s + 1) ÷ (Σw + 2),
// 근거 무게 3.0 미만이면 근거 부족, 0.4 미만 보완 필요, 0.7 이상 강점)으로 직접 계산한 값이다.
// 퀴즈 근거는 커리큘럼 목업의 사전 테스트(SAMPLE_PRE)와 확인 퀴즈(mocks/study.ts SAMPLE_CHECKS)를 더한 것
const PRE_TEST = '2026-11-01T10:30:00';
const ANALYZED = '2026-11-02T16:20:00';

const RUN: AnalysisRun = {
  id: 1,
  courseId: 1,
  status: 'completed',
  promptVersion: 'example',
  createdAt: '2026-11-02T16:19:00+09:00',
  completedAt: '2026-11-02T16:20:00+09:00',
  input: { conversationCount: 12, messageCount: 340, truncatedMessageCount: 0 },
  models: [
    {
      provider: 'gpt',
      model: null,
      status: 'succeeded',
      message: null,
      judgedConceptCount: 5,
      droppedEvidenceCount: 0,
    },
    {
      provider: 'claude',
      model: null,
      status: 'succeeded',
      message: null,
      judgedConceptCount: 5,
      droppedEvidenceCount: 1,
    },
    {
      provider: 'gemini',
      model: null,
      status: 'succeeded',
      message: null,
      judgedConceptCount: 5,
      droppedEvidenceCount: 0,
    },
  ],
  concepts: [
    {
      // 세 모델 모두 이해 부족 + 퀴즈 6문제 중 2문제 → 보완 필요
      concept: '공개키 암호',
      comparison: {
        outcome: 'unanimous',
        status: 'not_understood',
        judgedCount: 3,
        agreeCount: 3,
        calledCount: 3,
      },
      judgments: [
        judgment(
          'gpt',
          'not_understood',
          '공개키와 개인키 중 어느 쪽으로 암호화하는지를 두 번 다시 물었어요. 서명과 암호화의 차이를 헷갈리는 것으로 보여요.',
          [
            question(
              101,
              '그럼 공개키로 암호화하면 누가 풀 수 있는 거야? 아까랑 반대 아니야?',
            ),
            question(104, '다시 설명해 줘. 서명할 때도 공개키를 쓰는 거지?'),
          ],
          {
            aiRecommendedActivity: '서명과 암호화를 나눠 그림으로 정리해 보기',
          },
        ),
        judgment(
          'claude',
          'not_understood',
          '키 쌍의 역할을 스스로 설명한 메시지가 없고, 같은 질문을 다시 했어요.',
          [question(104, '다시 설명해 줘. 서명할 때도 공개키를 쓰는 거지?')],
        ),
        judgment(
          'gemini',
          'not_understood',
          '받는 사람의 공개키로 잠근다는 설명을 들은 뒤에도 내 공개키로 잠그는 것으로 다시 물었어요.',
          [
            question(
              101,
              '그럼 공개키로 암호화하면 누가 풀 수 있는 거야? 아까랑 반대 아니야?',
            ),
          ],
        ),
      ],
      feedback: null,
      studyOrder: 1,
      recommendConfirmQuiz: false,
      mastery: {
        level: 'needs_work',
        score: 0.33,
        evidenceWeight: 7,
        quizGradedCount: 6,
        quizRate: 0.33,
        conversationCount: 2,
        conversationStatus: 'not_understood',
        formulaVersion: '1',
        quizNeeded: null,
        history: [
          record(PRE_TEST, 'normal', 3, 0.33),
          record(ANALYZED, 'needs_work', 6, 0.33, 2, 'not_understood'),
        ],
      },
    },
    {
      // 세 모델 판단이 모두 달라 대화는 숙련도에 넣지 않음, 퀴즈 5문제 중 3문제 → 보통
      concept: '해시 함수',
      comparison: {
        outcome: 'disagreement',
        status: null,
        judgedCount: 3,
        agreeCount: 1,
        calledCount: 3,
      },
      judgments: [
        judgment(
          'gpt',
          'partial',
          '해시가 되돌릴 수 없다는 점은 설명했지만, 암호화와의 차이는 묻기만 했어요.',
          [
            explanation(
              201,
              '해시는 한 방향이라 원래 값을 다시 구할 수 없다고 이해했어',
            ),
          ],
          {
            aiRecommendedActivity: '암호화와 해시의 차이를 예시로 설명해 보기',
          },
        ),
        judgment(
          'claude',
          'not_understood',
          '비밀번호를 해시로 "복호화"한다고 쓴 부분이 있어 개념이 섞여 있어요.',
          [
            question(
              203,
              '로그인할 때 저장된 해시를 복호화해서 비교하는 거 맞지?',
            ),
          ],
        ),
        judgment(
          'gemini',
          'understood',
          '한 방향이라는 성질을 자기 말로 설명했고, 솔트를 쓰는 이유도 물었어요.',
          [
            explanation(
              201,
              '해시는 한 방향이라 원래 값을 다시 구할 수 없다고 이해했어',
            ),
          ],
        ),
      ],
      feedback: null,
      studyOrder: 2,
      recommendConfirmQuiz: false,
      mastery: {
        level: 'normal',
        score: 0.57,
        evidenceWeight: 5,
        quizGradedCount: 5,
        quizRate: 0.6,
        conversationCount: 0,
        conversationStatus: null,
        formulaVersion: '1',
        quizNeeded: null,
        history: [
          record(PRE_TEST, 'normal', 3, 0.33),
          record(ANALYZED, 'normal', 5, 0.6),
        ],
      },
    },
    {
      // 대화에서는 세 모델 모두 이해로 봤지만 퀴즈 근거가 없어 근거 부족 (대화만으로 단정하지 않음)
      concept: '대칭키 암호',
      comparison: {
        outcome: 'unanimous',
        status: 'understood',
        judgedCount: 3,
        agreeCount: 3,
        calledCount: 3,
      },
      judgments: [
        judgment(
          'gpt',
          'understood',
          '파일 암호화 예시로 같은 키를 쓰는 이유를 직접 설명했어요.',
          [
            explanation(
              401,
              '보내는 쪽과 받는 쪽이 같은 키를 써서 빠르지만 키를 나누는 게 문제야',
            ),
          ],
        ),
        judgment('claude', 'understood', '키 배송 문제까지 스스로 짚었어요.', [
          explanation(
            401,
            '보내는 쪽과 받는 쪽이 같은 키를 써서 빠르지만 키를 나누는 게 문제야',
          ),
        ]),
        judgment(
          'gemini',
          'understood',
          '공개키 암호와 섞어 쓰는 이유(속도)를 스스로 연결했어요.',
          [
            explanation(
              402,
              '그래서 실제로는 대칭키를 공개키로 감싸서 보내는 거구나',
            ),
          ],
        ),
      ],
      feedback: null,
      studyOrder: 5,
      recommendConfirmQuiz: false,
      mastery: {
        level: 'insufficient_evidence',
        score: null,
        evidenceWeight: 1.5,
        quizGradedCount: 0,
        quizRate: null,
        conversationCount: 3,
        conversationStatus: 'understood',
        formulaVersion: '1',
        quizNeeded: 2,
        history: [
          record(ANALYZED, 'insufficient_evidence', 0, null, 3, 'understood'),
        ],
      },
    },
    {
      // 세 모델 모두 판단 근거 부족이라 대화는 넣지 않음, 사전 테스트 3문제 중 2문제 → 보통
      concept: '인증서(PKI)',
      comparison: {
        outcome: 'unanimous',
        status: 'insufficient_evidence',
        judgedCount: 3,
        agreeCount: 3,
        calledCount: 3,
      },
      judgments: [
        judgment(
          'gpt',
          'insufficient_evidence',
          '인증서를 다룬 메시지가 거의 없어요.',
          [],
        ),
        judgment(
          'claude',
          'insufficient_evidence',
          'AI 설명만 있고 내가 쓴 메시지가 없어요.',
          [],
          {
            reportedStatus: 'understood',
            adjustedReason:
              '사용자가 쓴 메시지 근거가 없어 판단 근거 부족으로 봤어요.',
          },
        ),
        judgment(
          'gemini',
          'insufficient_evidence',
          '인증 기관이 무엇인지 한 번 물은 것 말고는 근거가 없어요.',
          [],
        ),
      ],
      feedback: null,
      studyOrder: 3,
      recommendConfirmQuiz: false,
      mastery: {
        level: 'normal',
        score: 0.6,
        evidenceWeight: 3,
        quizGradedCount: 3,
        quizRate: 0.67,
        conversationCount: 0,
        conversationStatus: null,
        formulaVersion: '1',
        quizNeeded: null,
        history: [record(PRE_TEST, 'normal', 3, 0.67)],
      },
    },
    {
      // 3개 중 2개가 부분 이해 + 사전 테스트 3문제 모두 정답 → 강점
      concept: '전자서명',
      comparison: {
        outcome: 'majority',
        status: 'partial',
        judgedCount: 3,
        agreeCount: 2,
        calledCount: 3,
      },
      judgments: [
        judgment(
          'gpt',
          'partial',
          '개인키로 서명한다는 점은 설명했지만, 해시와 함께 쓰는 이유는 묻기만 했어요.',
          [
            explanation(
              501,
              '서명은 내 개인키로 하고 남이 내 공개키로 확인하는 거지',
            ),
          ],
        ),
        judgment(
          'claude',
          'partial',
          '키 방향은 맞게 썼지만 위조를 막는 원리까지는 설명하지 않았어요.',
          [
            explanation(
              501,
              '서명은 내 개인키로 하고 남이 내 공개키로 확인하는 거지',
            ),
          ],
        ),
        judgment(
          'gemini',
          'understood',
          '서명과 암호화의 키 방향 차이를 스스로 비교했어요.',
          [
            explanation(
              501,
              '서명은 내 개인키로 하고 남이 내 공개키로 확인하는 거지',
            ),
          ],
        ),
      ],
      feedback: null,
      studyOrder: 4,
      recommendConfirmQuiz: false,
      mastery: {
        level: 'strong',
        score: 0.75,
        evidenceWeight: 3.5,
        quizGradedCount: 3,
        quizRate: 1,
        conversationCount: 1,
        conversationStatus: 'partial',
        formulaVersion: '1',
        quizNeeded: null,
        history: [
          record(PRE_TEST, 'strong', 3, 1),
          record(ANALYZED, 'strong', 3, 1, 1, 'partial'),
        ],
      },
    },
  ],
  // 목업에서만 동적 배치 예시를 보여 준다 (백엔드는 아직 배치를 주지 않는다)
  layout: {
    aiSummary:
      '이해 부족과 의견이 갈린 개념이 있어 개념별 판단을 위에 두었어요.',
    basis: {
      notUnderstoodCount: 1,
      disagreementCount: 1,
      conversationCount: 12,
    },
    items: [
      {
        component: 'conceptTable',
        order: 1,
        size: 'normal',
        aiReason: '먼저 볼 개념을 고르기 쉽게',
      },
      {
        component: 'conceptDetail',
        order: 2,
        size: 'normal',
        aiReason: '고른 개념의 근거를 바로 옆에',
      },
      {
        component: 'recommendation',
        order: 3,
        size: 'normal',
        aiReason: '다음에 할 공부 순서',
      },
      {
        component: 'models',
        order: 4,
        size: 'normal',
        aiReason: '어떤 모델이 판단했는지 확인',
      },
      {
        component: 'summary',
        order: 5,
        size: 'wide',
        aiReason: '분석에 쓴 기록의 양',
      },
    ],
  },
};

// 과목별 최근 분석 (목업에서는 분석을 시작하면 여기에 남는다, 새로고침하면 처음으로)
const latest = new Map<number, AnalysisRun>([[1, RUN]]);
// 내가 남긴 피드백 (과목 id → 개념 → 피드백)
const feedbacks = new Map<number, Map<string, ConceptFeedback>>();

// 서버처럼 읽을 때마다 피드백을 붙이고 순서를 다시 매긴다: "더 공부할래요"가 맨 앞, 나머지는 원래 순서.
// AI 판단(comparison·judgments)은 바꾸지 않는다
function withFeedback(run: AnalysisRun): AnalysisRun {
  const mine = feedbacks.get(run.courseId);
  const concepts = run.concepts
    .map((item) => ({ ...item, feedback: mine?.get(item.concept) ?? null }))
    .sort(
      (a, b) =>
        Number(b.feedback?.feedback === 'want_to_study') -
          Number(a.feedback?.feedback === 'want_to_study') ||
        a.studyOrder - b.studyOrder,
    )
    .map((item, index) => ({
      ...item,
      studyOrder: index + 1,
      recommendConfirmQuiz: item.feedback?.feedback === 'already_know',
    }));
  return { ...run, concepts };
}

export function mockGetProviders(): Promise<ProviderStatus[]> {
  return mockDelay(PROVIDERS);
}

export function mockGetLatestAnalysis(
  courseId: number,
): Promise<AnalysisRun | null> {
  const run = latest.get(courseId);
  return mockDelay(run ? withFeedback(run) : null);
}

export function mockSaveConceptFeedback(
  courseId: number,
  input: ConceptFeedbackInput,
): Promise<ConceptFeedback> {
  const mine = feedbacks.get(courseId) ?? new Map<string, ConceptFeedback>();
  const saved: ConceptFeedback = {
    id: mine.get(input.concept)?.id ?? Date.now(),
    courseId,
    concept: input.concept,
    feedback: input.feedback,
    comment: input.comment ?? null,
    analysisRunId: input.analysisRunId ?? null,
    updatedAt: new Date().toISOString(),
    nextAction:
      input.feedback === 'already_know' ? 'confirm_quiz' : 'prioritize_study',
  };
  mine.set(input.concept, saved);
  feedbacks.set(courseId, mine);
  return mockDelay(saved);
}

export function mockDeleteConceptFeedback(id: number): Promise<void> {
  for (const mine of feedbacks.values()) {
    for (const [concept, item] of mine) {
      if (item.id === id) mine.delete(concept);
    }
  }
  return mockDelay(undefined);
}

// 목업은 정보보호개론 예시를 그 과목 결과처럼 돌려준다
export function mockStartAnalysis(courseId: number): Promise<AnalysisRun> {
  const run: AnalysisRun = {
    ...RUN,
    id: Date.now(),
    courseId,
    createdAt: new Date().toISOString(),
    completedAt: new Date().toISOString(),
  };
  latest.set(courseId, run);
  return mockDelay(withFeedback(run), 1500);
}
