import { useEffect } from 'react';
import { useRouteError } from 'react-router-dom';
import { CircleAlert } from 'lucide-react';
import { Button } from '@/components';
import { isChunkLoadError, shouldAutoReload } from '@/utils';

const RELOAD_KEY = 'ai-lms-chunk-reload-at';

function readLastReload(): number | null {
  try {
    const value = sessionStorage.getItem(RELOAD_KEY);
    return value === null ? null : Number(value);
  } catch {
    return null;
  }
}

function markReload(now: number) {
  try {
    sessionStorage.setItem(RELOAD_KEY, String(now));
    return true;
  } catch {
    // 저장소를 못 쓰면 무한 새로고침을 막을 수 없으니 자동 새로고침을 하지 않는다
    return false;
  }
}

// 라우터 맨 위 errorElement. 화면을 그리다 오류가 나거나 화면 코드를 못 불러왔을 때
// React Router 기본 화면(영어, 오류 내용 노출) 대신 보여 준다
export default function RouteErrorPage() {
  const error = useRouteError();
  const chunkError = isChunkLoadError(error);

  // 새 버전이 배포되어 옛 화면 코드를 못 불러온 경우는 한 번만 자동으로 새로고침한다
  useEffect(() => {
    if (!chunkError) return;
    const now = Date.now();
    if (shouldAutoReload(readLastReload(), now) && markReload(now)) {
      window.location.reload();
    }
  }, [chunkError]);

  return (
    <main className="mx-auto flex min-h-dvh max-w-page flex-col items-center justify-center gap-4 px-6 text-center">
      <CircleAlert size={32} aria-hidden="true" className="text-danger" />
      <h1 className="text-2xl font-bold text-heading">
        {chunkError ? '새 버전이 나왔어요' : '화면을 보여 드리지 못했어요'}
      </h1>
      <p className="max-w-120 leading-relaxed text-ink-soft">
        {chunkError
          ? '사이트가 업데이트되어 이 화면을 다시 불러와야 해요. 새로고침해 주세요.'
          : '잠시 문제가 생겼어요. 새로고침하거나 메인으로 돌아가 다시 시도해 주세요.'}
      </p>
      <div className="mt-2 flex flex-wrap justify-center gap-2">
        <Button size="lg" onClick={() => window.location.reload()}>
          새로고침
        </Button>
        {/* 라우터가 오류 상태라 앱 안 이동 대신 주소를 새로 연다 */}
        <Button
          size="lg"
          variant="secondary"
          onClick={() => window.location.assign('/')}
        >
          메인으로 가기
        </Button>
      </div>
    </main>
  );
}
