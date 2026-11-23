import { useMemo, useState, type ReactNode } from 'react';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { ChartColumn, Download } from 'lucide-react';
import * as m from 'motion/react-m';
import {
  ApiError,
  analysisLatestKey,
  getLatestAnalysis,
  startAnalysis,
} from '@/api';
import { useMockData } from '@/store';
import { FADE_UP } from '@/constants';
import {
  AiLegend,
  AiWaiting,
  AsyncBoundary,
  Button,
  EmptyState,
  SelectInput,
  SubPage,
} from '@/components';
import { useCourses, useProviders } from '@/hooks';
import type { AnalysisRun, Course, DashboardComponentId } from '@/types';
import {
  DEFAULT_LAYOUT,
  isNoResponseError,
  parseDashboardLayout,
  sortByStudyOrder,
} from '@/utils';
import AnalysisSummaryPanel from './components/AnalysisSummaryPanel';
import ConceptDetailPanel from './components/ConceptDetailPanel';
import ConceptFeedbackBox from './components/ConceptFeedbackBox';
import ConceptTablePanel from './components/ConceptTablePanel';
import AssessmentComparisonPanel from './components/AssessmentComparisonPanel';
import StudyProgressPanel from './components/StudyProgressPanel';
import LayoutSummary from './components/LayoutSummary';
import ModelStatusPanel from './components/ModelStatusPanel';
import RecommendationPanel from './components/RecommendationPanel';

// 분석 결과를 배치대로 그린다. 배치 JSON은 검증을 통과할 때만 쓰고, 아니면 기본 배치
function AnalysisResult({ run }: { run: AnalysisRun }) {
  // 처음엔 추천 순서 첫 개념을 자세히 보여 준다. 피드백으로 순서가 바뀌어도 보던 개념은 그대로 둔다
  const [selected, setSelected] = useState<string | null>(
    () => sortByStudyOrder(run.concepts)[0]?.concept ?? null,
  );
  // AI 배치 대신 기본 배치로 보기 (사용자가 고른 경우)
  const [usingDefault, setUsingDefault] = useState(false);

  const aiLayout = useMemo(
    () => parseDashboardLayout(run.layout),
    [run.layout],
  );
  const layout = usingDefault ? DEFAULT_LAYOUT : aiLayout;
  const concepts = useMemo(
    () => sortByStudyOrder(run.concepts),
    [run.concepts],
  );
  const current =
    concepts.find((item) => item.concept === selected) ?? concepts[0] ?? null;

  // 영역 id → 화면. 배치 JSON에는 이 id만 올 수 있다
  const renderComponent = (
    id: DashboardComponentId,
    reason: string | null,
  ): ReactNode => {
    switch (id) {
      case 'summary':
        return <AnalysisSummaryPanel run={run} layoutReason={reason} />;
      case 'conceptTable':
        return (
          <ConceptTablePanel
            concepts={concepts}
            selected={current?.concept ?? null}
            onSelect={setSelected}
            layoutReason={reason}
          />
        );
      case 'conceptDetail':
        return (
          <ConceptDetailPanel
            courseId={run.courseId}
            concept={current}
            models={run.models}
            layoutReason={reason}
          >
            {current && (
              <ConceptFeedbackBox
                courseId={run.courseId}
                runId={run.id}
                concept={current}
              />
            )}
          </ConceptDetailPanel>
        );
      case 'models':
        return <ModelStatusPanel models={run.models} layoutReason={reason} />;
      case 'recommendation':
        return (
          <RecommendationPanel concepts={concepts} layoutReason={reason} />
        );
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <LayoutSummary
        layout={aiLayout}
        usingDefault={usingDefault}
        onToggleDefault={() => setUsingDefault((value) => !value)}
      />
      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {layout.items.map((item, index) => (
          // 영역이 위에서부터 차례로 나타난다
          <m.div
            key={item.component}
            initial={FADE_UP.initial}
            animate={FADE_UP.animate}
            transition={{ ...FADE_UP.transition, delay: index * 0.06 }}
            className={item.size === 'wide' ? 'lg:col-span-2' : undefined}
          >
            {renderComponent(item.component, item.aiReason)}
          </m.div>
        ))}
      </div>
    </div>
  );
}

// 시간 초과·409 뒤 최근 분석을 다시 확인하는 간격과 그만둘 때까지의 시간 (서버 분석 예산 100초보다 넉넉히)
const WAIT_POLL_MS = 10_000;
const WAIT_LIMIT_MS = 5 * 60_000;

// 과목 하나의 최근 분석과 분석 시작
function CourseAnalysis({ course }: { course: Course }) {
  const mockData = useMockData();
  const queryClient = useQueryClient();
  // 기다리기를 멈췄거나(시간 초과) 이미 분석 중(409)이면 서버에서 끝날 결과를 기다린다.
  // 그때 화면에 있던 분석 id와 기다리기를 그만둘 시각을 두고, 새 분석이 보이면 멈춘다
  const [waiting, setWaiting] = useState<{
    fromRunId: number | null;
    until: number;
  } | null>(null);
  const { data: run } = useSuspenseQuery({
    queryKey: analysisLatestKey(course.id),
    queryFn: () => getLatestAnalysis(course.id),
    refetchInterval: (query) =>
      waiting !== null &&
      (query.state.data?.id ?? null) === waiting.fromRunId &&
      Date.now() < waiting.until
        ? WAIT_POLL_MS
        : false,
  });
  const { data: providers } = useProviders();
  const mutation = useMutation({
    mutationFn: () => startAnalysis(course.id),
    onMutate: () => setWaiting(null),
    onSuccess: (next) =>
      queryClient.setQueryData(analysisLatestKey(course.id), next),
    onError: (error) => {
      if (
        isNoResponseError(error) ||
        (error instanceof ApiError && error.status === 409)
      ) {
        setWaiting({
          fromRunId: run?.id ?? null,
          until: Date.now() + WAIT_LIMIT_MS,
        });
        void queryClient.invalidateQueries({
          queryKey: analysisLatestKey(course.id),
        });
      }
    },
  });
  // 기다리던 새 분석이 도착했으면 오류·기다림 안내를 내린다
  const arrived = waiting !== null && (run?.id ?? null) !== waiting.fromRunId;

  const configured = providers.filter((item) => item.configured).length;
  const blockedReason =
    course.keywords.length === 0
      ? '이 과목에 키워드가 없어요. 내 과목에서 키워드를 넣으면 그 개념별로 분석해요.'
      : configured === 0
        ? '연결된 AI 모델이 없어 지금은 분석할 수 없어요.'
        : null;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body text-ink-soft">
          모델 {providers.length}개 중 {configured}개 연결됨
        </p>
        <div className="flex flex-wrap items-center gap-3">
          {run && (
            // 브라우저 인쇄 창에서 "PDF로 저장"을 고르면 이 화면을 PDF로 받을 수 있다 (상단바·버튼은 인쇄에서 빠진다)
            <Button
              variant="secondary"
              onClick={() => window.print()}
              className="print:hidden"
            >
              <Download size={16} aria-hidden="true" />
              PDF로 받기
            </Button>
          )}
          <Button
            onClick={() => mutation.mutate()}
            disabled={mutation.isPending || blockedReason !== null}
            className="print:hidden"
          >
            {mutation.isPending
              ? '분석 중이에요'
              : run
                ? '다시 분석하기'
                : '분석하기'}
          </Button>
        </div>
      </div>

      {/* 목업(배포 미리보기 등)에서는 실제 판단처럼 보이지 않게 알린다 */}
      {mockData && (
        <p className="rounded-xl bg-surface-muted px-4 py-3 text-body text-ink-soft">
          예시 데이터예요. 실제 AI 모델이 판단한 결과가 아니에요.
        </p>
      )}

      <div aria-live="polite" className="flex flex-col gap-3 empty:hidden">
        {waiting && !arrived && !mutation.isPending && (
          <p className="rounded-xl bg-primary-soft px-4 py-3 text-body text-heading">
            서버에서 아직 분석하고 있을 수 있어요. 끝나면 이 화면에 결과가
            나타나요. 몇 분이 지나도 그대로면 다시 분석해 주세요.
          </p>
        )}
        {mutation.isPending && (
          <AiWaiting state="weaving" live={false}>
            모델마다 대화를 따로 읽고 있어요. 길면 1분 40초쯤 걸려요. 이 화면을
            떠나지 말아 주세요.
          </AiWaiting>
        )}
        {mutation.isError && !waiting && (
          <p
            role="alert"
            className="rounded-xl border border-danger px-4 py-3 text-body text-heading"
          >
            {mutation.error.message}
          </p>
        )}
        {blockedReason && (
          <p className="rounded-xl bg-surface-muted px-4 py-3 text-body text-ink-soft">
            {blockedReason}
          </p>
        )}
      </div>

      {run ? (
        <AnalysisResult key={run.id} run={run} />
      ) : (
        <EmptyState
          icon={<ChartColumn size={24} aria-hidden="true" />}
          title="아직 이 과목을 분석하지 않았어요"
          description="공부한 대화를 가져온 뒤 분석하기를 누르면, 여러 AI 모델이 따로 읽고 개념마다 판단을 비교해 보여 드려요."
          action={{ label: '대화 가져오기', to: '/analysis/import' }}
        />
      )}
    </div>
  );
}

