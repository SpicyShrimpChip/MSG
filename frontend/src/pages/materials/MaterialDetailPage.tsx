import { useParams, useSearchParams } from 'react-router-dom';
import { useSuspenseQuery } from '@tanstack/react-query';
import { FileCheck, ListChecks, TriangleAlert, Video } from 'lucide-react';
import { materialQuery } from '@/api';
import {
  AiLegend,
  AiOrb,
  AsyncBoundary,
  Button,
  EmptyState,
  LIST_PANEL_CLASS,
  SubPage,
  Tabs,
} from '@/components';
import { useCourses, useQuizzes } from '@/hooks';
import type { MaterialDetail } from '@/types';
import { formatDate } from '@/utils';
import QuizCard from '@/pages/quizzes/components/QuizCard';
import MaterialConceptPanel from './components/MaterialConceptPanel';
import StudySummaryPanel from './components/StudySummaryPanel';

// 퀴즈 탭에서 자료가 아직 준비되지 않았을 때(파일만 올림·글 읽기 실패·읽는 중) 보여 주는 안내
function NotReady({ status }: { status: MaterialDetail['status'] }) {
  if (status === 'uploaded') {
    return (
      <EmptyState
        icon={<FileCheck size={24} aria-hidden="true" />}
        title="파일을 올렸어요"
        description="이 자료로 만든 퀴즈가 아직 없어요. 퀴즈 만들기에서 이 자료의 과목 개념으로 퀴즈를 만들 수 있어요. 올린 파일은 지우기 전까지 보관해요."
        action={{ label: '퀴즈 만들기', to: '/quizzes/new' }}
      />
    );
  }
  if (status === 'failed') {
    return (
      <EmptyState
        icon={<TriangleAlert size={24} aria-hidden="true" />}
        title="자료에서 글을 읽지 못했어요"
        description="스캔한 이미지만 있는 파일은 글을 뽑기 어려워요. 글자를 고를 수 있는 파일로 다시 올려 주세요."
        action={{ label: '다시 올리기', to: '/materials/upload' }}
      />
    );
  }
  return (
    <EmptyState
      icon={<AiOrb />}
      title="요약과 핵심 개념을 만들고 있어요"
      description="자료 길이에 따라 몇 분 걸릴 수 있어요. 다 되면 이 화면에서 바로 볼 수 있어요."
    />
  );
}

// 요약본은 자료 분석 상태와 관계없이 "요약 만들기"로 따로 만든다 (학습 목적별, 항목마다 원문 근거를 서버가 확인).
// 예전 자료 상세의 aiSummary(서버는 늘 null)는 쓰지 않는다
function SummaryTab({ material }: { material: MaterialDetail }) {
  return (
    <AsyncBoundary>
      <StudySummaryPanel
        materialId={material.id}
        fileName={material.fileName}
      />
    </AsyncBoundary>
  );
}

// 핵심 개념은 자료 분석 상태와 관계없이 "핵심 개념 뽑기"로 따로 만든다 (근거를 서버가 본문에서 확인한 것만).
// 개념마다 "자세한 설명"(AI)으로 개념을 공부한다. 커리큘럼·검색에서 ?concept=로 오면 그 개념을 펼쳐 보여 준다
function ConceptsTab({
  material,
  focusConceptId,
}: {
  material: MaterialDetail;
  focusConceptId: string | null;
}) {
  return (
    <AsyncBoundary>
      <MaterialConceptPanel
        materialId={material.id}
        fileName={material.fileName}
        courseId={material.courseId}
        focusConceptId={focusConceptId}
      />
    </AsyncBoundary>
  );
}

// 퀴즈는 자료 분석과 따로 저장될 수 있어서(분석 전 자료에 붙인 퀴즈 등) 상태와 관계없이 이 자료의 퀴즈를 먼저 찾는다.
// 퀴즈가 없을 때만 분석 상태 안내를 보여 준다
// 이 자료로 사전·사후평가 보기 (평가 화면에서 이 자료를 골라 둔다)
function QuizTab({ material }: { material: MaterialDetail }) {
  return (
    <div className="flex flex-col gap-4">
      {material.courseId !== null && (
        <div>
          <Button to={`/assessments?materialId=${material.id}`}>
            이 자료로 사전·사후평가 보기
          </Button>
        </div>
      )}
      <QuizTabContent material={material} />
    </div>
  );
}

function QuizTabContent({ material }: { material: MaterialDetail }) {
  const { data: quizzes } = useQuizzes();
  const mine = quizzes.filter((quiz) => quiz.materialId === material.id);
  if (mine.length === 0) {
    if (material.status !== 'ready') {
      return <NotReady status={material.status} />;
    }
    return (
      <EmptyState
        icon={<ListChecks size={24} aria-hidden="true" />}
        title="이 자료로 만든 퀴즈가 아직 없어요"
        description="퀴즈 결과는 학습 대시보드에서 개념별 숙련도의 근거로 쓰여요."
        action={{ label: '퀴즈 목록 보기', to: '/quizzes' }}
      />
    );
  }
  return (
    <ul className={LIST_PANEL_CLASS}>
      {mine.map((quiz) => (
        <QuizCard key={quiz.id} quiz={quiz} />
      ))}
    </ul>
  );
}

function MaterialDetailContent({ materialId }: { materialId: number }) {
  const [params] = useSearchParams();
  const focusConceptId = params.get('concept');
  const initialTab = params.get('tab') ?? (focusConceptId ? 'concepts' : null);
  const { data: material } = useSuspenseQuery(materialQuery(materialId));
  const { data: courses } = useCourses();
  const course = courses.find((item) => item.id === material.courseId);
  const date = formatDate(material.createdAt);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1.5">
          <h2 className="text-2xl font-semibold text-heading">
            {material.title}
          </h2>
          <p className="flex flex-wrap gap-x-3 gap-y-1 text-caption text-ink-muted">
            <span>{course?.name ?? '과목에 연결하지 않은 자료'}</span>
            <span>{material.fileName}</span>
            {date && <span>{date}</span>}
          </p>
        </div>
        <AiLegend />
      </div>
      <Tabs
        label="자료 상세 보기"
        initialId={initialTab}
        tabs={[
          {
            id: 'summary',
            label: '요약',
            content: <SummaryTab material={material} />,
          },
          {
            id: 'concepts',
            label: '핵심 개념',
            content: (
              <ConceptsTab
                material={material}
                focusConceptId={focusConceptId}
              />
            ),
          },
          {
            id: 'quiz',
            label: '퀴즈',
            content: (
              <AsyncBoundary>
                <QuizTab material={material} />
              </AsyncBoundary>
            ),
          },
          {
            id: 'video',
            label: 'AI 요약 영상',
            badge: '준비 중',
            content: (
              // TODO(미결): AI 요약 영상 기능의 우선순위 확정 전까지 자리만 둔다
              <EmptyState
                icon={<Video size={24} aria-hidden="true" />}
                title="준비 중인 기능이에요"
                description="자료 내용을 정리한 요약 영상을 제공할 예정이에요."
              />
            ),
          },
        ]}
      />
    </div>
  );
}

function MaterialDetailPage() {
  const { materialId } = useParams();
  return (
    <SubPage
      title="자료 상세"
      description="자료의 요약, 핵심 개념, 퀴즈를 한곳에서 볼 수 있어요"
    >
      <AsyncBoundary
        notFoundAction={{ label: '학습자료 목록으로', to: '/materials' }}
      >
        <MaterialDetailContent materialId={Number(materialId)} />
      </AsyncBoundary>
    </SubPage>
  );
}

export default MaterialDetailPage;
