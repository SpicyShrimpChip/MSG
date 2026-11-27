import { Link } from 'react-router-dom';
import { ArrowRight, Lock, Upload } from 'lucide-react';
import { DemoStartButton } from '@/components';
import MasteryMeter from '@/pages/analysis/components/MasteryMeter';
import type { MasteryStage } from '@/types';

// 가져온 대화에서 키워드를 칠하는 형광펜과 같은 모양 (analysis/components/import/marks.ts의 keyword)
const MARK_CLASS = 'rounded bg-highlight px-0.5 text-on-highlight';

// 근거 문장이 이어지는 개념 아래에 보여 줄 나머지 개념 (예시)
const OTHER_CONCEPTS: { name: string; level: MasteryStage }[] = [
  { name: '해시 함수', level: 'normal' },
  { name: '대칭키 암호', level: 'strong' },
];

// 첫 화면 오른쪽 장면: 내가 AI에게 했던 질문 → 과목 키워드 형광펜 → 개념 단계.
// 실제 사용자 데이터가 아니라 화면 소개용 예시라서 "예시 화면" 표시를 붙인다.
// 대시보드처럼 숫자 점수 없이 숙련도 단계와 판단 근거만 보여 준다
function EvidenceScene() {
  return (
    <figure className="m-0 min-w-0">
      <div className="overflow-hidden rounded-2xl border border-line bg-surface-raised shadow-lg shadow-shade/6">
        <div className="px-6 pt-6 pb-5">
          <div className="flex justify-between gap-3 text-caption text-ink-muted">
            <span>가져온 대화 · 정보보호 3주차 복습</span>
            <span>10/20</span>
          </div>
          <p className="mt-3 text-caption text-ink-muted">내 질문</p>
          <p className="mt-1 text-lg leading-relaxed text-heading">
            그럼 <mark className={MARK_CLASS}>공개키</mark>로{' '}
            <mark className={MARK_CLASS}>암호화</mark>하면 누가 다 풀 수 있는
            거야? 아까랑 반대 아니야?
          </p>
          <p className="mt-3 border-l-2 border-line-strong pl-3 text-body text-ink-soft">
            공개키로 잠근 내용은 짝이 되는 개인키로만 열 수 있어요…
          </p>
        </div>
        <p className="flex items-center gap-2.5 border-y border-line bg-surface-muted px-6 py-3 text-caption text-ink-soft">
          <span
            className="size-2 shrink-0 rounded-full bg-highlight ring-1 ring-shade/15"
            aria-hidden="true"
          />
          <span>
            과목 키워드 2개에 줄을 그었어요
            <ArrowRight
              size={13}
              className="mx-1 inline align-[-2px]"
              aria-label="그래서"
            />
            <span className="font-semibold text-heading">공개키 암호</span>의
            근거로 써요
          </span>
        </p>
        <div className="px-6 pt-5 pb-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-lg font-semibold text-heading">
              공개키 암호
            </span>
            <MasteryMeter level="needs_work" size="lg" />
          </div>
          <p className="mt-2 text-caption leading-relaxed text-ink-soft">
            <span className="font-semibold text-heading">판단 근거</span> ·
            10/20 대화에서 암호화에 쓰는 키를 다시 물어봄, 10/22 퀴즈 3문제 중
            1문제 정답
          </p>
          <ul className="mt-3">
            {OTHER_CONCEPTS.map((concept) => (
              <li
                key={concept.name}
                className="flex h-12 items-center justify-between gap-2 border-t border-line text-body text-ink-soft"
              >
                {concept.name}
                <MasteryMeter level={concept.level} size="lg" />
              </li>
            ))}
          </ul>
        </div>
      </div>
      <figcaption className="mt-2.5 text-right text-caption text-ink-muted">
        예시 화면이에요
      </figcaption>
    </figure>
  );
}

// 메인 첫 화면: 왼쪽에 소개 문구와 두 진입 경로(사용자 유형 A·B), 오른쪽에 "질문 → 형광펜 → 개념 단계" 장면.
// 첫 화면은 "무엇을 해 주는지" 한 장면만 보여 주고, "어떻게 하는지"는 아래 스크롤 소개가 단계로 나눠 보여 준다
function Hero() {
  return (
    <section>
      <div className="mx-auto grid max-w-page items-center gap-12 px-6 pt-10 pb-14 lg:grid-cols-[1.05fr_1fr] lg:gap-14 lg:pt-16 lg:pb-20">
        <div className="min-w-0">
          <p className="mb-5 text-body text-ink-soft">
            학습 자료와 ChatGPT·Claude 대화 기록으로
          </p>
          <h1 className="text-4xl leading-tight font-semibold tracking-tight text-heading md:text-5xl">
            AI와 공부한 기록에서
            <br />
            아는 것과 모르는 것을
            <br />
            찾아 드려요
          </h1>
          <p className="mt-6 max-w-130 text-lg leading-relaxed text-ink-soft">
            학습 자료와 생성형 AI 대화를 함께 분석해 개념별 강점과 보완할 점을
            보여 주고, 공부할 순서를 다시 짜 드려요.
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-2.5">
            <Link
              to="/materials/upload"
              className="inline-flex h-12 items-center gap-2 rounded-xl bg-primary px-5 font-semibold text-on-primary hover:bg-primary-hover"
            >
              <Upload size={18} aria-hidden="true" />
              자료 올리고 시작하기
            </Link>
            <Link
              to="/analysis/import"
              className="inline-flex h-12 items-center rounded-xl border border-line-strong bg-surface-raised px-5 font-semibold text-heading hover:bg-surface-muted"
            >
              AI 대화 가져오기
            </Link>
            <DemoStartButton className="inline-flex h-12 items-center px-3 font-semibold text-ink-soft underline decoration-line-strong underline-offset-4 hover:text-heading" />
          </div>
          <p className="mt-5 flex items-center gap-1.5 text-caption text-ink-muted">
            <Lock size={14} aria-hidden="true" />
            원본 대화 파일은 서버로 보내지 않아요
          </p>
        </div>
        <EvidenceScene />
      </div>
    </section>
  );
}

export default Hero;
