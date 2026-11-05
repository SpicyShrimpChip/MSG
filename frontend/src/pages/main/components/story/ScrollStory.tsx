import { useRef, useState, type ReactNode } from 'react';
import {
  AnimatePresence,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
} from 'motion/react';
import * as m from 'motion/react-m';
import { Lock } from 'lucide-react';
import { FADE_UP, SWEEP_TRANSITION } from '@/constants';
import PrivacyNotice from '../PrivacyNotice';
import { SceneMotionContext } from './sceneMotionContext';
import StartPaths from './StartPaths';
import { STORY_STEPS } from './StoryScenes';

const TOTAL = STORY_STEPS.length;
// 다음 장면으로 넘어가기까지 스크롤할 양 (화면 높이 대비). 11/6: 76vh → 55vh로 줄임
const STEP_SCROLL_VH = 55;
const pad = (n: number) => String(n).padStart(2, '0');

// 다음 장면은 진행 방향 쪽에서 가림막을 걷듯 들어오고, 이전 장면은 반대쪽으로 밀려나며 흐려진다
const SCENE_VARIANTS = {
  enter: (direction: number) => ({
    clipPath: direction > 0 ? 'inset(0 0 0 100%)' : 'inset(0 100% 0 0)',
    x: `${direction * 6}%`,
    zIndex: 1,
  }),
  center: { clipPath: 'inset(0 0 0 0%)', x: 0, zIndex: 1 },
  exit: (direction: number) => ({
    opacity: 0,
    x: `${direction * -6}%`,
    zIndex: 0,
  }),
};

// 맥 창의 닫기·최소화·확대 버튼 색 (꾸밈용)
const WINDOW_DOTS = ['bg-window-close', 'bg-window-minimize', 'bg-window-zoom'];

// 장면을 실제 화면처럼 보이게 하는 브라우저 창 틀 (점 3개 + 주소줄)
function BrowserFrame({
  path,
  children,
}: {
  path: string;
  children: ReactNode;
}) {
  return (
    <figure className="relative m-0 overflow-hidden rounded-2xl border border-line bg-surface-raised shadow-[0_30px_60px_-20px] shadow-brand/30">
      <div className="flex h-10.5 items-center gap-2 border-b border-line bg-surface-muted px-4">
        {WINDOW_DOTS.map((color) => (
          <span
            key={color}
            aria-hidden="true"
            className={`size-3 rounded-full ${color}`}
          />
        ))}
        <span className="mx-auto flex h-6.5 items-center gap-1.5 rounded-full border border-line bg-surface-raised px-3.5 text-xs text-ink-muted">
          <Lock size={11} strokeWidth={2.4} aria-hidden="true" />
          ai-lms{path}
        </span>
        <span aria-hidden="true" className="w-11.5" />
      </div>
      {children}
    </figure>
  );
}

// 창 뒤로 카드 두 장을 겹쳐 깊이감을 준다
function StackedCards({ children }: { children: ReactNode }) {
  return (
    <div className="relative pt-7">
      <span
        aria-hidden="true"
        className="absolute inset-x-[9%] top-0 h-30 rounded-2xl border border-line bg-primary-soft"
      />
      <span
        aria-hidden="true"
        className="absolute inset-x-[4.5%] top-3.5 h-30 rounded-2xl border border-line bg-surface-muted"
      />
      {children}
    </div>
  );
}

