import type { ReactNode } from 'react';
import { Check, FileText, Lock } from 'lucide-react';
import { AiNote, Chip, LevelBadge, ScoreBar } from '@/components';
import type { MasteryLevel } from '@/types';
import { Dim, MaskSwap, Reveal, SlotItem } from './SceneMotion';

// 스크롤 소개의 오른쪽 장면들 (브라우저 창 틀 안에 들어간다). 모두 화면 소개용 예시라서 끝에 "예시" 표시를 붙인다
// (실제 사용자 데이터가 아니고, 대화·점수는 mocks/analysis.ts의 시연 과목에 맞춘 값이다).
// 장면이 들어오면 안의 요소가 차례로 나타난다 (SceneMotion)

function Caption({ delay, children }: { delay: number; children: ReactNode }) {
  return (
    <Reveal as="p" delay={delay} className="text-caption text-ink-muted">
      {children}
    </Reveal>
  );
}

const SAMPLE_CONVERSATIONS = [
  { title: '공개키로 암호화하면 누가 열 수 있어?', keyword: '공개키' },
  { title: '전자서명 검증 순서 다시 설명해 줘', keyword: '전자서명' },
  { title: '비밀번호 저장할 때 솔트는 왜 써?', keyword: '해시' },
];

function ImportScene() {
  return (
    <div className="flex flex-col gap-4">
      <Reveal
        delay={0.08}
        className="flex items-center gap-3 rounded-xl border border-dashed border-line-strong px-4 py-3"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-primary">
          <FileText size={18} aria-hidden="true" />
        </span>
        <span className="min-w-0">
          <span className="block text-body font-bold text-heading">
            conversations.json
          </span>
          <span className="text-caption text-ink-muted">
            ChatGPT 내보내기 · 내 브라우저에서 읽는 중
          </span>
        </span>
      </Reveal>
      <ul className="flex flex-col gap-2">
        {SAMPLE_CONVERSATIONS.map((conversation, index) => (
          <Reveal
            as="li"
            key={conversation.title}
            delay={0.22 + index * 0.1}
            className="flex items-center gap-3 rounded-xl border border-line px-3.5 py-3"
          >
            <Reveal
              as="span"
              effect="pop"
              delay={0.52 + index * 0.12}
              className="flex size-5 shrink-0 items-center justify-center rounded-md bg-primary text-on-primary"
            >
              <Check size={12} strokeWidth={3.5} aria-label="선택함" />
            </Reveal>
            <span className="min-w-0 flex-1 truncate text-body text-heading">
              {conversation.title}
            </span>
            <Chip tone="primary" className="shrink-0">
              {conversation.keyword}
            </Chip>
          </Reveal>
        ))}
        <Reveal
          as="li"
          delay={0.52}
          className="flex items-center gap-3 rounded-xl bg-surface-muted px-3.5 py-3"
        >
          <span
            className="size-5 shrink-0 rounded-md border-[1.5px] border-line-strong"
            aria-label="선택 안 함"
          />
          <Dim delay={0.9} className="min-w-0 flex-1 text-body text-ink-soft">
            주말 여행 일정 짜 줘
          </Dim>
          <Dim delay={0.9} className="text-caption text-ink-muted">
            학습과 관련 없음
          </Dim>
        </Reveal>
      </ul>
      <Caption delay={1}>
        예시 · 대화 214개 중 키워드에 맞는 12개를 골랐어요
      </Caption>
    </div>
  );
}

function MaskScene() {
  return (
    <div className="flex flex-col gap-4.5">
      <Reveal
        as="p"
        delay={0.08}
        className="flex items-center gap-2 text-caption font-bold text-ink-muted"
      >
        <Lock size={14} aria-hidden="true" />내 브라우저에서 가리는 중
      </Reveal>
      <Reveal delay={0.16} className="flex items-start gap-3">
        <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-pill text-caption font-extrabold text-heading">
          나
        </span>
        <p className="flex-1 rounded-tl-sm rounded-tr-2xl rounded-b-2xl bg-surface-muted px-4.5 py-4 leading-loose text-heading">
          과제는{' '}
          <MaskSwap
            original="student@example.com"
            masked="[이메일]"
            delay={0.65}
          />
          으로 냈고, 조원 번호는{' '}
          <MaskSwap original="010-0000-0000" masked="[전화번호]" delay={1.1} />
          이야. 그런데 공개키로 암호화하면 누가 열 수 있어?
        </p>
      </Reveal>
      <div className="flex flex-wrap gap-2 pl-11">
        <Reveal as="span" delay={1.65}>
          <Chip tone="primary">이메일 1개 가림</Chip>
        </Reveal>
        <Reveal as="span" delay={1.75}>
          <Chip tone="primary">전화번호 1개 가림</Chip>
        </Reveal>
        <Reveal as="span" delay={1.85}>
          <Chip>원본 파일은 보내지 않아요</Chip>
        </Reveal>
      </div>
      <div className="pl-11">
        <Caption delay={1.95}>예시 화면이에요</Caption>
      </div>
    </div>
  );
}

