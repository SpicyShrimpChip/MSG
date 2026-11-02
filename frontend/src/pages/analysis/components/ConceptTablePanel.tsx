import type { ConceptResult } from '@/types';
import { FEEDBACK_LABEL, cn, describeComparison } from '@/utils';
import Panel from './Panel';
import { ComparisonBadge } from './UnderstandingBadge';

interface ConceptTablePanelProps {
  concepts: ConceptResult[];
  selected: string | null;
  onSelect: (concept: string) => void;
  layoutReason?: string | null;
}

// 개념마다 모델들의 판단을 한 줄로. 줄을 누르면 옆 영역에 모델별 판단과 근거가 나온다
function ConceptTablePanel({
  concepts,
  selected,
  onSelect,
  layoutReason,
}: ConceptTablePanelProps) {
  return (
    <Panel
      title="개념별 판단"
      description="판단 개수는 정확도가 아니라 몇 개 모델이 같은 판단을 했는지예요."
      layoutReason={layoutReason}
    >
      <div className="overflow-x-auto">
        <table className="w-full min-w-130 border-collapse text-body">
          <thead>
            <tr className="bg-surface-muted text-caption text-ink-soft">
              <th scope="col" className="px-3 py-2.5 text-left font-bold">
                개념
              </th>
              <th scope="col" className="px-3 py-2.5 text-left font-bold">
                모델들의 판단
              </th>
              <th scope="col" className="px-3 py-2.5 text-left font-bold">
                판단한 모델
              </th>
              <th scope="col" className="px-3 py-2.5 text-left font-bold">
                내 선택
              </th>
            </tr>
          </thead>
          <tbody>
            {concepts.map((item) => {
              const active = item.concept === selected;
              return (
                <tr
                  key={item.concept}
                  className={cn(
                    'border-b border-line last:border-b-0',
                    active && 'bg-primary-soft',
                  )}
                >
                  <th scope="row" className="p-0 text-left font-bold">
                    <button
                      type="button"
                      onClick={() => onSelect(item.concept)}
                      aria-pressed={active}
                      className="w-full px-3 py-3 text-left text-heading hover:text-primary"
                    >
                      {item.concept}
                    </button>
                  </th>
                  <td className="px-3 py-3">
                    <ComparisonBadge
                      outcome={item.comparison.outcome}
                      status={item.comparison.status}
                    />
                  </td>
                  <td className="px-3 py-3 text-caption text-ink-soft">
                    {describeComparison(item.comparison)}
                  </td>
                  <td className="px-3 py-3 text-caption font-bold text-primary">
                    {item.feedback ? (
                      FEEDBACK_LABEL[item.feedback.feedback]
                    ) : (
                      <span className="font-normal text-ink-muted">-</span>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </Panel>
  );
}

export default ConceptTablePanel;
