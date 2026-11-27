import { useRef, useState, type ReactNode } from 'react';
import {
  AnimatePresence,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
} from 'motion/react';
import * as m from 'motion/react-m';
import { FADE_UP, SWEEP_TRANSITION } from '@/constants';
import { StartPaths } from '@/components';
import { cn } from '@/utils';
import PrivacyNotice from '../PrivacyNotice';
import { SceneMotionContext } from './sceneMotionContext';
import { STORY_STEPS } from './storySteps';

const TOTAL = STORY_STEPS.length;
// 다음 장면으로 넘어가기까지 스크롤할 양 (화면 높이 대비). 11/6: 76vh → 55vh로 줄임
const STEP_SCROLL_VH = 55;

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

// 장면을 담는 카드. 가짜 브라우저 틀(창 점·주소줄) 없이 어느 화면인지 이름표만 단다.
// 주인공은 장면이라 틀은 흰 카드 + 얇은 선 + 작은 그림자로 조용하게 둔다
function SceneCard({
  screen,
  children,
}: {
  screen: string;
  children: ReactNode;
}) {
  return (
    <figure className="m-0 overflow-hidden rounded-2xl border border-line bg-surface-raised shadow-lg shadow-shade/6">
      <figcaption className="px-6 pt-5 text-caption text-ink-muted">
        {screen}
      </figcaption>
      {children}
    </figure>
  );
}

// 넓은 화면: 구간을 지나는 동안 무대를 화면에 고정해 두고, 스크롤한 만큼 단계를 넘긴다
function PinnedStory({ header }: { header: ReactNode }) {
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
      {/* 머리말을 무대 안에 두어 장면과 항상 같이 움직인다. 머리말+장면을 한 묶음으로 남은 높이 가운데에 둬서 제목이 장면 바로 위에 온다.
          위 여백은 상단바 자리(80px)인데, 상단바가 숨으면(Header가 <html data-header-hidden>) 아래와 같게 줄여 장면이 아래로 쏠리지 않게 한다 */}
      <div className="sticky top-0 flex h-svh flex-col justify-center gap-6 pt-20 pb-6 in-data-[header-hidden]:pt-6 motion-safe:transition-[padding-top] [@media(min-height:900px)]:gap-12">
        {header}
        <div className="grid min-h-0 grid-cols-[minmax(0,4fr)_minmax(0,7fr)] items-center gap-14">
          <div className="flex flex-col gap-8">
            {/* 단계 표시 + 제목 + 한두 줄 설명 */}
            <div className="min-h-52">
              <AnimatePresence mode="wait" initial={false}>
                <m.div key={step} {...FADE_UP} className="flex flex-col gap-4">
                  <p className="text-caption text-ink-muted">
                    {step + 1}단계 · 전체 {TOTAL}단계
                  </p>
                  <h3 className="text-4xl leading-tight font-semibold tracking-tight whitespace-pre-line text-heading">
                    {current.headline}
                  </h3>
                  <p className="text-lg leading-relaxed text-ink-soft">
                    {current.body}
                  </p>
                </m.div>
              </AnimatePresence>
            </div>

            {/* 단계 이름 목록: 지금 단계만 왼쪽 잉크 선과 진한 글자로 짚는다 */}
            <ol className="flex flex-col">
              {STORY_STEPS.map((item, index) => {
                const active = index === step;
                return (
                  <li key={item.title}>
                    <button
                      type="button"
                      onClick={() => goTo(index)}
                      aria-current={active ? 'step' : undefined}
                      className={cn(
                        'flex min-h-10 w-full items-center border-l-2 pl-4 text-left transition-colors',
                        active
                          ? 'border-primary font-semibold text-heading'
                          : 'border-line text-ink-muted hover:text-heading',
                      )}
                    >
                      {item.title}
                    </button>
                  </li>
                );
              })}
            </ol>
          </div>

          <SceneCard screen={current.screen}>
            {/* 들어오는 장면과 나가는 장면을 같은 칸에 겹쳐 둔다. 높이를 고정해 장면마다 창이 들썩이지 않게 한다 */}
            <div className="grid h-110 overflow-hidden px-6.5 pt-4 pb-6.5">
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
          </SceneCard>
        </div>
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
              <p className="text-caption text-ink-muted">
                {index + 1}단계 · {item.title}
              </p>
              <h3 className="text-2xl leading-snug font-semibold tracking-tight whitespace-pre-line text-heading">
                {item.headline}
              </h3>
              <p className="leading-relaxed text-ink-soft">{item.body}</p>
            </div>
            <SceneCard screen={item.screen}>
              <div className="px-5 pt-3 pb-5">
                <item.Scene />
              </div>
            </SceneCard>
          </li>
        ))}
      </ol>
    </SceneMotionContext>
  );
}

// 소개 머리말. 넓은 화면 무대와 좁은 화면에 한 번씩 그리므로 id는 한쪽에만 붙인다
// (숨겨진 쪽의 제목이어도 section의 aria-labelledby 이름으로 읽힌다)
function intro(withId: boolean) {
  return (
    // 구간 이름표: 한 줄 제목 + 오른쪽 설명 + 본문 폭 끝까지 가는 선. 주인공은 아래 장면이라 작게 둔다
    <div className="flex flex-wrap items-baseline justify-between gap-x-8 gap-y-1.5 border-b border-line pb-4">
      <h2
        id={withId ? 'story-title' : undefined}
        className="text-xl font-semibold tracking-tight text-heading"
      >
        대화 기록이 공부 순서가 되기까지
      </h2>
      <p className="text-body text-ink-soft">
        AI 대화 기록이 적어도 괜찮아요. 자료로 시작해도 퀴즈 기록이 쌓이면 같은
        방식으로 분석해요.
      </p>
    </div>
  );
}

// 메인 소개 6단계: 대화 가져오기 → 개인정보 가리기 → 강점·약점 → 판단 근거 → 맞춤 순서 → 나아졌는지 확인
function ScrollStory() {
  const reducedMotion = useReducedMotion();

  return (
    <section aria-labelledby="story-title">
      <div className="mx-auto flex max-w-page flex-col gap-10 px-6 pt-24 pb-22">
        {reducedMotion ? (
          <div className="flex flex-col gap-10">
            {intro(true)}
            <StackedStory />
          </div>
        ) : (
          <>
            {/* 넓은 화면: 머리말이 장면 무대 안에 들어가 장면과 함께 고정된다 */}
            <div className="max-lg:hidden">
              <PinnedStory header={intro(true)} />
            </div>
            <div className="flex flex-col gap-10 lg:hidden">
              {intro(false)}
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
