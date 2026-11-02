import { useParams } from 'react-router-dom';
import { useSuspenseQuery } from '@tanstack/react-query';
import {
  BookOpen,
  FileCheck,
  ListChecks,
  LoaderCircle,
  Sparkles,
  TriangleAlert,
  Video,
} from 'lucide-react';
import { MATERIALS_QUERY_KEY, getMaterial } from '@/api';
import {
  AiNote,
  AsyncBoundary,
  Button,
  Card,
  EmptyState,
  SubPage,
  Tabs,
} from '@/components';
import { useCourses, useQuizzes } from '@/hooks';
import type { MaterialDetail } from '@/types';
import { formatDate } from '@/utils';
import QuizCard from '@/pages/quizzes/components/QuizCard';

// 요약·개념·퀴즈를 아직 만들지 않았거나(분석 전·분석 중) 만들지 못한 자료는 탭마다 같은 안내를 보여 준다
function NotReady({ status }: { status: MaterialDetail['status'] }) {
  if (status === 'uploaded') {
    return (
      <EmptyState
        icon={<FileCheck size={24} aria-hidden="true" />}
        title="파일을 올렸어요. 아직 AI 분석 전이에요"
        description="요약, 핵심 개념, 퀴즈를 만드는 기능은 준비 중이에요. 올린 파일은 지우기 전까지 보관해요."
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
      icon={
        <LoaderCircle size={24} aria-hidden="true" className="animate-spin" />
      }
      title="요약과 핵심 개념을 만들고 있어요"
      description="자료 길이에 따라 몇 분 걸릴 수 있어요. 다 되면 이 화면에서 바로 볼 수 있어요."
    />
  );
}

function SummaryTab({ material }: { material: MaterialDetail }) {
  if (material.status !== 'ready') return <NotReady status={material.status} />;
  if (!material.aiSummary) {
    return (
      <EmptyState
        icon={<Sparkles size={24} aria-hidden="true" />}
        title="요약이 아직 없어요"
        description="자료 내용이 짧으면 요약을 만들지 않을 수 있어요. 핵심 개념 탭을 확인해 보세요."
      />
    );
  }
  return (
    <AiNote title="자료 요약">
      <p className="whitespace-pre-line">{material.aiSummary}</p>
    </AiNote>
  );
}

function ConceptsTab({ material }: { material: MaterialDetail }) {
  if (material.status !== 'ready') return <NotReady status={material.status} />;
  if (material.concepts.length === 0) {
    return (
      <EmptyState
        icon={<BookOpen size={24} aria-hidden="true" />}
        title="뽑은 핵심 개념이 없어요"
        description="이 자료에서 과목 키워드와 맞는 개념을 찾지 못했어요. 내 과목의 키워드를 확인해 보세요."
        action={{ label: '내 과목 보기', to: '/courses' }}
      />
    );
  }
  return (
    <div className="flex flex-col gap-4">
      <p className="text-body text-ink-soft">
        이 자료에서 다루는 개념 {material.concepts.length}개예요. 퀴즈 결과를
        개념별 숙련도에 반영하는 기능은 준비 중이에요.
      </p>
      <ul className="grid gap-3 md:grid-cols-2">
        {material.concepts.map((concept) => (
          <Card
            as="li"
            key={concept.conceptId}
            className="flex flex-col gap-3 p-5"
          >
            <p className="font-bold text-heading">{concept.name}</p>
            {concept.aiDescription && (
              <AiNote title="이 자료에서는">{concept.aiDescription}</AiNote>
            )}
          </Card>
        ))}
      </ul>
      <div>
        <Button to="/analysis" variant="secondary">
          학습 대시보드에서 숙련도 보기
        </Button>
      </div>
    </div>
  );
}

// 분석이 끝난 자료만 퀴즈 목록을 불러온다
function QuizTab({ material }: { material: MaterialDetail }) {
  if (material.status !== 'ready') return <NotReady status={material.status} />;
  return <MaterialQuizzes materialId={material.id} />;
}

function MaterialQuizzes({ materialId }: { materialId: number }) {
  const { data: quizzes } = useQuizzes();
  const mine = quizzes.filter((quiz) => quiz.materialId === materialId);
  if (mine.length === 0) {
    return (
      <EmptyState
        icon={<ListChecks size={24} aria-hidden="true" />}
        title="이 자료로 만든 퀴즈가 아직 없어요"
        description="퀴즈 결과를 개념별 숙련도에 반영하는 기능은 준비 중이에요."
        action={{ label: '퀴즈 목록 보기', to: '/quizzes' }}
      />
    );
  }
  return (
    <ul className="flex flex-col gap-3">
      {mine.map((quiz) => (
        <QuizCard key={quiz.id} quiz={quiz} />
      ))}
    </ul>
  );
}

function MaterialDetailContent({ materialId }: { materialId: number }) {
  const { data: material } = useSuspenseQuery({
    queryKey: [...MATERIALS_QUERY_KEY, materialId],
    queryFn: () => getMaterial(materialId),
  });
  const { data: courses } = useCourses();
  const course = courses.find((item) => item.id === material.courseId);
  const date = formatDate(material.createdAt);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1.5">
          <h2 className="text-2xl font-extrabold text-heading">
            {material.title}
          </h2>
          <p className="flex flex-wrap gap-x-3 gap-y-1 text-caption text-ink-muted">
            <span>{course?.name ?? '과목에 연결하지 않은 자료'}</span>
            <span>{material.fileName}</span>
            {date && <span>{date}</span>}
          </p>
        </div>
        <p className="flex items-center gap-1.5 text-caption text-ai">
          <Sparkles size={14} aria-hidden="true" />
          AI 표시가 있는 내용은 AI가 작성했어요
        </p>
      </div>
      <Tabs
        label="자료 상세 보기"
        tabs={[
          {
            id: 'summary',
            label: '요약',
            content: <SummaryTab material={material} />,
          },
          {
            id: 'concepts',
            label: '핵심 개념',
            content: <ConceptsTab material={material} />,
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
      <AsyncBoundary>
        <MaterialDetailContent materialId={Number(materialId)} />
      </AsyncBoundary>
    </SubPage>
  );
}

export default MaterialDetailPage;