const SAMPLE_MASTERY: { name: string; score: number; level: MasteryLevel }[] = [
  { name: '접근 통제', score: 76, level: 'strength' },
  { name: '해시 함수', score: 63, level: 'normal' },
  { name: '인증서(PKI)', score: 55, level: 'normal' },
  { name: '공개키 암호', score: 41, level: 'weakness' },
  { name: '전자서명', score: 38, level: 'weakness' },
];

function MasteryScene() {
  return (
    <div className="flex flex-col gap-4.5">
      <Reveal
        delay={0.08}
        className="flex flex-wrap items-baseline justify-between gap-2"
      >
        <p className="text-lg font-bold text-heading">개념별 숙련도</p>
        <p className="text-caption text-ink-muted">
          정보보호개론 · 대화 9건 · 퀴즈 22문제
        </p>
      </Reveal>
      <ul className="flex flex-col gap-4">
        {SAMPLE_MASTERY.map((concept, index) => (
          <Reveal
            as="li"
            key={concept.name}
            delay={0.16 + index * 0.08}
            className="flex flex-col gap-2"
          >
            <div className="flex items-center justify-between gap-2 text-body">
              <span className="font-medium text-heading">{concept.name}</span>
              <span className="flex items-center gap-2">
                {/* 보완 필요는 막대가 다 차오른 뒤에 톡 나타난다 */}
                <Reveal
                  as="span"
                  effect="pop"
                  delay={concept.level === 'weakness' ? 1.3 + index * 0.05 : 0}
                >
                  <LevelBadge level={concept.level} />
                </Reveal>
                <span className="font-bold text-heading">{concept.score}</span>
              </span>
            </div>
            <ScoreBar
              value={concept.score}
              level={concept.level}
              delay={0.3 + index * 0.12}
            />
          </Reveal>
        ))}
      </ul>
      <Caption delay={1.5}>예시 점수 · 정해진 규칙으로 계산해요</Caption>
    </div>
  );
}

const SAMPLE_EVIDENCE = [
  {
    date: '10/21',
    source: '대화',
    summary: '서명 검증 순서를 거꾸로 설명했다가 다시 질문함',
  },
  { date: '10/22', source: '퀴즈', summary: '2문제 모두 오답' },
];

function EvidenceScene() {
  return (
    <div className="flex flex-col gap-4">
      <Reveal delay={0.08} className="flex items-center justify-between gap-2">
        <p className="text-lg font-bold text-heading">전자서명 · 38점</p>
        <LevelBadge level="weakness" />
      </Reveal>
      <Reveal
        as="p"
        delay={0.16}
        className="text-caption font-bold text-ink-muted"
      >
        이 기록을 보고 판단했어요
      </Reveal>
      {/* 날짜별 기록을 세로 선 위에 점으로 잇는다 */}
      <ol className="ml-1 border-l-2 border-line">
        {SAMPLE_EVIDENCE.map((evidence, index) => (
          <Reveal
            as="li"
            key={evidence.date}
            delay={0.3 + index * 0.18}
            className="relative flex items-start gap-3 py-1.5 pl-4 text-body text-ink"
          >
            <span
              aria-hidden="true"
              className="absolute top-2.5 -left-[7px] size-3 rounded-full border-2 border-primary bg-surface-raised"
            />
            <span className="w-10 shrink-0 pt-0.5 text-caption text-ink-muted">
              {evidence.date}
            </span>
            <Chip className="shrink-0">{evidence.source}</Chip>
            <span>{evidence.summary}</span>
          </Reveal>
        ))}
      </ol>
      <Reveal delay={0.8}>
        <AiNote title="이렇게 판단한 이유">
          개인키로 서명하고 공개키로 검증하는 방향을 반대로 기억하고 있어요.
          대화와 퀴즈에서 같은 부분이 두 번 막혔어요.
        </AiNote>
      </Reveal>
      <Caption delay={0.95}>
        예시 · 점수는 규칙 코드가, 설명은 AI가 써요
      </Caption>
    </div>
  );
}

