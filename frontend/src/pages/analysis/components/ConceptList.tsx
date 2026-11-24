import { ChevronDown } from 'lucide-react';
import { AiBadge, Chip } from '@/components';
import type { ConceptResult, ModelRun } from '@/types';
import {
  FEEDBACK_LABEL,
  PROVIDER_LABEL,
  cn,
  describeComparison,
} from '@/utils';
import ConceptFeedbackBox from './ConceptFeedbackBox';
import ConceptJudgments from './ConceptJudgments';
import ConceptVerificationBox from './ConceptVerificationBox';
import MasteryEvidence from './MasteryEvidence';
import MasteryMeter from './MasteryMeter';
import Panel from './Panel';
import { ModelVerdictChip } from './UnderstandingBadge';

// 목록의 한 줄로 내려갈 때 쓰는 id (개념 이름에는 띄어쓰기·괄호가 있어 순번으로 만든다)
export const conceptRowId = (index: number) => `concept-row-${index + 1}`;

// 모델들이 권한 공부 방법 (처음 나온 것 하나). 없으면 null
function firstActivity(concept: ConceptResult): string | null {
  return (
    concept.judgments.find((item) => item.aiRecommendedActivity)
      ?.aiRecommendedActivity ?? null
  );
}

interface ConceptRowProps {
  index: number;
  concept: ConceptResult;
  models: ModelRun[];
  courseId: number;
  runId: number;
  open: boolean;
  onToggle: () => void;
}

// 개념 한 줄: 순서·이름·숙련도·모델마다의 판단. 누르면 아래로 근거와 모델별 판단이 펼쳐진다
function ConceptRow({
  index,
  concept,
  models,
  courseId,
  runId,
  open,
  onToggle,
}: ConceptRowProps) {
  const rowId = conceptRowId(index);
  const bodyId = `${rowId}-body`;
  const activity = firstActivity(concept);

  return (
    <li
      id={rowId}
      className={cn(
        'scroll-mt-28 rounded-2xl border bg-surface-raised transition-colors',
        open ? 'border-primary' : 'border-line',
      )}
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        aria-controls={bodyId}
        className="grid w-full grid-cols-[2rem_minmax(0,1fr)_1.25rem] items-center gap-x-3 gap-y-2.5 px-4 py-4 text-left lg:grid-cols-[2rem_minmax(0,1.3fr)_minmax(0,0.9fr)_minmax(0,2fr)_1.25rem] lg:px-5"
      >
        <span
          className="flex size-7 items-center justify-center rounded-full bg-primary text-caption font-extrabold text-on-primary"
          aria-hidden="true"
        >
          {index + 1}
        </span>
        <span className="flex min-w-0 flex-col gap-0.5">
          <span className="flex flex-wrap items-center gap-2">
            <span className="text-lg font-bold text-heading">
              <span className="sr-only">공부 순서 {index + 1}번째. </span>
              {concept.concept}
            </span>
            {concept.feedback && (
              <Chip>{FEEDBACK_LABEL[concept.feedback.feedback]}</Chip>
            )}
          </span>
          <span className="text-caption text-ink-soft">
            {describeComparison(concept.comparison)}
          </span>
        </span>
        <ChevronDown
          size={20}
          aria-hidden="true"
          className={cn(
            'col-start-3 row-start-1 text-ink-soft transition-transform lg:col-start-5',
            open && 'rotate-180',
          )}
        />
        {/* 좁은 화면에서는 둘째 줄로 내려간다 */}
        <span className="col-span-2 col-start-2 lg:col-span-1 lg:col-start-3 lg:row-start-1">
          {concept.mastery ? (
            <MasteryMeter level={concept.mastery.level} />
          ) : (
            <span className="text-caption text-ink-muted">숙련도 계산 전</span>
          )}
        </span>
        <span className="col-span-2 col-start-2 flex flex-wrap gap-1.5 lg:col-span-1 lg:col-start-4 lg:row-start-1">
          {models.map((model) => {
            const judgment = concept.judgments.find(
              (item) => item.provider === model.provider,
            );
            return (
              <ModelVerdictChip
                key={model.provider}
                provider={PROVIDER_LABEL[model.provider]}
                status={judgment?.status ?? null}
                missingLabel={
                  model.status === 'not_configured' ? '연결 안 됨' : undefined
                }
              />
            );
          })}
        </span>
      </button>

      {open && (
        <div
          id={bodyId}
          className="grid gap-6 border-t border-line px-4 py-5 lg:grid-cols-2 lg:px-5"
        >
          {/* 왼쪽: 코드가 계산한 값(숙련도 근거·변화) */}
          <div className="flex min-w-0 flex-col gap-4">
            <ConceptVerificationBox courseId={courseId} concept={concept} />
            {concept.mastery ? (
              <MasteryEvidence
                courseId={courseId}
                concept={concept.concept}
                mastery={concept.mastery}
              />
            ) : (
              <p className="text-body text-ink-soft">
                아직 이 개념의 숙련도를 계산하지 않았어요. 퀴즈를 풀거나 대화를
                분석하면 단계가 나와요.
              </p>
            )}
            <ConceptFeedbackBox
              courseId={courseId}
              runId={runId}
              concept={concept}
            />
          </div>
          {/* 오른쪽: 모델들의 판단과 이유(AI) */}
          <div className="flex min-w-0 flex-col gap-4">
            {activity && (
              <p className="flex flex-wrap items-center gap-1.5 rounded-xl border border-ai-border bg-ai-soft px-4 py-3 text-body text-ai">
                <AiBadge />
                추천 공부 방법: {activity}
              </p>
            )}
            <ConceptJudgments concept={concept} models={models} />
          </div>
        </div>
      )}
    </li>
  );
}

interface ConceptListProps {
  // 추천 학습 순서대로 정렬된 개념
  concepts: ConceptResult[];
  models: ModelRun[];
  courseId: number;
  runId: number;
  openConcepts: ReadonlySet<string>;
  onToggle: (concept: string) => void;
  layoutReason?: string | null;
}

// 공부할 순서대로 본 개념 목록. 예전의 개념 표·개념 상세·추천 학습 순서를 한 목록으로 합쳤다.
// 순서는 서버 규칙(studyOrder), 숙련도는 코드 계산(파랑), 모델별 이유·추천 공부 방법만 AI(보라)
function ConceptList({
  concepts,
  models,
  courseId,
  runId,
  openConcepts,
  onToggle,
  layoutReason,
}: ConceptListProps) {
  return (
    <Panel
      title="공부할 순서대로 본 개념"
      description="순서는 정해진 규칙으로 정해요: 더 공부하겠다고 고른 개념, 이해 부족, 부분 이해, 의견이 갈린 개념, 근거 부족, 이해 순이에요. 칩은 모델마다의 판단이고, 숙련도는 퀴즈와 대화 판단을 규칙으로 계산한 단계예요. 줄을 누르면 근거가 펼쳐져요."
      layoutReason={layoutReason}
    >
      {concepts.length === 0 ? (
        <p className="text-body text-ink-soft">
          이 과목 키워드로 판단한 개념이 없어요.
        </p>
      ) : (
        <ol className="flex flex-col gap-3">
          {concepts.map((concept, index) => (
            <ConceptRow
              key={concept.concept}
              index={index}
              concept={concept}
              models={models}
              courseId={courseId}
              runId={runId}
              open={openConcepts.has(concept.concept)}
              onToggle={() => onToggle(concept.concept)}
            />
          ))}
        </ol>
      )}
    </Panel>
  );
}

export default ConceptList;