function Dashboard() {
  const { data: courses } = useCourses();
  const [courseId, setCourseId] = useState<number | null>(null);
  const course = courses.find((item) => item.id === courseId) ?? courses[0];

  if (!course) {
    return (
      <EmptyState
        icon={<ChartColumn size={24} aria-hidden="true" />}
        title="먼저 과목을 만들어 주세요"
        description="과목과 키워드를 넣으면, 가져온 대화를 그 개념별로 분석해요."
        action={{ label: '내 과목 만들기', to: '/courses' }}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <label className="flex flex-col gap-1.5 text-caption font-bold text-ink-soft">
          과목
          <SelectInput
            value={course.id}
            onChange={(event) => setCourseId(Number(event.target.value))}
            className="min-w-56 text-body font-normal"
          >
            {courses.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </SelectInput>
        </label>
        <AiLegend />
      </div>

      {/* 맨 위: 사전평가 vs 사후평가 (평가가 없거나 불러오지 못해도 아래 분석은 그대로 보인다) */}
      <AsyncBoundary key={`assessment-${course.id}`}>
        <AssessmentComparisonPanel courseId={course.id} />
      </AsyncBoundary>

      {/* 개념별 학습 진도 (커리큘럼 응답 재사용, 대화 분석과 따로) */}
      <AsyncBoundary key={`study-${course.id}`}>
        <StudyProgressPanel courseId={course.id} />
      </AsyncBoundary>

      {/* 과목을 바꾸면 그 과목의 분석을 새로 불러온다 */}
      <AsyncBoundary key={course.id}>
        <CourseAnalysis course={course} />
      </AsyncBoundary>
    </div>
  );
}

function DashboardPage() {
  return (
    <SubPage
      title="학습 대시보드"
      description="가져온 대화를 여러 AI 모델이 따로 읽고, 개념마다 판단과 근거를 비교해 보여 드려요"
    >
      <AsyncBoundary>
        <Dashboard />
      </AsyncBoundary>
    </SubPage>
  );
}

export default DashboardPage;