// from: 처음 섞여 있던 자리, 배열 순서가 추천 순서다
const SAMPLE_ORDER = [
  { name: '공개키 암호', action: '요약 → 퀴즈 5문제', from: 2 },
  { name: '전자서명', action: '퀴즈 다시 풀기', from: 0 },
  { name: '인증서(PKI)', action: '챗봇에게 묻기', from: 1 },
];
// 한 칸 높이(h-14 = 56px) + 칸 사이(10px)
const ORDER_SLOT = 66;

function OrderScene() {
  return (
    <div className="flex flex-col gap-4">
      <Reveal delay={0.08} className="flex items-center justify-between gap-2">
        <p className="text-lg font-bold text-heading">추천 학습 순서</p>
        <Reveal
          as="span"
          delay={1.3}
          className="text-caption font-bold text-primary"
        >
          순서를 다시 짰어요
        </Reveal>
      </Reveal>
      <ol
        className="relative"
        style={{ height: ORDER_SLOT * SAMPLE_ORDER.length - 10 }}
      >
        {SAMPLE_ORDER.map((item, index) => (
          <SlotItem
            key={item.name}
            from={item.from}
            to={index}
            slotHeight={ORDER_SLOT}
            delay={0.45}
            className={`absolute inset-x-0 top-0 flex h-14 items-center gap-3 rounded-2xl border px-4 ${
              index === 0
                ? 'border-primary-pill bg-primary-soft'
                : 'border-line bg-surface-raised'
            }`}
          >
            <Reveal
              as="span"
              effect="pop"
              delay={1.2 + index * 0.08}
              className={`flex size-7 shrink-0 items-center justify-center rounded-full text-caption font-extrabold ${
                index === 0
                  ? 'bg-primary text-on-primary'
                  : 'bg-primary-pill text-heading'
              }`}
            >
              {index + 1}
            </Reveal>
            <span className="flex-1 font-bold text-heading">{item.name}</span>
            <span className="text-caption text-ink-soft">{item.action}</span>
          </SlotItem>
        ))}
      </ol>
      <Reveal delay={1.5}>
        <AiNote title="이 순서인 이유">
          전자서명과 인증서는 공개키 암호를 바탕으로 동작해요. 공개키 암호를
          먼저 다지면 뒤의 두 개념도 함께 이해하기 쉬워요.
        </AiNote>
      </Reveal>
    </div>
  );
}

export const STORY_STEPS: {
  title: string;
  // 왼쪽 큰 제목. 줄바꿈 자리는 \n
  headline: string;
  body: string;
  // 창 틀 주소줄에 보여 줄 실제 화면 경로
  path: string;
  Scene: () => ReactNode;
}[] = [
  {
    title: '대화 가져오기',
    headline: '흩어진 AI 대화를\n과목별로 모아요',
    body: 'ChatGPT·Claude 내보내기 파일을 올리면 내 브라우저에서 읽고, 과목 키워드에 맞는 학습 대화만 골라요.',
    path: '/analysis/import',
    Scene: ImportScene,
  },
  {
    title: '개인정보 가리기',
    headline: '보내기 전에\n개인정보부터 가려요',
    body: '이메일·전화번호 같은 정보는 브라우저에서 가린 뒤, 내가 선택한 대화만 서버로 보내요.',
    path: '/analysis/import',
    Scene: MaskScene,
  },
  {
    title: '개념별 강점·약점',
    headline: '개념마다 어디까지\n아는지 보여 줘요',
    body: '대화에서 다시 물어본 것과 퀴즈 결과를 정해진 규칙으로 계산해 개념별 점수를 매겨요.',
    path: '/analysis',
    Scene: MasteryScene,
  },
  {
    title: '판단 근거',
    headline: '왜 그렇게 판단했는지\n함께 보여 줘요',
    body: '점수 옆에 어떤 대화와 퀴즈를 보고 판단했는지 날짜와 함께 남겨요. 설명은 AI 표시로 구분해요.',
    path: '/analysis',
    Scene: EvidenceScene,
  },
  {
    title: '맞춤 순서',
    headline: '보완할 개념부터\n공부 순서를 다시 짜요',
    body: '요약·퀴즈·챗봇으로 이어서 공부하고, 결과가 쌓이면 순서를 다시 계산해요.',
    path: '/curriculum',
    Scene: OrderScene,
  },
];
