import { USE_MOCK } from '@/api/axios';
import { useDemoStore } from '@/store';

// 체험 모드 중 모든 화면 맨 위에 보이는 얇은 띠. 지금 보는 것이 예시 데이터라는 점을 알려 준다
function DemoBanner() {
  const active = useDemoStore((state) => state.active);
  const end = useDemoStore((state) => state.end);

  if (!USE_MOCK || !active) return null;

  return (
    <div
      role="status"
      className="flex items-center gap-3 border-b border-line bg-primary-soft px-6 py-2 text-body text-heading-soft max-sm:gap-2 max-sm:px-4 print:hidden"
    >
      <span className="shrink-0 rounded bg-primary px-2 py-0.5 text-caption font-bold text-on-primary">
        체험 모드
      </span>
      {/* 휴대폰 폭(375px)에서는 한 줄에 들어가게 뒷문장을 뺀다 */}
      <span className="min-w-0 truncate">
        예시 데이터를 보여 드려요.
        <span className="max-sm:hidden"> 저장되지 않아요.</span>
      </span>
      <button
        type="button"
        onClick={() => {
          end();
          // 보호된 화면에서 끝내면 가드가 먼저 로그인으로 보내 버리므로, 메인을 새로 불러온다
          // (체험 중 React Query에 담긴 예시 데이터도 함께 사라진다)
          window.location.assign('/');
        }}
        className="ml-auto shrink-0 font-bold text-primary underline underline-offset-4 hover:text-primary-hover"
      >
        체험 끝내기
      </button>
    </div>
  );
}

export default DemoBanner;
