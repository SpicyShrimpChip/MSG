import { Skeleton } from '@/components';

interface DashboardSkeletonProps {
  // 과목 목록을 불러오는 동안이면 과목 고르기 줄 자리도 함께 그린다
  withCourseRow?: boolean;
}

// 학습 대시보드를 불러오는 동안의 틀. 분석 결과 자리를 화면 높이만큼 잡아 두어,
// 결과가 들어올 때 먼저 그려진 사전·사후평가 구역과 바닥글이 아래로 밀리지 않게 한다(화면 밀림 방지)
function DashboardSkeleton({ withCourseRow = false }: DashboardSkeletonProps) {
  return (
    <div role="status" className="flex flex-col gap-6">
      <span className="sr-only">불러오는 중이에요</span>
      {withCourseRow && (
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="flex flex-col gap-1.5">
            <Skeleton className="h-4 w-10" />
            <Skeleton className="h-11 w-56 rounded-xl" />
          </div>
          <Skeleton className="h-4 w-56" />
        </div>
      )}
      <div className="flex min-h-screen flex-col gap-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Skeleton className="h-5 w-40" />
          <Skeleton className="h-11 w-36 rounded-xl" />
        </div>
        <Skeleton className="h-12 rounded-xl" />
        <Skeleton className="h-14 rounded-2xl" />
        <Skeleton className="flex-1 rounded-2xl" />
      </div>
    </div>
  );
}

export default DashboardSkeleton;
