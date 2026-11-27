import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowLeftRight,
  CircleAlert,
  CircleCheck,
  CircleHelp,
  Contrast,
  Equal,
  Users,
  type LucideIcon,
} from 'lucide-react';
import { assessmentComparisonQuery, curriculumQuery } from '@/api';
import { ScoreBar } from '@/components';
import type { AnalysisRun, MasteryStage } from '@/types';
import {
  MASTERY_LABEL,
  cn,
  keywordQuizProgress,
  summarizeConcepts,
} from '@/utils';
import { deltaText, scorePercent, submitted } from '@/utils/assessment';
import Panel from './Panel';

// 아래쪽 자세한 영역으로 내려가는 목표 (DashboardPage가 그 영역에 붙인다)
export const ASSESSMENT_ANCHOR = 'dashboard-assessment';
export const PROGRESS_ANCHOR = 'dashboard-progress';

// 막대·범례 순서와 모양. 숙련도는 코드 계산 값이라 AI 보라색을 쓰지 않는다.
// 근거 부족은 이해 부족과 다른 모양(점선 칸, 물음표)으로 그린다
const STAGES: {
  level: MasteryStage;
  bar: string;
  text: string;
  icon: LucideIcon;
}[] = [
  {
    level: 'strong',
    bar: 'bg-strength',
    text: 'text-strength',
    icon: CircleCheck,
  },
  { level: 'normal', bar: 'bg-primary', text: 'text-primary', icon: Contrast },
  {
    level: 'needs_work',
    bar: 'bg-weakness-bar',
    text: 'text-weakness',
    icon: CircleAlert,
  },
  {
    level: 'insufficient_evidence',
    bar: 'border border-dashed border-line-strong bg-surface-raised',
    text: 'text-ink-soft',
    icon: CircleHelp,
  },
];

// 모델 의견: 퍼센트·정확도가 아니라 "같은 판단을 한 모델 수"로만 나눈다
const OPINIONS: {
  key: 'unanimous' | 'majority' | 'disagreement';
  label: string;
  icon: LucideIcon;
}[] = [
  { key: 'unanimous', label: '모두 같음', icon: Equal },
  { key: 'majority', label: '다수가 같음', icon: Users },
  { key: 'disagreement', label: '의견이 갈림', icon: ArrowLeftRight },
];

function Tile({
  label,
  children,
  more,
}: {
  label: string;
  children: ReactNode;
  // 오른쪽 위 링크 (자세히 보기 등)
  more?: ReactNode;
}) {
  return (
    <div className="flex min-w-0 flex-col gap-2.5 rounded-xl bg-surface-muted p-4">
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-caption font-semibold text-ink-soft">{label}</h3>
        {more}
      </div>
      {children}
    </div>
  );
}

const MORE_LINK = 'text-caption font-semibold text-primary hover:underline';

// 사전→사후평가 (과목 단위, 아래 자세한 비교 영역과 같은 조회라 다시 부르지 않는다)
function AssessmentTile({ courseId }: { courseId: number }) {
  const { data, isPending, isError } = useQuery(
    assessmentComparisonQuery(courseId),
  );
  const more = (
    <a href={`#${ASSESSMENT_ANCHOR}`} className={MORE_LINK}>
      자세히
    </a>
  );
  if (isPending || isError || !data.pre) {
    return (
      <Tile label="사전 → 사후평가">
        <p className="text-body text-ink-soft">
          {isPending
            ? '불러오는 중이에요'
            : isError
              ? '평가 결과를 불러오지 못했어요'
              : '아직 사전평가가 없어요'}
        </p>
        {!isPending && !isError && (
          <Link to="/assessments" className={MORE_LINK}>
            사전평가 보러 가기
          </Link>
        )}
      </Tile>
    );
  }
  const pre = submitted(data.pre)
    ? scorePercent(data.pre.score, data.pre.maxScore)
    : null;
  const post =
    data.post && submitted(data.post)
      ? scorePercent(data.post.score, data.post.maxScore)
      : null;
  const improved = data.concepts.filter((c) => c.status === 'improved').length;
  return (
    <Tile label="사전 → 사후평가" more={more}>
      <p className="text-2xl font-semibold text-heading">
        {pre === null ? '채점 전' : `${pre}%`}
        <span className="mx-1.5 text-ink-muted" aria-hidden="true">
          →
        </span>
        <span className="sr-only">에서 </span>
        {post === null ? '사후 전' : `${post}%`}
      </p>
      <p className="text-caption text-ink-soft">
        {data.overallDelta !== null && (
          <strong className="text-primary">
            {deltaText(data.overallDelta)}{' '}
          </strong>
        )}
        {post === null
          ? '공부한 뒤 사후평가로 비교해요'
          : `오른 개념 ${improved}개`}
      </p>
    </Tile>
  );
}

