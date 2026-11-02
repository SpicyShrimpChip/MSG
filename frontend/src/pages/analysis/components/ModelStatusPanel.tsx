import { CircleAlert, CircleCheck, Unplug } from 'lucide-react';
import type { ModelRun } from '@/types';
import { PROVIDER_LABEL } from '@/utils';
import Panel from './Panel';

interface ModelStatusPanelProps {
  models: ModelRun[];
  layoutReason?: string | null;
}

function StatusText({ model }: { model: ModelRun }) {
  switch (model.status) {
    case 'succeeded':
      return (
        <span className="inline-flex items-center gap-1 font-bold text-strength">
          <CircleCheck size={16} aria-hidden="true" />
          판단함 · 개념 {model.judgedConceptCount}개
        </span>
      );
    case 'not_configured':
      return (
        <span className="inline-flex items-center gap-1 font-bold text-ink-muted">
          <Unplug size={16} aria-hidden="true" />
          연결 안 됨
        </span>
      );
    default:
      // 실패·형식이 맞지 않는 답변: 오류 빨강(danger)은 이런 실패에만 쓴다
      return (
        <span className="inline-flex items-center gap-1 font-bold text-danger">
          <CircleAlert size={16} aria-hidden="true" />
          {model.status === 'failed'
            ? '판단하지 못했어요'
            : '답변을 읽지 못했어요'}
        </span>
      );
  }
}

// 이번 분석에 어떤 모델이 참여했는지. 연결되지 않은 모델을 연결된 척하지 않는다
function ModelStatusPanel({ models, layoutReason }: ModelStatusPanelProps) {
  return (
    <Panel title="모델 연결 상태" layoutReason={layoutReason}>
      <ul className="flex flex-col gap-2">
        {models.map((model) => (
          <li
            key={model.provider}
            className="flex flex-col gap-1 rounded-xl bg-surface-muted px-4 py-3"
          >
            <div className="flex flex-wrap items-center justify-between gap-2 text-body">
              <span className="font-bold text-heading">
                {PROVIDER_LABEL[model.provider]}
                {model.model && (
                  <span className="ml-1.5 text-caption font-medium text-ink-muted">
                    {model.model}
                  </span>
                )}
              </span>
              <StatusText model={model} />
            </div>
            {model.status !== 'succeeded' &&
              model.status !== 'not_configured' &&
              model.message && (
                <p className="text-caption text-ink-soft">{model.message}</p>
              )}
            {model.droppedEvidenceCount > 0 && (
              <p className="text-caption text-ink-soft">
                확인되지 않은 근거 {model.droppedEvidenceCount}개는 뺐어요.
              </p>
            )}
          </li>
        ))}
      </ul>
      <p className="text-caption leading-relaxed text-ink-muted">
        모델마다 같은 기준으로 따로 판단하고, 다른 모델의 답은 보지 않아요.
      </p>
    </Panel>
  );
}

export default ModelStatusPanel;
