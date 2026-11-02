import type {
  AnalysisRun,
  ConceptJudgment,
  JudgmentEvidence,
  ProviderStatus,
} from '@/types';
import { mockDelay } from './delay';

// 시연 과목(정보보호개론)에 맞춘 샘플 분석 결과. 화면 확인용 예시이고 실제 모델이 판단한 값이 아니다.
// 강의자료 문장은 넣지 않는다. Gemini는 "연결 안 됨" 상태를 보여 주려고 미설정으로 둔다
const PROVIDERS: ProviderStatus[] = [
  { provider: 'gpt', model: null, configured: true },
  { provider: 'claude', model: null, configured: true },
  { provider: 'gemini', model: null, configured: false },
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

const RUN: AnalysisRun = {
  id: 1,
  courseId: 1,
  status: 'partial',
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
      judgedConceptCount: 4,
      droppedEvidenceCount: 1,
    },
    {
      provider: 'gemini',
      model: null,
      status: 'not_configured',
      message: '이 모델은 연결되어 있지 않아요.',
      judgedConceptCount: 0,
      droppedEvidenceCount: 0,
    },
  ],
  concepts: [
    {
      concept: '공개키',
      comparison: {
        outcome: 'unanimous',
        status: 'not_understood',
        judgedCount: 2,
        agreeCount: 2,
        calledCount: 2,
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
      ],
      feedback: null,
      studyOrder: 1,
      recommendConfirmQuiz: false,
    },
    {
      concept: '해시',
      comparison: {
        outcome: 'disagreement',
        status: null,
        judgedCount: 2,
        agreeCount: 1,
        calledCount: 2,
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
      ],
      feedback: null,
      studyOrder: 2,
      recommendConfirmQuiz: false,
    },
    {
      concept: '방화벽',
      comparison: {
        outcome: 'unanimous',
        status: 'insufficient_evidence',
        judgedCount: 2,
        agreeCount: 2,
        calledCount: 2,
      },
      judgments: [
        judgment(
          'gpt',
          'insufficient_evidence',
          '방화벽을 다룬 메시지가 거의 없어요.',
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
      ],
      feedback: null,
      studyOrder: 3,
      recommendConfirmQuiz: false,
    },
    {
      concept: '접근 제어',
      comparison: {
        outcome: 'single_model',
        status: 'partial',
        judgedCount: 1,
        agreeCount: 1,
        calledCount: 2,
      },
      judgments: [
        judgment(
          'gpt',
          'partial',
          '역할 기반 접근 제어 예시는 들었지만 다른 방식과 비교하지는 않았어요.',
          [
            explanation(
              301,
              '학생은 성적 조회만, 교수는 입력까지 되는 게 역할 기반이지',
            ),
          ],
        ),
      ],
      feedback: null,
      studyOrder: 4,
      recommendConfirmQuiz: false,
    },
    {
      concept: '대칭키 암호',
      comparison: {
        outcome: 'unanimous',
        status: 'understood',
        judgedCount: 2,
        agreeCount: 2,
        calledCount: 2,
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
      ],
      feedback: null,
      studyOrder: 5,
      recommendConfirmQuiz: false,
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

export function mockGetProviders(): Promise<ProviderStatus[]> {
  return mockDelay(PROVIDERS);
}

export function mockGetLatestAnalysis(
  courseId: number,
): Promise<AnalysisRun | null> {
  return mockDelay(latest.get(courseId) ?? null);
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
  return mockDelay(run, 1500);
}
