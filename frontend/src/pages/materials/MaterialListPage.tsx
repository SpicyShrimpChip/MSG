import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { FileText, LoaderCircle, Trash2 } from 'lucide-react';
import { MATERIALS_QUERY_KEY, deleteMaterial, getMaterials } from '@/api';
import {
  AsyncBoundary,
  Button,
  Card,
  Chip,
  ConfirmDialog,
  EmptyState,
  SubPage,
  ErrorText,
} from '@/components';
import { useCourses } from '@/hooks';
import type { Course, MaterialSummary } from '@/types';
import { formatDate, formatSemester } from '@/utils';

interface MaterialGroup {
  key: string;
  title: string;
  materials: MaterialSummary[];
}

// 내 과목 순서대로 묶는다. 과목 없이 올린 자료는 맨 뒤에 따로 모은다
// (과목을 지우면 그 과목의 자료도 서버에서 함께 지워진다)
function groupByCourse(
  materials: MaterialSummary[],
  courses: Course[],
): MaterialGroup[] {
  const groups: MaterialGroup[] = courses
    .map((course) => ({
      key: String(course.id),
      title: `${course.name} · ${formatSemester(course.semester)}`,
      materials: materials.filter((item) => item.courseId === course.id),
    }))
    .filter((group) => group.materials.length > 0);
  const orphaned = materials.filter(
    (item) => !courses.some((course) => course.id === item.courseId),
  );
  if (orphaned.length > 0) {
    groups.push({
      key: 'orphaned',
      title: '과목에 연결하지 않은 자료',
      materials: orphaned,
    });
  }
  return groups;
}

function StatusChip({ status }: { status: MaterialSummary['status'] }) {
  if (status === 'uploaded') {
    return <span className="text-caption">AI 분석 전</span>;
  }
  if (status === 'processing') {
    return (
      <Chip tone="primary" className="gap-1.5">
        <LoaderCircle size={12} className="animate-spin" aria-hidden="true" />
        요약 만드는 중
      </Chip>
    );
  }
  if (status === 'failed') {
    return (
      <span className="text-caption font-medium text-danger">
        글을 읽지 못했어요
      </span>
    );
  }
  return null;
}

function MaterialCard({
  material,
  onDelete,
  deleting,
}: {
  material: MaterialSummary;
  onDelete: () => void;
  deleting: boolean;
}) {
  const date = formatDate(material.createdAt);
  return (
    <Card as="li" className="flex items-start justify-between gap-4 p-5">
      <div className="flex min-w-0 flex-col gap-1.5">
        <Link
          to={`/materials/${material.id}`}
          className="font-bold text-heading hover:text-primary hover:underline"
        >
          {material.title}
        </Link>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-caption text-ink-muted">
          <span className="truncate">{material.fileName}</span>
          {date && <span>{date}</span>}
          {material.status === 'ready' && (
            <span>퀴즈 {material.quizCount}개</span>
          )}
          <StatusChip status={material.status} />
        </p>
      </div>
      <button
        type="button"
        onClick={onDelete}
        disabled={deleting}
        aria-label={`${material.title} 삭제`}
        className="shrink-0 rounded-lg p-2 text-ink-muted hover:bg-surface-muted hover:text-heading disabled:opacity-50"
      >
        <Trash2 size={18} aria-hidden="true" />
      </button>
    </Card>
  );
}

function MaterialList() {
  const queryClient = useQueryClient();
  const { data: materials } = useSuspenseQuery({
    queryKey: MATERIALS_QUERY_KEY,
    queryFn: getMaterials,
  });
  const { data: courses } = useCourses();
  const [deleting, setDeleting] = useState<MaterialSummary | null>(null);

  const removal = useMutation({
    mutationFn: deleteMaterial,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: MATERIALS_QUERY_KEY }),
  });

  if (materials.length === 0) {
    return (
      <EmptyState
        icon={<FileText size={24} aria-hidden="true" />}
        title="아직 올린 자료가 없어요"
        description="필기, 정리 노트, 연습문제 풀이를 올리면 요약과 퀴즈로 바로 공부를 시작할 수 있어요."
        action={{ label: '첫 자료 올리기', to: '/materials/upload' }}
      />
    );
  }

  const groups = groupByCourse(materials, courses);

  return (
    <div className="flex flex-col gap-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-body text-ink-soft">
          자료 {materials.length}개 · 자료를 누르면 요약, 핵심 개념, 퀴즈를 볼
          수 있어요
        </p>
        <Button to="/materials/upload">자료 올리기</Button>
      </div>

      <ErrorText error={removal.error} />

      {groups.map((group) => (
        <section
          key={group.key}
          aria-labelledby={`material-group-${group.key}`}
          className="flex flex-col gap-3"
        >
          <h2
            id={`material-group-${group.key}`}
            className="text-lg font-extrabold text-heading"
          >
            {group.title}{' '}
            <span className="text-body font-medium text-ink-muted">
              {group.materials.length}개
            </span>
          </h2>
          <ul className="flex flex-col gap-3">
            {group.materials.map((material) => (
              <MaterialCard
                key={material.id}
                material={material}
                onDelete={() => setDeleting(material)}
                deleting={
                  removal.isPending && removal.variables === material.id
                }
              />
            ))}
          </ul>
        </section>
      ))}

      <ConfirmDialog
        open={deleting !== null}
        title={`'${deleting?.title ?? ''}' 자료를 삭제할까요?`}
        description="이 자료로 만든 요약·퀴즈와, 퀴즈 결과로 계산한 판단 근거도 함께 지워져요. 되돌릴 수 없어요."
        confirmLabel="삭제하기"
        onConfirm={() => {
          if (deleting) removal.mutate(deleting.id);
          setDeleting(null);
        }}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
}

function MaterialListPage() {
  return (
    <SubPage
      title="내 학습자료"
      description="올린 자료에서 요약, 핵심 개념, 퀴즈를 만들어요"
    >
      <AsyncBoundary>
        <MaterialList />
      </AsyncBoundary>
    </SubPage>
  );
}

export default MaterialListPage;
