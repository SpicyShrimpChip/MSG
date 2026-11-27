import { useState } from 'react';
import { useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import { FileWarning, ShieldCheck, Sparkles } from 'lucide-react';
import {
  MATERIALS_QUERY_KEY,
  generateStudySummary,
  getStudySummary,
} from '@/api';
import {
  AiBadge,
  AiWaitingStatus,
  Card,
  Chip,
  Notice,
  SourceQuote,
  VerificationBadge,
  ToggleChip,
} from '@/components';
import { QUIZ_MODES } from '@/constants';
import { useMe, useQuizAiStatus, useSingleFlightMutation } from '@/hooks';
import { useMockData } from '@/store';
import type {
  StudySummary,
  StudySummaryItem,
  StudySummaryPurpose,
} from '@/types';
import {
  SOURCE_CHECK_LABEL,
  aiQuizBlockedReason,
  cn,
  formatDate,
  materialContentBlockedReason,
} from '@/utils';
import MaterialAiSendCard from './MaterialAiSendCard';

// 항목 하나: AI가 쓴 글 + 원문 근거 상태. 글은 모두 AI가 썼으므로 AI 표시를 붙이고(서버가 확인한 것은 인용뿐),
// 자료에 없는 AI 보충 설명은 보라색 상자로 따로 보여 준다
function SummaryItemView({ item }: { item: StudySummaryItem }) {
  const supplement = item.basis === 'supplement';
  return (
    <li
      className={cn(
        'flex flex-col gap-2 rounded-xl border p-3',
        supplement ? 'border-ai-border bg-ai-soft' : 'border-line',
      )}
    >
      <div className="flex flex-wrap items-center gap-2">
        {item.name && <p className="font-semibold text-heading">{item.name}</p>}
        {supplement ? (
          <span className="inline-flex items-center gap-1 text-caption font-semibold text-ai">
            <AiBadge />
            자료에 없는 AI 보충 설명
          </span>
        ) : (
          <>
            <span className="inline-flex items-center gap-1 text-caption font-semibold text-ai">
              <AiBadge />
              AI 요약
            </span>
            {item.check && (
              <VerificationBadge
                status={item.check}
                label={SOURCE_CHECK_LABEL[item.check]}
              />
            )}
          </>
        )}
      </div>
      <p className="text-body leading-relaxed text-ink">{item.text}</p>
      {!supplement && item.quote && (
        <SourceQuote quote={item.quote} location={item.location} />
      )}
      {!supplement && !item.quote && (
        <p className="text-caption text-ink-soft">
          {item.check === 'failed'
            ? 'AI가 든 근거 문장을 자료에서 찾지 못했어요. 자료를 직접 확인해 주세요.'
            : item.check === 'partial'
              ? `AI가 든 근거 문장이 자료와 일부만 일치해요. 자료의 ${item.location ?? '해당 부분'}을 직접 확인해 주세요.`
              : 'AI가 근거 문장을 들지 않아 확인하지 못했어요.'}
        </p>
      )}
    </li>
  );
}

function Section({
  title,
  items,
}: {
  title: string;
  items: StudySummaryItem[];
}) {
  if (items.length === 0) return null;
  return (
    <section className="flex flex-col gap-2">
      <h4 className="text-body font-semibold text-heading">{title}</h4>
      <ul className="flex flex-col gap-2">
        {items.map((item) => (
          <SummaryItemView
            key={`${item.name ?? ''}:${item.text}`}
            item={item}
          />
        ))}
      </ul>
    </section>
  );
}

function SummaryView({ summary }: { summary: StudySummary }) {
  const content = summary.content;
  const verification = summary.verification;
  if (!content || !verification) return null;
  return (
    <div className="flex flex-col gap-5">
      <Card className="flex flex-col gap-3 p-5">
        <div className="flex flex-wrap items-center gap-2">
          <ShieldCheck size={18} className="text-primary" aria-hidden="true" />
          <p className="text-body font-semibold text-heading">원문 근거 확인</p>
          <VerificationBadge status={verification.status} />
        </div>
        <div className="flex flex-wrap gap-2">
          <Chip tone="primary">원문과 일치 {verification.verified}</Chip>
          <Chip tone="primary">일부 일치 {verification.partial}</Chip>
          <Chip tone="primary">찾지 못함 {verification.failed}</Chip>
          <Chip tone="primary">인용 없음 {verification.insufficient}</Chip>
          <Chip>AI 보충 설명 {verification.supplement}</Chip>
        </div>
        <p className="text-caption text-ink-soft">
          서버가 항목마다 AI가 든 인용문이 자료 원문에 그대로 있는지 확인했어요.
          인용이 원문에 있다는 것만 확인한 것이고, 요약 글 전체가 사실이라는
          뜻은 아니에요.
        </p>
        <p className="text-caption text-ink-muted">
          {summary.generatedBy?.provider.toUpperCase()} ·{' '}
          {summary.promptVersion} ·{' '}
          {summary.createdAt ? formatDate(summary.createdAt) : ''} · 보낸 자료{' '}
          {summary.sourceScope.length > 0
            ? summary.sourceScope.join(', ')
            : '위치 표시 없음'}
          {summary.sourceTruncated && ' (자료가 길어 일부만 보냈어요)'}
          {summary.reused && ' · 저장한 요약을 다시 보여 드려요'}
        </p>
      </Card>

      {summary.stale && (
        <Notice icon={FileWarning} iconClassName="text-weakness">
          {summary.staleReason}
        </Notice>
      )}

      <Section title="자료 전체 개요" items={[content.overview]} />
      <Section title="핵심 개념과 정의" items={content.concepts} />
      <Section title="중요한 원리" items={content.principles} />
      <Section title="개념 사이 관계" items={content.relations} />
      <Section title="이해를 돕는 예시" items={content.examples} />
      <Section title="혼동하기 쉬운 점" items={content.confusions} />
      <Section title="핵심 정리" items={content.keyPoints} />
      {content.selfCheck.length > 0 && (
        <section className="flex flex-col gap-2">
          <h4 className="text-body font-semibold text-heading">
            스스로 확인할 질문
          </h4>
          <ol className="flex list-decimal flex-col gap-2 pl-5 text-body text-ink">
            {content.selfCheck.map((item) => (
              <li key={item.question}>
                {item.question}
                {item.hint && (
                  <p className="text-caption text-ink-soft">
                    힌트: {item.hint}
                  </p>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}
      {summary.sources.length > 0 && (
        <section className="flex flex-col gap-2">
          <h4 className="text-body font-semibold text-heading">원문 출처</h4>
          <div className="flex flex-wrap gap-2">
            {summary.sources.map((source) => (
              <Chip key={source.location}>
                {source.location} · 인용 {source.count}개
              </Chip>
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

// 자료 요약본: 학습 목적별로 저장한 요약을 보여 주고, 없거나 바뀌었으면 만든다.
// 만들기는 자료 본문(서버가 고른 부분)을 외부 AI로 보내므로 운영 설정·AI 퀴즈 처리 동의·이번 요청의 확인이 모두 있어야 한다
function StudySummaryPanel({
  materialId,
  fileName,
}: {
  materialId: number;
  fileName: string;
}) {
  const mockData = useMockData();
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const { data: ai } = useQuizAiStatus();
  const [purpose, setPurpose] = useState<StudySummaryPurpose>('exam_prep');
  const [confirmed, setConfirmed] = useState(false);
  const queryKey = [...MATERIALS_QUERY_KEY, materialId, 'summary', purpose];
  const { data: summary } = useSuspenseQuery({
    queryKey,
    queryFn: () => getStudySummary(materialId, purpose),
  });

  const mutation = useSingleFlightMutation({
    mutationFn: (regenerate: boolean) =>
      generateStudySummary(materialId, {
        purpose,
        materialContentConsent: true,
        regenerate,
      }),
    onSuccess: (next) => {
      queryClient.setQueryData(queryKey, next);
      setConfirmed(false);
    },
  });

  const blockedReason =
    materialContentBlockedReason(ai, fileName) ??
    aiQuizBlockedReason(ai, me?.aiQuizConsent, '요약을 만들');

  const ready = summary.status === 'ready';

  return (
    <div className="flex flex-col gap-4">
      <div
        role="radiogroup"
        aria-label="요약 방향(학습 목적)"
        className="flex flex-wrap gap-2"
      >
        {QUIZ_MODES.map((mode) => (
          <ToggleChip
            key={mode.value}
            role="radio"
            on={purpose === mode.value}
            onClick={() => {
              setPurpose(mode.value);
              setConfirmed(false);
              mutation.reset();
            }}
          >
            {mode.label}
          </ToggleChip>
        ))}
      </div>
      <p className="text-caption text-ink-soft">
        {purpose === 'exam_prep' &&
          '시험 범위의 핵심 개념·정의·차이를 앞세워 정리해요.'}
        {purpose === 'self_development' &&
          '기초 설명, 개념 사이 연관, 이해를 돕는 예시를 앞세워 정리해요.'}
        {purpose === 'work' &&
          '실무에서의 의미, 문제 해결 과정, 적용 방향을 앞세워 정리해요.'}
      </p>

      {/* 목적을 바꾸면 새로 그려 다른 목적의 ready를 완료로 읽지 않게 한다 */}
      <AiWaitingStatus
        key={purpose}
        waiting={summary.status === 'running'}
        failed={summary.status !== 'ready'}
        doneText="요약을 다 만들었어요."
      >
        이 자료의 요약을 만들고 있어요.
      </AiWaitingStatus>
      {ready ? (
        <SummaryView summary={summary} />
      ) : summary.status === 'running' ? null : (
        <Notice icon={Sparkles} iconClassName="text-primary">
          아직 이 목적으로 만든 요약이 없어요. 아래에서 만들면 항목마다 자료
          원문 근거를 확인해 보여 드려요.
        </Notice>
      )}

      <MaterialAiSendCard
        title={ready ? '요약 다시 만들기' : '요약 만들기'}
        notice={
          <>
            자료가 짧으면 전체를, 길면 핵심 개념이 나오는 부분만 골라 최대
            12,000자를 외부 생성형 AI로 보내요. 이메일·전화번호 같은 개인정보가
            보이면 보내지 않아요. 같은 파일·같은 목적의 요약은 저장해 두고 다시
            써요.
          </>
        }
        blockedReason={blockedReason}
        confirmed={confirmed}
        onConfirmedChange={setConfirmed}
        confirmLabel={
          <>
            이 자료의 본문을 외부 생성형 AI로 보내는 것을 확인했어요. 본인이
            이용 권한이 있는 자료예요.
          </>
        }
        error={mutation.error}
        pending={mutation.isPending}
        failed={mutation.isError}
        pendingText="AI가 요약을 쓰고 서버가 원문 근거를 확인하는 중이에요. 최대 1분 40초 걸릴 수 있어요."
        doneText="요약을 다 만들었어요."
        // 목적 버튼은 요청을 reset해 isPending을 끄므로, 목적마다 대기 알림을 새로 그려 끝난 것으로 읽지 않게 한다
        waitingKey={purpose}
        submitLabel={ready && !summary.stale ? '새로 만들기' : '요약 만들기'}
        onSubmit={() => mutation.mutate(ready && !summary.stale)}
        mockNote={
          mockData
            ? '목업에서는 AI를 부르지 않아요. 예시 요약은 미리 써 둔 예시예요.'
            : null
        }
      />
    </div>
  );
}

export default StudySummaryPanel;
