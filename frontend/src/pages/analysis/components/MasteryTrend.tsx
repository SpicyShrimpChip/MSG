import type { MasteryRecord, MasteryStage } from '@/types';
import { MASTERY_LABEL, cn, describeMasteryChange, shortDate } from '@/utils';

// 점 그림에 보여 줄 최근 기록 수 (좁은 화면에서도 칸이 너무 좁아지지 않게)
const CHART_LIMIT = 6;

// 위에서부터 강점 → 근거 부족. 근거 부족은 이해 부족과 다른 모양(빈 점선 점)으로 그린다
const LANES: { stage: MasteryStage; text: string; dot: string }[] = [
  { stage: 'strong', text: 'text-strength', dot: 'bg-strength' },
  { stage: 'normal', text: 'text-primary', dot: 'bg-primary' },
  { stage: 'needs_work', text: 'text-weakness', dot: 'bg-weakness' },
  {
    stage: 'insufficient_evidence',
    text: 'text-ink-soft',
    dot: 'border-2 border-dashed border-ink-muted bg-surface-raised',
  },
];

const CHANGE_TEXT: Record<MasteryStage, string> = {
  strong: 'text-strength',
  normal: 'text-primary',
  needs_work: 'text-weakness',
  insufficient_evidence: 'text-ink-soft',
};

// 개념 하나의 숙련도 변화: 단계(행) × 기록한 날(열) 점 그림 + 기록 목록(최신이 위)
function MasteryTrend({ history }: { history: MasteryRecord[] }) {
  if (history.length === 0) return null;

  const shown = history.slice(-CHART_LIMIT);
  const changes = history
    .map((item, index) =>
      describeMasteryChange(index > 0 ? history[index - 1] : null, item),
    )
    .reverse();
  const summary = shown
    .map((item) => `${shortDate(item.recordedAt)} ${MASTERY_LABEL[item.level]}`)
    .join(', ');

  return (
    <div className="flex flex-col gap-3 border-t border-line pt-4">
      <div className="flex flex-col gap-1">
        <h3 className="font-extrabold text-heading">이 개념의 변화</h3>
        <p className="text-caption text-ink-muted">
          단계나 근거가 바뀔 때마다 그때 단계를 남겨요.
        </p>
      </div>

      {history.length > 1 ? (
        <div
          role="img"
          aria-label={`숙련도 변화: ${summary}`}
          className="grid overflow-hidden rounded-xl border border-line"
          style={{
            gridTemplateColumns: `5.5rem repeat(${shown.length}, minmax(0, 1fr))`,
          }}
        >
          {LANES.map((lane) => (
            <div key={lane.stage} className="contents">
              <div
                className={cn(
                  'flex h-10 items-center border-b border-line bg-surface-muted px-2.5 text-caption font-bold',
                  lane.text,
                )}
              >
                {MASTERY_LABEL[lane.stage]}
              </div>
              {shown.map((item, index) => (
                <div
                  key={`${lane.stage}-${item.recordedAt}`}
                  className="flex h-10 items-center justify-center border-b border-l border-line"
                >
                  {item.level === lane.stage && (
                    <span
                      className={cn(
                        'size-3.5 rounded-full',
                        lane.dot,
                        index === shown.length - 1 &&
                          'ring-4 ring-primary-pill',
                      )}
                    />
                  )}
                </div>
              ))}
            </div>
          ))}
          <div className="h-8" />
          {shown.map((item) => (
            <div
              key={`date-${item.recordedAt}`}
              className="flex h-8 items-center justify-center border-l border-line text-caption font-bold text-ink-soft"
            >
              {shortDate(item.recordedAt)}
            </div>
          ))}
        </div>
      ) : (
        <p className="text-body text-ink-muted">
          아직 기록이 하나라 변화는 다음 기록부터 보여 드려요.
        </p>
      )}

      <ol className="flex flex-col gap-2">
        {changes.map((item, index) => (
          <li
            key={`${history.length - index}`}
            className="grid grid-cols-[3rem_minmax(0,1fr)] gap-3 rounded-lg bg-surface-muted px-3 py-2.5"
          >
            <span className="text-caption font-bold text-ink-soft">
              {item.date}
            </span>
            <div className="flex flex-col gap-1">
              <p className={cn('text-body font-bold', CHANGE_TEXT[item.stage])}>
                {item.change}
              </p>
              <p className="text-caption text-ink-soft">
                {item.reasons.join(' · ')}
              </p>
            </div>
          </li>
        ))}
      </ol>

      <p className="rounded-lg border border-dashed border-line-strong px-3 py-2.5 text-caption leading-relaxed text-ink-soft">
        단계는 공부했다는 기록이 아니라 퀴즈·대화 근거로 계산한 값이에요. 산식
        버전이 바뀌면 그 앞뒤 기록은 바로 비교하기 어려워요.
      </p>
    </div>
  );
}

export default MasteryTrend;
