import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence } from 'motion/react';
import * as m from 'motion/react-m';
import { CircleCheck, X } from 'lucide-react';
import { FADE_UP } from '@/constants';

// 마이페이지에서 탈퇴한 뒤 메인으로 올 때 넘기는 표시 (개인정보는 넣지 않는다)
export interface MainLocationState {
  withdrawn?: boolean;
}

// 안내가 저절로 닫히기까지 (밀리초). 닫기 버튼으로 바로 닫을 수도 있다
const AUTO_HIDE_MS = 8000;

// 탈퇴가 끝났다는 한 번만 보이는 안내. 화면 아래쪽에 떠 있다가 닫힌다
function WithdrawnToast() {
  const location = useLocation();
  const navigate = useNavigate();
  const withdrawn = (location.state as MainLocationState | null)?.withdrawn;
  const [open, setOpen] = useState(withdrawn === true);

  // 새로고침·뒤로 가기로 다시 뜨지 않도록 주소의 표시는 바로 지운다 (지금 안내는 그대로 둔다)
  useEffect(() => {
    if (withdrawn) navigate(location.pathname, { replace: true, state: null });
  }, [withdrawn, navigate, location.pathname]);

  useEffect(() => {
    if (!open) return;
    const timer = window.setTimeout(() => setOpen(false), AUTO_HIDE_MS);
    return () => window.clearTimeout(timer);
  }, [open]);

  return (
    <div
      role="status"
      className="pointer-events-none fixed inset-x-4 bottom-6 z-50 flex justify-center print:hidden"
    >
      <AnimatePresence>
        {open && (
          <m.div
            {...FADE_UP}
            className="pointer-events-auto flex max-w-md items-start gap-3 rounded-xl border border-line bg-surface-raised p-4 text-body text-heading shadow-lg shadow-brand/15"
          >
            <CircleCheck
              size={20}
              className="mt-0.5 shrink-0 text-primary"
              aria-hidden="true"
            />
            <p className="min-w-0 leading-relaxed">
              탈퇴했어요. 계정과 학습 데이터를 모두 지웠어요.
            </p>
            <button
              type="button"
              onClick={() => setOpen(false)}
              aria-label="안내 닫기"
              className="-m-1 shrink-0 rounded p-1 text-ink-muted hover:text-heading"
            >
              <X size={18} aria-hidden="true" />
            </button>
          </m.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default WithdrawnToast;
