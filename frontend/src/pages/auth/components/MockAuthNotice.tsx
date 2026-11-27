import { Info } from 'lucide-react';
import { NO_BACKEND } from '@/store';
import { DemoStartButton, Notice } from '@/components';

interface MockAuthNoticeProps {
  // 로그인 가드가 보낸 경우 원래 가려던 화면 (있으면 안내 문구가 달라진다)
  from?: string;
}

// 로그인·가입 화면 위 안내. 백엔드가 없는 빌드(공개 배포)에서는 로그인·가입이 닫혀 있다는 것과 체험을,
// 실제 서비스에서는 계정 없이 예시로 먼저 둘러볼 수 있다는 것을 알린다
function MockAuthNotice({ from }: MockAuthNoticeProps) {
  return (
    <Notice icon={Info}>
      <p>
        {from ? '이 화면은 로그인한 뒤에 볼 수 있어요. ' : ''}
        {NO_BACKEND
          ? '지금은 체험용 화면이라 로그인과 회원가입은 아직 열려 있지 않아요. 예시 데이터로 먼저 둘러볼 수 있어요.'
          : '계정 없이 예시 데이터로 먼저 둘러볼 수도 있어요. 체험 중에 입력한 내용은 서버로 보내지 않아요.'}
      </p>
      <DemoStartButton
        // 관리자 화면은 체험에 포함하지 않으므로 기본 화면(학습 대시보드)으로 보낸다
        to={from && !from.startsWith('/admin') ? from : undefined}
        className="w-fit rounded-lg bg-primary px-3.5 py-2 font-semibold text-on-primary hover:bg-primary-hover"
      />
    </Notice>
  );
}

export default MockAuthNotice;
