import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, MessageSquare, Upload } from 'lucide-react';
import * as m from 'motion/react-m';
import { DemoStartButton } from '@/components';
import { FADE_UP } from '@/constants';
import { useAuthStore } from '@/store';

const PATHS: {
  to: string;
  who: string;
  title: string;
  description: string;
  icon: ReactNode;
}[] = [
  {
    to: '/analysis/import',
    who: 'AI 대화 기록이 많다면',
    title: 'AI 대화 가져오기',
    description: 'ChatGPT·Claude 내보내기 파일을 올리면 바로 분석해요.',
    icon: <MessageSquare size={22} aria-hidden="true" />,
  },
  {
    to: '/materials/upload',
    who: 'AI 대화 기록이 적다면',
    title: '자료 올리고 시작하기',
    description: '필기·정리 노트를 올리고 퀴즈로 지금 수준부터 확인해요.',
    icon: <Upload size={22} aria-hidden="true" />,
  },
];

// 스크롤 소개를 다 본 뒤 바로 시작할 수 있게 두 진입 경로(사용자 유형 A·B)를 보여 준다.
// 메인 소개는 대부분 아직 가입하지 않은 사람이 보므로, 로그인 전에는 카드가 회원가입으로 가고
// 가입·로그인을 마치면 고른 화면으로 이어진다 (SignupPage → LoginPage가 from을 넘겨준다)
function StartPaths() {
  const signedIn = useAuthStore((state) => state.status === 'authenticated');

  return (
    <m.div
      initial={FADE_UP.initial}
      whileInView={FADE_UP.animate}
      viewport={{ once: true, amount: 0.4 }}
      transition={FADE_UP.transition}
      className="flex flex-col gap-8 rounded-3xl bg-section p-5 md:p-12"
    >
      <div className="flex flex-col gap-2">
        <h3 className="text-2xl font-extrabold tracking-tight text-heading md:text-3xl">
          내 공부 기록으로 시작해 볼까요?
        </h3>
        <p className="text-ink-soft">
          {signedIn
            ? '어느 쪽으로 시작해도 기록이 쌓이면 같은 방식으로 분석해요.'
            : '나에게 맞는 쪽을 고르면 회원가입 뒤 그 화면으로 바로 이어져요.'}
        </p>
      </div>

      <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {PATHS.map((path) => (
          <li key={path.to}>
            <Link
              to={signedIn ? path.to : '/signup'}
              state={signedIn ? undefined : { from: path.to }}
              className="group flex h-full items-center gap-4 rounded-2xl border border-line bg-surface-raised p-4.5 shadow-sm hover:border-primary-pill hover:shadow-lg hover:shadow-brand/10 md:p-6"
            >
              <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
                {path.icon}
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-1">
                <span className="text-caption font-medium text-ink-muted">
                  {path.who}
                </span>
                <span className="text-lg font-extrabold text-heading">
                  {path.title}
                </span>
                <span className="text-body text-ink-soft">
                  {path.description}
                </span>
              </span>
              <ArrowRight
                size={20}
                aria-hidden="true"
                className="shrink-0 text-primary transition-transform group-hover:translate-x-1 motion-reduce:transition-none"
              />
            </Link>
          </li>
        ))}
      </ul>

      {!signedIn && (
        <p className="text-body text-ink-soft">
          이미 계정이 있나요?{' '}
          <Link
            to="/login"
            className="font-bold text-primary underline-offset-4 hover:underline"
          >
            로그인
          </Link>
          {/* 공개 배포(목업 모드)에서만 보인다 */}
          <DemoStartButton className="ml-4 font-bold text-primary underline-offset-4 hover:underline" />
        </p>
      )}
    </m.div>
  );
}

export default StartPaths;
