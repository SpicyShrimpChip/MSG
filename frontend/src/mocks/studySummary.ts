import type {
  StudySummary,
  StudySummaryInput,
  StudySummaryPurpose,
} from '@/types';
import { ApiError } from '@/api/request';
import { mockDelay } from './delay';

// 체험 모드 예시 요약 (자료 1 "공개키 암호 정리 노트", 시험 대비). 직접 쓴 예시이고 실제 AI가 만든 결과가 아니다.
// 서버 응답과 같은 모양으로 원문 근거 / AI 보충 / 확인 실패를 섞어 화면 표시를 확인할 수 있게 했다
const SAMPLE: StudySummary = {
  materialId: 1,
  purpose: 'exam_prep',
  status: 'ready',
  reused: false,
  stale: false,
  staleReason: null,
  generatedBy: { provider: 'gpt', model: '예시' },
  promptVersion: 'summary-v1',
  createdAt: '2026-10-21T09:10:00',
  verification: {
    status: 'partial',
    verified: 5,
    partial: 1,
    failed: 1,
    insufficient: 0,
    supplement: 2,
  },
  sourceScope: ['문단 1', '문단 2', '문단 3'],
  sourceTruncated: false,
  content: {
    overview: {
      name: null,
      text: '공개키 암호의 키 쌍, 전자서명, 인증서를 차례로 정리한 노트예요.',
      basis: 'material',
      quote:
        '공개키 암호는 누구나 아는 공개키와 나만 가진 개인키를 한 쌍으로 쓴다',
      location: '문단 1',
      check: 'verified',
    },
    concepts: [
      {
        name: '공개키 암호',
        text: '공개키와 개인키를 한 쌍으로 쓰고, 받는 사람의 공개키로 잠근다.',
        basis: 'material',
        quote: '받는 사람의 공개키로 잠그고',
        location: '문단 1',
        check: 'verified',
      },
      {
        name: '전자서명',
        text: '보내는 사람이 개인키로 서명하고 누구나 공개키로 검증한다.',
        basis: 'material',
        quote: '보내는 사람이 개인키로 서명하고',
        location: '문단 2',
        check: 'verified',
      },
      {
        name: '인증서',
        text: '공개키가 정말 그 사람 것인지 인증 기관이 보증한다.',
        basis: 'material',
        quote: '인증 기관이 발급한 인증서로 확인한다',
        location: '문단 3',
        check: 'verified',
      },
    ],
    principles: [
      {
        name: null,
        text: '암호화와 서명은 같은 키 쌍을 반대 방향으로 쓴다.',
        basis: 'material',
        quote: '전자서명은 반대로',
        location: '문단 2',
        check: 'partial',
      },
    ],
    relations: [
      {
        name: null,
        text: '인증서는 공개키 암호와 전자서명이 믿을 만하게 동작하도록 공개키의 주인을 보증한다.',
        basis: 'supplement',
        quote: null,
        location: null,
        check: null,
      },
    ],
    examples: [
      {
        name: null,
        text: '자물쇠(공개키)는 나눠 주고 열쇠(개인키)는 나만 갖는 비유로 기억할 수 있다.',
        basis: 'supplement',
        quote: null,
        location: null,
        check: null,
      },
    ],
    confusions: [
      {
        name: null,
        text: '"내 공개키"가 아니라 "받는 사람의 공개키"로 잠근다.',
        basis: 'material',
        quote: '받는 사람만 자기 개인키로 연다',
        location: '문단 1',
        check: 'verified',
      },
      {
        name: null,
        text: '공개키 암호는 대칭키보다 항상 빠르다.',
        basis: 'material',
        quote: null,
        location: null,
        check: 'failed',
      },
    ],
    keyPoints: [],
    selfCheck: [
      {
        question: '전자서명에서 서명과 검증에 각각 어떤 키를 쓰나요?',
        hint: '암호화와 방향이 반대예요.',
      },
    ],
  },
  sources: [
    { location: '문단 1', count: 3 },
    { location: '문단 2', count: 2 },
    { location: '문단 3', count: 1 },
  ],
};

const none = (materialId: number, purpose: StudySummaryPurpose) =>
  ({
    materialId,
    purpose,
    status: 'none',
    reused: false,
    stale: false,
    staleReason: null,
    generatedBy: null,
    promptVersion: null,
    createdAt: null,
    verification: null,
    sourceScope: [],
    sourceTruncated: false,
    content: null,
    sources: [],
  }) satisfies StudySummary;

export function mockGetStudySummary(
  materialId: number,
  purpose: StudySummaryPurpose,
): Promise<StudySummary> {
  return mockDelay(
    materialId === SAMPLE.materialId && purpose === SAMPLE.purpose
      ? SAMPLE
      : none(materialId, purpose),
  );
}

// 목업은 AI를 부르지 않는다. 예시 요약이 있으면 재사용처럼 돌려주고, 없으면 서버 기본값(본문 전송 꺼짐)처럼 거절한다
export function mockGenerateStudySummary(
  materialId: number,
  input: StudySummaryInput,
): Promise<StudySummary> {
  if (
    materialId === SAMPLE.materialId &&
    input.purpose === SAMPLE.purpose &&
    !input.regenerate
  ) {
    return mockDelay({ ...SAMPLE, reused: true });
  }
  return Promise.reject(
    new ApiError('목업에서는 AI를 부르지 않아 요약을 새로 만들지 않아요.', 403),
  );
}
