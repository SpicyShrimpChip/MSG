import { AiBadge } from '@/components';
import type { ConceptResult } from '@/types';
import Panel from './Panel';
import { ComparisonBadge } from './UnderstandingBadge';

interface RecommendationPanelProps {
  // 추천 순서대로 정렬된 개념
  concepts: ConceptResult[];
  layoutReason?: string | null;
}

// 모델들이 권한 공부 방법 (처음 나온 것 하나). 없으면 null
function firstActivity(concept: ConceptResult): string | null {
  return (
    concept.judgments.find((item) => item.aiRecommendedActivity)
      ?.aiRecommendedActivity ?? null
  );
}

// 추천 학습 순서. 순서는 코드 규칙, 각 개념에 붙은 공부 방법만 AI 글이다
function RecommendationPanel({
  concepts,
  layoutReason,
}: RecommendationPanelProps) {
  // 이미 이해한 개념은 순서에서 뺀다
  const items = concepts.filter(
    (item) => item.comparison.status !== 'understood',
  );

  return (
    <Panel
      title="추천 학습 순서"
      description="순서는 정해진 규칙으로 정해요. 이해 부족, 의견이 갈린 개념, 근거 부족 순이에요."
      layoutReason={layoutReason}
    >
      {items.length === 0 ? (
        <p className="text-body text-ink-soft">
          모든 개념을 이해한 것으로 판단했어요. 퀴즈로 확인해 보세요.
        </p>
      ) : (
        <ol className="flex flex-col gap-2">
          {items.map((item, index) => {
            const activity = firstActivity(item);
            return (
              <li
                key={item.concept}
                className="flex gap-3 rounded-xl border border-line px-4 py-3"
              >
                <span
                  aria-hidden="true"
                  className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-caption font-extrabold text-on-primary"
                >
                  {index + 1}
                </span>
                <div className="flex min-w-0 flex-col gap-1.5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-bold text-heading">
                      <span className="sr-only">{index + 1}번째. </span>
                      {item.concept}
                    </span>
                    <ComparisonBadge
                      outcome={item.comparison.outcome}
                      status={item.comparison.status}
                    />
                  </div>
                  {activity ? (
                    <p className="flex flex-wrap items-center gap-1.5 text-caption text-ai">
                      <AiBadge />
                      {activity}
                    </p>
                  ) : item.comparison.status === 'insufficient_evidence' ? (
                    <p className="text-caption text-ink-soft">
                      판단할 대화가 적어요. 이 개념을 다룬 대화를 더 가져오면
                      판단할 수 있어요.
                    </p>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ol>
      )}
    </Panel>
  );
}

export default RecommendationPanel;