// 학습 진도 (커리큘럼 응답의 flow.progress, 아래 자세한 진도 영역과 같은 조회)
function ProgressTile({ courseId }: { courseId: number }) {
  const { data, isPending, isError } = useQuery(curriculumQuery(courseId));
  const progress = data?.flow.progress;
  const keyword = data ? keywordQuizProgress(data.flow.concepts) : null;
  // 자료 개념이 없고 과목 키워드만 있으면 퀴즈로 확인한 키워드 수를 센다
  if (progress && progress.studiableConcepts === 0 && keyword?.total) {
    if (data?.flow.preTest.status !== 'submitted' || keyword.percent === null) {
      return (
        <Tile label="학습 진도">
          <p className="text-body text-ink-soft">사전 테스트 뒤부터 세요</p>
        </Tile>
      );
    }
    return (
      <Tile
        label="학습 진도"
        more={
          <a href={`#${PROGRESS_ANCHOR}`} className={MORE_LINK}>
            자세히
          </a>
        }
      >
        <p className="text-2xl font-semibold text-heading">
          {keyword.checked}
          <span className="text-lg text-ink-muted"> / {keyword.total}개</span>
        </p>
        <ScoreBar value={keyword.percent} />
        <p className="text-caption text-ink-soft">
          퀴즈로 확인 {keyword.percent}% (과목 키워드)
        </p>
      </Tile>
    );
  }
  if (
    isPending ||
    isError ||
    !progress ||
    progress.studiableConcepts === 0 ||
    progress.percent === null
  ) {
    return (
      <Tile label="학습 진도">
        <p className="text-body text-ink-soft">
          {isPending
            ? '불러오는 중이에요'
            : isError
              ? '진도를 불러오지 못했어요'
              : '자료에서 핵심 개념을 뽑으면 진도를 세요'}
        </p>
      </Tile>
    );
  }
  return (
    <Tile
      label="학습 진도"
      more={
        <a href={`#${PROGRESS_ANCHOR}`} className={MORE_LINK}>
          자세히
        </a>
      }
    >
      <p className="text-2xl font-semibold text-heading">
        {progress.studiedConcepts}
        <span className="text-lg text-ink-muted">
          {' '}
          / {progress.studiableConcepts}개
        </span>
      </p>
      <ScoreBar value={progress.percent} />
      <p className="text-caption text-ink-soft">
        학습 완료 {progress.percent}%
      </p>
    </Tile>
  );
}

interface SummaryStripProps {
  run: AnalysisRun;
  layoutReason?: string | null;
}

// 대시보드 맨 위 요약 칸 4개. 모두 코드가 센 값이다(숙련도·모델 의견은 이번 분석, 평가·진도는 과목 기록)
function SummaryStrip({ run, layoutReason }: SummaryStripProps) {
  const overview = summarizeConcepts(run.concepts);
  return (
    <Panel
      title="한눈에 보기"
      description="모든 숫자는 정해진 규칙으로 센 값이에요."
      layoutReason={layoutReason}
    >
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Tile label={`개념별 숙련도 · ${overview.total}개`}>
          <div
            className="flex h-2.5 gap-0.5 overflow-hidden rounded-full"
            aria-hidden="true"
          >
            {STAGES.filter((stage) => overview.mastery[stage.level] > 0).map(
              (stage) => (
                <span
                  key={stage.level}
                  className={cn('h-full rounded-full', stage.bar)}
                  style={{ flexGrow: overview.mastery[stage.level] }}
                />
              ),
            )}
          </div>
          <ul className="grid grid-cols-2 gap-x-3 gap-y-1">
            {STAGES.map((stage) => {
              const Icon = stage.icon;
              return (
                <li
                  key={stage.level}
                  className="flex items-center justify-between gap-1 text-caption"
                >
                  <span
                    className={cn(
                      'inline-flex items-center gap-1 font-semibold',
                      stage.text,
                    )}
                  >
                    <Icon size={14} aria-hidden="true" />
                    {MASTERY_LABEL[stage.level]}
                  </span>
                  <span className="font-semibold text-heading">
                    {overview.mastery[stage.level]}
                  </span>
                </li>
              );
            })}
          </ul>
        </Tile>

        <Tile label="모델들의 의견">
          <ul className="flex flex-col gap-1.5">
            {OPINIONS.map((item) => {
              const Icon = item.icon;
              return (
                <li
                  key={item.key}
                  className="flex items-center justify-between gap-2 text-body"
                >
                  <span className="inline-flex items-center gap-1.5 text-heading">
                    <Icon
                      size={16}
                      aria-hidden="true"
                      className="text-primary"
                    />
                    {item.label}
                  </span>
                  <span className="font-semibold text-heading">
                    {overview.comparison[item.key]}개
                  </span>
                </li>
              );
            })}
          </ul>
          {overview.comparison.singleModel > 0 && (
            <p className="text-caption text-ink-muted">
              모델 하나만 판단해 비교하지 못한 개념{' '}
              {overview.comparison.singleModel}개
            </p>
          )}
          {overview.comparison.noResult > 0 && (
            <p className="text-caption text-ink-muted">
              판단한 모델이 없는 개념 {overview.comparison.noResult}개
            </p>
          )}
        </Tile>

        <AssessmentTile courseId={run.courseId} />
        <ProgressTile courseId={run.courseId} />
      </div>
    </Panel>
  );
}

export default SummaryStrip;
