import { Info } from 'lucide-react';
import { USE_MOCK } from '@/api';
import { DemoStartButton, Notice } from '@/components';

interface MockAuthNoticeProps {
  // 로그인 가드가 보낸 경우 원래 가려던 화면 (있으면 안내 문구가 달라진다)
  from?: string;
}

// 목업 모드(공개 배포)에는 서버가 없어서 로그인·가입이 아직 열려 있지 않다. 그 사실과 체험 모드를 알린다.
// 서버와 연결된 실제 모드에서는 아무것도 그리지 않는다
function MockAuthNotice({ from }: MockAuthNoticeProps) {
  if (!USE_MOCK) return null;

  return (
    <Notice icon={Info} className="max-w-md">
      <p>
        {from ? '이 화면은 로그인한 뒤에 볼 수 있어요. ' : ''}
        지금은 체험용 화면이라 로그인과 회원가입은 아직 열려 있지 않아요. 예시
        데이터로 먼저 둘러볼 수 있어요.
      </p>
      <DemoStartButton
        // 관리자 화면은 체험에 포함하지 않으므로 기본 화면(학습 대시보드)으로 보낸다
        to={from && !from.startsWith('/admin') ? from : undefined}
        className="w-fit rounded-lg bg-primary px-3.5 py-2 font-bold text-on-primary hover:bg-primary-hover"
      />
    </Notice>
  );
}

export default MockAuthNotice;
