import type { ReactNode } from 'react';
import { AiNote } from '@/components';
import type {
  ConceptJudgment,
  ConceptResult,
  JudgmentEvidence,
  ModelRun,
} from '@/types';
import {
  PROVIDER_LABEL,
  UNDERSTANDING_LABEL,
  describeComparison,
} from '@/utils';
import Panel from './Panel';
import { UnderstandingBadge } from './UnderstandingBadge';

interface ConceptDetailPanelProps {
  concept: ConceptResult | null;
  models: ModelRun[];
  layoutReason?: string | null;
  // 판단 아래에 붙일 내용 (내 생각 남기기)
  children?: ReactNode;
}

const EVIDENCE_KIND_LABEL: Record<JudgmentEvidence['kind'], string> = {
  user_explanation: '내 설명',
  user_solution: '내 풀이',
  user_question: '내 질문',
  ai_explanation: 'AI 답변',
};

function JudgmentCard({ judgment }: { judgment: ConceptJudgment }) {
  return (
    <li className="flex flex-col gap-3 rounded-xl border border-line p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="font-extrabold text-heading">
          {PROVIDER_LABEL[judgment.provider]}
          {judgment.model && (
            <span className="ml-1.5 text-caption font-medium text-ink-muted">
              {judgment.model}
            </span>
          )}
        </p>
        <UnderstandingBadge status={judgment.status} />
      </div>

      {/* 서버가 근거를 확인하고 판단을 바꾼 경우: 코드가 정한 값이라 AI 상자가 아닌 회색 안내로 */}
      {judgment.adjustedReason && (
        <p className="rounded-lg bg-surface-muted px-3 py-2 text-caption text-ink-soft">
          모델은 '{UNDERSTANDING_LABEL[judgment.reportedStatus]}'로 냈지만,{' '}
          {judgment.adjustedReason}
        </p>
      )}

      {judgment.aiReason && (
        <AiNote title="이렇게 판단한 이유">{judgment.aiReason}</AiNote>
      )}
      {judgment.aiNeedsCheck && (
        <AiNote title="확인해 볼 점">{judgment.aiNeedsCheck}</AiNote>
      )}

      {judgment.evidence.length > 0 ? (
        <div className="flex flex-col gap-2">
          <p className="text-caption font-bold text-ink-soft">
            근거 메시지 {judgment.evidence.length}개
          </p>
          <ul className="flex flex-col gap-1.5">
            {judgment.evidence.map((item) => (
              <li
                key={item.messageId}
                className="flex flex-col gap-1 rounded-lg bg-surface-muted px-3 py-2.5"
              >
                <span className="text-caption font-bold text-ink-soft">
                  {EVIDENCE_KIND_LABEL[item.kind]} · {item.conversationTitle}
                </span>
                <span className="text-body leading-relaxed break-words text-ink">
                  "{item.excerpt}"
                </span>
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <p className="text-caption text-ink-muted">
          근거로 든 메시지가 없어요.
        </p>
      )}
    </li>
  );
}

// 고른 개념에 대한 모델별 판단과 근거. 이번 분석에 참여하지 않은 모델도 숨기지 않는다
function ConceptDetailPanel({
  concept,
  models,
  layoutReason,
  children,
}: ConceptDetailPanelProps) {
  if (!concept) {
    return (
      <Panel title="모델별 판단" layoutReason={layoutReason}>
        <p className="text-body text-ink-soft">
          왼쪽 표에서 개념을 고르면 모델마다 어떻게 판단했는지 보여 드려요.
        </p>
      </Panel>
    );
  }

  const judged = new Set(concept.judgments.map((item) => item.provider));
  const absent = models.filter((model) => !judged.has(model.provider));

  return (
    <Panel
      title={`${concept.concept} · 모델별 판단`}
      description={describeComparison(concept.comparison)}
      layoutReason={layoutReason}
    >
      <ul className="flex flex-col gap-3">
        {concept.judgments.map((judgment) => (
          <JudgmentCard key={judgment.provider} judgment={judgment} />
        ))}
        {absent.map((model) => (
          <li
            key={model.provider}
            className="flex flex-wrap items-center justify-between gap-2 rounded-xl border border-dashed border-line-strong px-4 py-3 text-body text-ink-muted"
          >
            <span className="font-bold">{PROVIDER_LABEL[model.provider]}</span>
            <span>
              {model.status === 'not_configured'
                ? '연결 안 됨 · 이번 분석에 참여하지 않았어요'
                : '이 개념은 판단하지 않았어요'}
            </span>
          </li>
        ))}
      </ul>
      {children}
    </Panel>
  );
}

export default ConceptDetailPanel;
