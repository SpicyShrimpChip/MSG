import { Outlet } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { EmptyState, LoadingFallback, SubPage } from '@/components';
import { useMe } from '@/hooks';
import { MOCK_OPEN_ACCESS, useMockData } from '@/store';

// 관리자 화면을 감싸는 가드. 반드시 RequireAuth 안에 둔다 (로그인 확인은 거기서 한다).
// 이 가드는 안내용일 뿐이고, 진짜 권한은 서버가 요청마다 확인한다 (/api/admin/**).
// 목업 모드에는 관리자 계정이 없어서 체험 모드에서도 열지 않는다 (개발 중 VITE_MOCK_OPEN_ACCESS=true만 예외)
function RequireAdmin() {
  const { data: me, isPending } = useMe();
  // 체험이거나 백엔드가 없으면(= 목업 데이터를 읽으면) 관리자 계정이 없다
  const noAdmin = useMockData();

  // MOCK_OPEN_ACCESS는 백엔드가 없는 빌드에서만 켜진다 (store/useDemoStore.ts)
  if (MOCK_OPEN_ACCESS) return <Outlet />;
  if (!noAdmin && isPending) return <LoadingFallback />;
  if (noAdmin || me?.role !== 'ADMIN') {
    return (
      <SubPage title="관리자">
        <EmptyState
          icon={<ShieldAlert size={24} aria-hidden="true" />}
          title="관리자만 볼 수 있어요"
          description={
            noAdmin
              ? '체험 모드에서는 관리자 화면을 보여 드리지 않아요.'
              : '이 화면은 관리자 계정으로 로그인했을 때만 볼 수 있어요.'
          }
          action={{ label: '홈으로 가기', to: '/' }}
        />
      </SubPage>
    );
  }
  return <Outlet />;
}

export default RequireAdmin;
