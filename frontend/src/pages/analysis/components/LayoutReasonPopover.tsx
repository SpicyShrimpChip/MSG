import { useEffect, useId, useRef, useState } from 'react';
import { Sparkles } from 'lucide-react';
import { AnimatePresence } from 'motion/react';
import * as m from 'motion/react-m';
import { AiNote } from '@/components';
import { FADE_UP, MOTION_DURATION, MOTION_EASE } from '@/constants';

interface LayoutReasonPopoverProps {
  // 영역 제목. 화면 읽기 프로그램용 버튼 이름에 쓴다
  title: string;
  reason: string;
}

// "왜 여기에?" 말풍선: 마우스를 올리거나 키보드로 가면 뜨고, 클릭·탭하면 고정된다.
// 말풍선 위로 마우스를 옮겨도 유지되고, Esc나 바깥 클릭으로 닫힌다 (WCAG 1.4.13)
function LayoutReasonPopover({ title, reason }: LayoutReasonPopoverProps) {
  const popoverId = useId();
  const wrapperRef = useRef<HTMLDivElement>(null);
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const [pinned, setPinned] = useState(false);
  // Esc로 닫으면 마우스·포커스가 그대로여도 다시 들어올 때까지 닫아 둔다
  const [dismissed, setDismissed] = useState(false);

  const open = !dismissed && (pinned || hovered || focused);

  useEffect(() => {
    if (!pinned) return;
    const handlePointerDown = (e: PointerEvent) => {
      if (!wrapperRef.current?.contains(e.target as Node)) setPinned(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [pinned]);

  return (
    <div
      ref={wrapperRef}
      className="relative shrink-0"
      onMouseEnter={() => {
        setHovered(true);
        setDismissed(false);
      }}
      onMouseLeave={() => setHovered(false)}
      onKeyDown={(e) => {
        if (e.key === 'Escape' && open) {
          setPinned(false);
          setDismissed(true);
        }
      }}
    >
      <button
        type="button"
        aria-expanded={open}
        aria-controls={popoverId}
        onClick={() => {
          setPinned((value) => !value || dismissed);
          setDismissed(false);
        }}
        onFocus={() => {
          setFocused(true);
          setDismissed(false);
        }}
        onBlur={() => setFocused(false)}
        className="inline-flex items-center gap-1 rounded-full bg-ai-chip px-2.5 py-1 text-caption font-medium text-ai hover:bg-ai-chip-hover"
      >
        <Sparkles size={14} aria-hidden="true" />이 자리인 이유
        {/* 접근 이름이 보이는 글로 시작해야 음성 제어("이 자리인 이유 클릭")가 먹는다 */}
        <span className="sr-only">: {title}</span>
      </button>
      <AnimatePresence>
        {open && (
          // 버튼과 말풍선 사이를 padding으로 이어서, 마우스를 옮기는 동안 닫히지 않게 한다
          <m.div
            id={popoverId}
            initial={FADE_UP.initial}
            animate={FADE_UP.animate}
            exit={FADE_UP.exit}
            transition={{ duration: MOTION_DURATION.fast, ease: MOTION_EASE }}
            className="absolute top-full right-0 z-20 w-72 max-w-[calc(100vw-3rem)] pt-2"
          >
            <div className="rounded-xl shadow-md">
              <AiNote title="AI가 이 자리에 둔 이유">{reason}</AiNote>
            </div>
          </m.div>
        )}
      </AnimatePresence>
    </div>
  );
}

export default LayoutReasonPopover;
