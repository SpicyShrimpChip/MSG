import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient, useSuspenseQuery } from '@tanstack/react-query';
import { ChartColumn, CircleAlert, Download } from 'lucide-react';
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
  Notice,
  ResultBeam,
  SelectInput,
  SubPage,
} from '@/components';
import { useCourses, useProviders, useSingleFlightMutation } from '@/hooks';
import type {
  AnalysisRun,
  Course,
  DashboardComponentId,
  LastAnalysisAttempt,
  LatestAnalysis,
} from '@/types';
import {
  DEFAULT_LAYOUT,
  formatDateTime,
  isAnalysisRunning,
  isNoResponseError,
  newestAnalysisId,
  parseDashboardLayout,
  sortByStudyOrder,
} from '@/utils';
import AnalysisSummaryPanel from './components/AnalysisSummaryPanel';
import AssessmentComparisonPanel from './components/AssessmentComparisonPanel';
import ConceptList from './components/ConceptList';
import LayoutSummary from './components/LayoutSummary';
import ModelStatusPanel from './components/ModelStatusPanel';
import StudyProgressPanel from './components/StudyProgressPanel';
import SummaryStrip, {
  ASSESSMENT_ANCHOR,
  PROGRESS_ANCHOR,
} from './components/SummaryStrip';
// 분석 결과를 배치대로 그린다. 배치 JSON은 검증을 통과할 때만 쓰고, 아니면 기본 배치
interface AnalysisResultProps {
  run: AnalysisRun;
  // 방금 도착한 분석이면 카드마다 테두리 빛이 한 바퀴 돈다
  highlight: boolean;
  onHighlightDone: () => void;
}

