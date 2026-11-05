import { Link } from 'react-router-dom';
import { Lock } from 'lucide-react';
import { DemoStartButton } from '@/components';
import MasteryMeter from '@/pages/analysis/components/MasteryMeter';
import type { MasteryStage } from '@/types';

// 첫 화면 오른쪽 예시 카드. 실제 사용자 데이터가 아니라 화면 소개용 예시라서 "예시 화면" 표시를 붙인다.
// 대시보드처럼 숫자 점수 없이 숙련도 단계만 보여 준다
const SAMPLE_CONCEPTS: { name: string; level: MasteryStage }[] = [
  { name: '대칭키 암호', level: 'strong' },
  { name: '해시 함수', level: 'normal' },
  { name: '공개키 암호', level: 'needs_work' },
];

function SampleCard() {
  return (
    <figure className="m-0 min-w-0 flex-[1_1_420px]">
      <div className="flex flex-col gap-5 rounded-2xl bg-surface-raised p-6 shadow-2xl shadow-brand/40">
        <div className="flex flex-wrap items-baseline justify-between gap-3">
          <div>
            <p className="text-caption font-medium text-ink-muted">
              정보보호개론
            </p>
            <p className="mt-1 text-lg font-bold text-heading">개념별 숙련도</p>
          </div>
          <p className="text-caption text-ink-muted">대화 9건 · 퀴즈 22문제</p>
        </div>
        <ul className="flex flex-col gap-4">
          {SAMPLE_CONCEPTS.map((concept) => (
            <li
              key={concept.name}
              className="flex flex-wrap items-center justify-between gap-2 text-body"
            >
              <span className="font-medium text-heading">{concept.name}</span>
              <MasteryMeter level={concept.level} size="lg" />
            </li>
          ))}
        </ul>
        <p className="rounded-xl bg-surface-muted px-4 py-3 text-body leading-relaxed text-ink-soft">
          <span className="font-bold text-heading">판단 근거</span> · 10/20
          대화에서 암호화에 쓰는 키를 다시 물어봄, 10/22 퀴즈 3문제 중 1문제
          정답
        </p>
      </div>
      <figcaption className="mt-2.5 text-right text-caption text-on-brand-hover">
        예시 화면이에요
      </figcaption>
    </figure>
  );
}

// 메인 첫 화면: 짙은 파랑 띠 위에 소개 문구와 두 진입 경로(사용자 유형 A·B), 오른쪽에 예시 카드
function Hero() {
  return (
    // 넓은 화면에서는 상단바 자리(80px) 밑까지 파란 띠를 끌어올려 알약이 띠 위에 떠 있게 한다 (위에 검은 띠가 남지 않게)
    <section className="bg-brand text-on-brand lg:-mt-20">
      <div className="mx-auto flex max-w-page flex-wrap items-center gap-14 px-6 pt-20 pb-32 lg:pt-40">
        <div className="min-w-0 flex-[1_1_460px]">
          <p className="mb-5 inline-flex rounded-full bg-on-brand/12 px-3 py-1.5 text-caption font-medium text-on-brand-hover">
            학습 자료 + ChatGPT·Claude 대화 기록 분석
          </p>
          <h1 className="text-4xl leading-tight font-extrabold tracking-tight md:text-5xl">
            AI와 공부한 기록에서
            <br />
            아는 것과 모르는 것을
            <br />
            찾아 드려요
          </h1>
          <p className="mt-6 max-w-130 text-lg leading-relaxed text-on-brand-hover">
            학습 자료와 생성형 AI 대화를 함께 분석해 개념별 강점과 보완할 점을
            보여 주고, 공부할 순서를 다시 짜 드려요.
          </p>
          <div className="mt-9 flex flex-wrap gap-2.5">
            <Link
              to="/materials/upload"
              className="inline-flex h-13 items-center rounded-xl bg-on-brand px-6 font-extrabold text-brand hover:bg-on-brand-hover"
            >
              자료 올리고 시작하기
            </Link>
            <Link
              to="/analysis/import"
              className="inline-flex h-13 items-center rounded-xl border border-on-brand/50 px-6 font-bold text-on-brand hover:bg-on-brand/10"
            >
              AI 대화 가져오기
            </Link>
            <DemoStartButton className="inline-flex h-13 items-center rounded-xl px-4 font-bold text-on-brand-hover underline underline-offset-4 hover:text-on-brand" />
          </div>
          <p className="mt-5 flex items-center gap-1.5 text-body text-on-brand-hover">
            <Lock size={15} aria-hidden="true" />
            원본 대화 파일은 서버로 보내지 않아요
          </p>
        </div>
        <SampleCard />
      </div>
    </section>
  );
}

export default Hero;
