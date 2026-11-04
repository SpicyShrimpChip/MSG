import type { ReactNode } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useSuspenseQuery } from '@tanstack/react-query';
import {
  BookOpen,
  FileText,
  FolderOpen,
  Megaphone,
  Search,
} from 'lucide-react';
import { SEARCH_QUERY_KEY, USE_MOCK, search } from '@/api';
import {
  AsyncBoundary,
  Button,
  Card,
  Chip,
  EmptyState,
  SubPage,
} from '@/components';
import { MOCK_OPEN_ACCESS, useAuthStore, useDemoStore } from '@/store';
import { SEARCH_MAX_LENGTH, formatSemester } from '@/utils';

function Section({
  icon,
  title,
  count,
  children,
}: {
  icon: ReactNode;
  title: string;
  count: number;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="flex items-center gap-2 text-lg font-extrabold text-heading">
        <span className="text-primary" aria-hidden="true">
          {icon}
        </span>
        {title}
        <span className="text-body font-medium text-ink-muted">{count}</span>
      </h2>
      <ul className="flex flex-col gap-2">{children}</ul>
    </section>
  );
}

// 결과 한 줄: 제목 링크 + 부가 정보
function ResultRow({
  to,
  title,
  meta,
  badge,
  children,
}: {
  to: string;
  title: string;
  meta?: ReactNode;
  badge?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <Card as="li" className="flex flex-col gap-1.5 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Link
          to={to}
          className="font-bold text-heading underline-offset-4 hover:text-primary hover:underline"
        >
          {title}
        </Link>
        {badge}
      </div>
      {meta && <p className="text-caption text-ink-muted">{meta}</p>}
      {children}
    </Card>
  );
}

function SearchResults({ keyword }: { keyword: string }) {
  const { data } = useSuspenseQuery({
    queryKey: [...SEARCH_QUERY_KEY, keyword],
    queryFn: () => search(keyword),
  });
  const total =
    data.courses.length +
    data.materials.length +
    data.concepts.length +
    data.notices.length;

  if (total === 0) {
    return (
      <EmptyState
        icon={<Search size={24} aria-hidden="true" />}
        title="검색 결과가 없어요"
        description="내 과목 이름·키워드, 자료 제목, 자료에서 뽑은 핵심 개념, 공지 제목에서 찾았어요. 자료 본문 내용은 찾지 않아요. 다른 말로 찾아보세요."
        action={{ label: '내 학습자료 보기', to: '/materials' }}
      />
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <p className="text-body text-ink-soft">
        모두 {total}건을 찾았어요. 띄어쓰기와 대소문자는 구분하지 않아요.
        {USE_MOCK && ' 지금은 예시 데이터에서 찾았어요.'}
      </p>

      {data.courses.length > 0 && (
        <Section
          icon={<FolderOpen size={18} />}
          title="과목"
          count={data.courses.length}
        >
          {data.courses.map((course) => (
            <ResultRow
              key={course.id}
              to={`/curriculum?courseId=${course.id}`}
              title={course.name}
              meta={`${formatSemester(course.semester)} · 자료 ${course.materialCount}개`}
              badge={
                !course.nameMatched && <Chip tone="neutral">키워드가 맞음</Chip>
              }
            >
              {course.matchedKeywords.length > 0 && (
                <p className="text-caption text-ink-soft">
                  맞는 키워드: {course.matchedKeywords.join(', ')}
                </p>
              )}
              <div className="mt-1 flex flex-wrap gap-2">
                <Button
                  variant="secondary"
                  to={`/curriculum?courseId=${course.id}`}
                >
                  커리큘럼 보기
                </Button>
                <Button variant="secondary" to="/materials">
                  학습자료 보기
                </Button>
              </div>
            </ResultRow>
          ))}
        </Section>
      )}

      {data.materials.length > 0 && (
        <Section
          icon={<FileText size={18} />}
          title="학습자료"
          count={data.materials.length}
        >
          {data.materials.map((material) => (
            <ResultRow
              key={material.id}
              to={`/materials/${material.id}`}
              title={material.title}
              meta={[
                material.courseName ?? '과목에 연결하지 않은 자료',
                material.fileName,
              ].join(' · ')}
              badge={
                material.matchedBy === 'course' && (
                  <Chip tone="neutral">과목 이름이 맞음</Chip>
                )
              }
            />
          ))}
        </Section>
      )}

      {data.concepts.length > 0 && (
        <Section
          icon={<BookOpen size={18} />}
          title="핵심 개념"
          count={data.concepts.length}
        >
          {data.concepts.map((concept) => (
            <ResultRow
              key={`${concept.materialId}-${concept.conceptId}`}
              to={`/materials/${concept.materialId}?tab=concepts&concept=${encodeURIComponent(concept.conceptId)}`}
              title={concept.name}
              meta={`${concept.materialTitle}에서 뽑은 개념`}
            >
              {concept.aiDescription && (
                <p className="text-caption text-ink-soft">
                  {concept.aiDescription}
                </p>
              )}
            </ResultRow>
          ))}
        </Section>
      )}

      {data.notices.length > 0 && (
        <Section
          icon={<Megaphone size={18} />}
          title="공지사항"
          count={data.notices.length}
        >
          {data.notices.map((notice) => (
            <ResultRow
              key={notice.id}
              to={`/notices/${notice.id}`}
              title={notice.title}
              meta={notice.createdAt}
            />
          ))}
        </Section>
      )}
    </div>
  );
}

// 내 데이터를 찾으므로 로그인(목업이면 체험 모드)이 필요하다. 검색 화면 자체는 누구나 열 수 있다
function SearchBody({ keyword }: { keyword: string }) {
  const status = useAuthStore((state) => state.status);
  const demoActive = useDemoStore((state) => state.active);

  if (!keyword) {
    return (
      <EmptyState
        icon={<Search size={24} aria-hidden="true" />}
        title="찾고 싶은 과목·자료·개념을 입력하세요"
        description="상단 검색창에서 과목 이름, 자료 제목, 핵심 개념 이름으로 찾을 수 있어요."
      />
    );
  }
  if (keyword.length > SEARCH_MAX_LENGTH) {
    return (
      <EmptyState
        icon={<Search size={24} aria-hidden="true" />}
        title="검색어가 너무 길어요"
        description={`검색어는 ${SEARCH_MAX_LENGTH}자까지 입력할 수 있어요.`}
      />
    );
  }
  if (USE_MOCK ? !demoActive && !MOCK_OPEN_ACCESS : status === 'anonymous') {
    return (
      <EmptyState
        icon={<Search size={24} aria-hidden="true" />}
        title={
          USE_MOCK
            ? '체험 모드를 켜면 예시 데이터에서 찾아요'
            : '로그인하면 내 과목과 자료에서 찾아요'
        }
        description="검색은 내 과목·자료·개념에서 찾기 때문에 먼저 들어와야 해요."
        action={{
          label: USE_MOCK ? '메인으로' : '로그인',
          to: USE_MOCK ? '/' : '/login',
        }}
      />
    );
  }
  if (!USE_MOCK && status === 'unknown') return null;
  return (
    <AsyncBoundary>
      <SearchResults keyword={keyword} />
    </AsyncBoundary>
  );
}

function SearchPage() {
  const [searchParams] = useSearchParams();
  const keyword = searchParams.get('q')?.trim() ?? '';

  return (
    <SubPage
      title="검색"
      description={keyword ? `‘${keyword}’ 검색 결과` : undefined}
    >
      <div className="flex max-w-3xl flex-col gap-6">
        <SearchBody keyword={keyword} />
      </div>
    </SubPage>
  );
}

export default SearchPage;