function AnalysisResult({
  run,
  highlight,
  onHighlightDone,
}: AnalysisResultProps) {
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
  // 펼친 개념. 처음엔 공부 순서 첫 개념만 펼친다. 피드백으로 순서가 바뀌어도 펼친 개념은 그대로 둔다
  const [openConcepts, setOpenConcepts] = useState<ReadonlySet<string>>(
    () => new Set(concepts[0] ? [concepts[0].concept] : []),
  );
  const toggleConcept = (concept: string) =>
    setOpenConcepts((current) => {
      const next = new Set(current);
      if (next.has(concept)) next.delete(concept);
      else next.add(concept);
      return next;
    });

  // 영역 id → 화면. 배치 JSON에는 이 id만 올 수 있다
  const renderComponent = (
    id: DashboardComponentId,
    reason: string | null,
  ): ReactNode => {
    switch (id) {
      case 'summary':
        return <SummaryStrip run={run} layoutReason={reason} />;
      case 'concepts':
        return (
          <ConceptList
            concepts={concepts}
            models={run.models}
            courseId={run.courseId}
            runId={run.id}
            openConcepts={openConcepts}
            onToggle={toggleConcept}
            layoutReason={reason}
          />
        );
      case 'models':
        return (
          <div className="grid gap-6 lg:grid-cols-2">
            <ModelStatusPanel models={run.models} layoutReason={reason} />
            <AnalysisSummaryPanel run={run} />
          </div>
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
      {layout.items.map((item, index) => (
        // 영역이 위에서부터 차례로 나타난다. 영역은 모두 한 줄 전체를 쓴다(위에서 아래로 읽는 화면)
        <m.div
          key={item.component}
          initial={FADE_UP.initial}
          animate={FADE_UP.animate}
          transition={{ ...FADE_UP.transition, delay: index * 0.06 }}
          className="relative"
        >
          {renderComponent(item.component, item.aiReason)}
          <ResultBeam active={highlight} onDone={onHighlightDone} />
        </m.div>
      ))}
    </div>
  );
}

// 시간 초과·409 뒤나 서버가 분석 중이라고 할 때 최근 분석을 다시 확인하는 간격,
// 시간 초과·409 뒤 그만둘 때까지의 시간 (서버 분석 예산 100초보다 넉넉히)
const WAIT_POLL_MS = 10_000;
const WAIT_LIMIT_MS = 5 * 60_000;

// 결과 없이 끝난 가장 최근 분석 안내. 이전 결과가 있으면 그것을 그대로 보여 준다고 알린다
function FailedAttemptNotice({
  attempt,
  hasPreviousRun,
}: {
  attempt: LastAnalysisAttempt;
  hasPreviousRun: boolean;
}) {
  const at = formatDateTime(attempt.finishedAt ?? attempt.startedAt);
  return (
    <Notice icon={CircleAlert}>
      <p className="text-heading">
        {hasPreviousRun
          ? `가장 최근 분석(${at})은 결과를 받지 못해 이전 분석 결과를 그대로 보여 드려요.`
          : `분석(${at})을 시도했지만 결과를 받지 못했어요.`}
      </p>
      {attempt.message && <p>{attempt.message}</p>}
      <p>
        잠시 뒤 다시 분석해 주세요. 결과를 받지 못한 분석도 24시간 분석 횟수에
        들어가요.
      </p>
    </Notice>
  );
}

// 과목 하나의 최근 분석과 분석 시작
function CourseAnalysis({ course }: { course: Course }) {
  const mockData = useMockData();
  const queryClient = useQueryClient();
  // 기다리기를 멈췄거나(시간 초과) 이미 분석 중(409)이면 서버에서 끝날 결과를 기다린다.
  // 그때 가장 나중 분석 id와 기다리기를 그만둘 시각을 두고, 더 나중 분석이 보이면 멈춘다
  const [waiting, setWaiting] = useState<{
    fromRunId: number | null;
    until: number;
  } | null>(null);
  const { data: latest } = useSuspenseQuery({
    queryKey: analysisLatestKey(course.id),
    queryFn: () => getLatestAnalysis(course.id),
    // 서버가 분석 중이라고 하면(새로고침·다른 화면에서 돌아옴) 끝날 때까지 다시 확인한다.
    // 서버는 오래 멈춘 분석(running-timeout)을 진행 중으로 주지 않으므로 끝없이 묻지 않는다
    refetchInterval: (query) =>
      isAnalysisRunning(query.state.data) ||
      (waiting !== null &&
        newestAnalysisId(query.state.data) === waiting.fromRunId &&
        Date.now() < waiting.until)
        ? WAIT_POLL_MS
        : false,
  });
  const { run, lastAttempt } = latest;
  const serverRunning = isAnalysisRunning(latest);
  const { data: providers } = useProviders();
  const mutation = useSingleFlightMutation({
    mutationFn: () => startAnalysis(course.id),
    onMutate: () => setWaiting(null),
    onSuccess: (next) => {
      // 모델이 모두 실패한 분석은 이전 결과를 덮지 않는다. 서버에서 이전 결과와 실패 안내를 다시 받는다
      if (next.status === 'failed') {
        void queryClient.invalidateQueries({
          queryKey: analysisLatestKey(course.id),
        });
        return;
      }
      queryClient.setQueryData<LatestAnalysis>(analysisLatestKey(course.id), {
        run: next,
        lastAttempt: null,
      });
    },
    onError: (error) => {
      if (
        isNoResponseError(error) ||
        (error instanceof ApiError && error.status === 409)
      ) {
        setWaiting({
          fromRunId: newestAnalysisId(latest),
          until: Date.now() + WAIT_LIMIT_MS,
        });
        void queryClient.invalidateQueries({
          queryKey: analysisLatestKey(course.id),
        });
      }
    },
  });
  // 기다리던 분석이 끝났으면(더 나중 분석이 보이고 진행 중이 아님) 오류·기다림 안내를 내린다
  const arrived =
    waiting !== null &&
    newestAnalysisId(latest) !== waiting.fromRunId &&
    !serverRunning;
  const analyzing = mutation.isPending || serverRunning;

  // 화면을 연 뒤 새 분석이 들어오면(분석하기 응답이든 기다리다 도착했든) 결과 카드에 빛을 한 번 돌린다.
  // 처음 불러온 분석에는 돌리지 않는다. 과목을 바꾸면 이 부품이 새로 만들어져 처음부터 센다
  const runId = run?.id ?? null;
  const [seenRunId, setSeenRunId] = useState(runId);
  const [beamRunId, setBeamRunId] = useState<number | null>(null);
  if (runId !== seenRunId) {
    setSeenRunId(runId);
    setBeamRunId(runId);
  }
  const endBeam = useCallback(() => setBeamRunId(null), []);

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
            disabled={analyzing || blockedReason !== null}
            className="print:hidden"
          >
            {analyzing ? '분석 중이에요' : run ? '다시 분석하기' : '분석하기'}
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
        {waiting && !arrived && !analyzing && (
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
        {/* 새로고침하거나 다른 화면에서 돌아와도 서버가 분석 중이면 이어서 보여 준다 */}
        {!mutation.isPending && serverRunning && (
          <AiWaiting state="weaving" live={false}>
            모델마다 대화를 따로 읽고 있어요. 길면 1분 40초쯤 걸리고, 끝나면 이
            화면에 결과가 나타나요.
          </AiWaiting>
        )}
        {!analyzing && lastAttempt?.status === 'failed' && (
          <FailedAttemptNotice
            attempt={lastAttempt}
            hasPreviousRun={run !== null}
          />
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
        <AnalysisResult
          key={run.id}
          run={run}
          highlight={beamRunId === run.id}
          onHighlightDone={endBeam}
        />
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
        <label className="flex flex-col gap-1.5 text-caption font-semibold text-ink-soft">
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

      {/* 과목을 바꾸면 그 과목의 분석을 새로 불러온다 */}
      <AsyncBoundary key={course.id}>
        <CourseAnalysis course={course} />
      </AsyncBoundary>

      {/* 맨 위 요약 칸의 "자세히"가 내려오는 곳. 분석 결과와 따로 과목 기록으로 센다(분석 전에도 보인다) */}
      <div id={ASSESSMENT_ANCHOR} className="scroll-mt-28">
        <AsyncBoundary key={`assessment-${course.id}`}>
          <AssessmentComparisonPanel courseId={course.id} />
        </AsyncBoundary>
      </div>
      <div id={PROGRESS_ANCHOR} className="scroll-mt-28">
        <AsyncBoundary key={`study-${course.id}`}>
          <StudyProgressPanel courseId={course.id} />
        </AsyncBoundary>
      </div>
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