// 넓은 화면: 구간을 지나는 동안 무대를 화면에 고정해 두고, 스크롤한 만큼 단계를 넘긴다
function PinnedStory() {
  const trackRef = useRef<HTMLDivElement>(null);
  const [view, setView] = useState({ step: 0, direction: 1 });
  const { step, direction } = view;
  const { scrollYProgress } = useScroll({
    target: trackRef,
    offset: ['start start', 'end end'],
  });

  useMotionValueEvent(scrollYProgress, 'change', (progress) => {
    const next = Math.min(TOTAL - 1, Math.floor(progress * TOTAL));
    setView((current) =>
      current.step === next
        ? current
        : { step: next, direction: next > current.step ? 1 : -1 },
    );
  });

  // 단계 이름을 누르면 그 단계가 보이는 스크롤 위치로 옮긴다 (가운데쯤에 멈춰 앞뒤로 조금 움직여도 넘어가지 않게)
  const goTo = (index: number) => {
    const track = trackRef.current;
    if (!track) return;
    const top = track.getBoundingClientRect().top + window.scrollY;
    const scrollable = track.offsetHeight - window.innerHeight;
    window.scrollTo({
      top: top + (scrollable * (index + 0.5)) / TOTAL,
      behavior: 'smooth',
    });
  };

  const current = STORY_STEPS[step];

  return (
    <div
      ref={trackRef}
      // 고정된 무대 한 화면 + 단계마다 STEP_SCROLL_VH만큼
      style={{ height: `calc(100svh + ${TOTAL * STEP_SCROLL_VH}vh)` }}
    >
      <div className="sticky top-0 grid h-svh grid-cols-[minmax(0,5fr)_minmax(0,6fr)] items-center gap-16 py-12">
        <div className="flex flex-col gap-10">
          {/* 큰 단계 번호 + 제목 + 설명 */}
          <div className="min-h-64">
            <AnimatePresence mode="wait" initial={false}>
              <m.div key={step} {...FADE_UP} className="flex flex-col gap-4">
                <p className="flex items-baseline gap-2">
                  <span className="text-6xl leading-none font-extrabold tracking-tighter text-primary">
                    {pad(step + 1)}
                  </span>
                  <span className="text-lg font-bold text-line-strong">
                    / {pad(TOTAL)}
                  </span>
                </p>
                <h3 className="text-[38px] leading-tight font-extrabold tracking-tight whitespace-pre-line text-heading">
                  {current.headline}
                </h3>
                <p className="text-[17px] leading-relaxed text-ink-soft">
                  {current.body}
                </p>
              </m.div>
            </AnimatePresence>
          </div>

          {/* 세로 타임라인: 스크롤한 만큼 선이 파랗게 차오른다 */}
          <ol className="relative">
            <span
              aria-hidden="true"
              className="absolute top-5.5 bottom-5.5 left-2.25 w-0.5 rounded-full bg-line"
            />
            <m.span
              aria-hidden="true"
              className="absolute top-5.5 bottom-5.5 left-2.25 w-0.5 origin-top rounded-full bg-primary"
              style={{ scaleY: scrollYProgress }}
            />
            {STORY_STEPS.map((item, index) => {
              const active = index === step;
              const reached = index <= step;
              return (
                <li key={item.title} className="relative">
                  <button
                    type="button"
                    onClick={() => goTo(index)}
                    aria-current={active ? 'step' : undefined}
                    className="group flex min-h-11 w-full items-center gap-3.5 rounded-lg text-left"
                  >
                    <span
                      className={`size-5 shrink-0 rounded-full border-2 transition-[background-color,border-color,box-shadow] ${
                        reached
                          ? 'border-primary bg-primary'
                          : 'border-line-strong bg-surface-raised'
                      } ${active ? 'shadow-[0_0_0_5px] shadow-primary-pill' : ''}`}
                    />
                    <span
                      className={
                        active
                          ? 'font-extrabold text-heading'
                          : 'font-medium text-ink-muted group-hover:text-heading'
                      }
                    >
                      {item.title}
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
        </div>

        <StackedCards>
          <BrowserFrame path={current.path}>
            {/* 들어오는 장면과 나가는 장면을 같은 칸에 겹쳐 둔다. 높이를 고정해 장면마다 창이 들썩이지 않게 한다 */}
            <div className="grid h-110 overflow-hidden p-6.5">
              <AnimatePresence initial={false} custom={direction}>
                <m.div
                  key={step}
                  custom={direction}
                  variants={SCENE_VARIANTS}
                  initial="enter"
                  animate="center"
                  exit="exit"
                  transition={SWEEP_TRANSITION}
                  className="[grid-area:1/1]"
                >
                  <current.Scene />
                </m.div>
              </AnimatePresence>
            </div>
          </BrowserFrame>
        </StackedCards>
      </div>
    </div>
  );
}

// 좁은 화면이나 "동작 줄이기": 고정 없이 단계를 차례로 쌓고, 장면은 마지막 모습으로 보여 준다
function StackedStory() {
  return (
    <SceneMotionContext value={false}>
      <ol className="flex flex-col gap-14">
        {STORY_STEPS.map((item, index) => (
          <li key={item.title} className="flex flex-col gap-5">
            <div className="flex flex-col gap-2.5">
              <p className="flex items-baseline gap-1.5">
                <span className="text-4xl leading-none font-extrabold tracking-tighter text-primary">
                  {pad(index + 1)}
                </span>
                <span className="text-body font-bold text-ink-muted">
                  {item.title}
                </span>
              </p>
              <h3 className="text-2xl leading-snug font-extrabold tracking-tight whitespace-pre-line text-heading">
                {item.headline}
              </h3>
              <p className="leading-relaxed text-ink-soft">{item.body}</p>
            </div>
            <BrowserFrame path={item.path}>
              <div className="p-5">
                <item.Scene />
              </div>
            </BrowserFrame>
          </li>
        ))}
      </ol>
    </SceneMotionContext>
  );
}

// 메인 소개 6단계: 대화 가져오기 → 개인정보 가리기 → 강점·약점 → 판단 근거 → 맞춤 순서 → 나아졌는지 확인
function ScrollStory() {
  const reducedMotion = useReducedMotion();

  return (
    <section aria-labelledby="story-title">
      <div className="mx-auto flex max-w-page flex-col gap-10 px-6 pt-24 pb-22">
        <div className="flex flex-col gap-2.5">
          <p className="text-sm font-extrabold tracking-wide text-primary">
            이렇게 동작해요
          </p>
          <h2
            id="story-title"
            className="text-3xl font-extrabold tracking-tight text-heading"
          >
            대화 기록이 공부 순서가 되기까지
          </h2>
          <p className="text-ink-soft">
            AI 대화 기록이 적어도 괜찮아요. 자료로 시작하면 퀴즈 기록이 쌓이는
            대로 같은 방식으로 분석해요.
          </p>
        </div>

        {reducedMotion ? (
          <StackedStory />
        ) : (
          <>
            <div className="max-lg:hidden">
              <PinnedStory />
            </div>
            <div className="lg:hidden">
              <StackedStory />
            </div>
          </>
        )}

        <StartPaths />
        <PrivacyNotice />
      </div>
    </section>
  );
}

export default ScrollStory;
