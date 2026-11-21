import type { ReactNode } from 'react';
import { BookOpenCheck, ChartColumn, FolderOpen } from 'lucide-react';
import { StartPaths, SubPage } from '@/components';

// 설문(2026-11-01~06, 50명 응답, 4~15번 문항은 49명 응답). 숫자는 설문 결과라 AI 보라색을 쓰지 않는다
const SURVEY_RESPONDENTS = 49;
const SURVEY_STATS = [
  {
    count: 41,
    label: '새 대화를 열 때 어디까지 배웠는지 다시 설명한 적이 있어요',
  },
  { count: 35, label: '다음에 무엇을 공부할지 막막했던 적이 있어요' },
];

// 메뉴 순서(학습자료 → 학습분석 → 맞춤학습)와 같은 흐름
const STEPS: {
  menu: string;
  title: string;
  description: string;
  icon: ReactNode;
}[] = [
  {
    menu: '학습자료',
    title: '모으기',
    description:
      '과목과 키워드를 등록하고, 필기·정리 노트를 올리거나 AI와 공부한 대화를 가져와요.',
    icon: <FolderOpen size={22} aria-hidden="true" />,
  },
  {
    menu: '학습분석',
    title: '진단하기',
    description:
      '학습 대시보드에서 세 AI의 판단과 개념별 숙련도, 그렇게 판단한 근거를 함께 봐요.',
    icon: <ChartColumn size={22} aria-hidden="true" />,
  },
  {
    menu: '맞춤학습',
    title: '공부하고 확인하기',
    description:
      '맞춤 커리큘럼 순서대로 공부하고 퀴즈·사전·사후평가로 확인해요. 다시 분석하면 숙련도와 학습 순서가 새로 계산돼요.',
    icon: <BookOpenCheck size={22} aria-hidden="true" />,
  },
];

const PRINCIPLES = [
  {
    title: '원본 대화 파일은 서버로 보내지 않아요',
    description:
      '내 브라우저에서 학습 관련 대화만 고르고 개인정보를 가린 뒤, 내가 선택한 대화만 보내요.',
  },
  {
    title: '숙련도는 정해진 규칙으로 계산해요',
    description:
      '개념별 숙련도는 대화 분류 결과와 퀴즈 결과를 정해진 규칙으로 계산해 네 단계로 보여 줘요. AI는 숙련도를 정하지 않고 근거를 설명해요.',
  },
  {
    title: 'AI 세 곳의 판단을 비교해요',
    description:
      'GPT, Claude, Gemini가 서로의 답을 보지 않고 따로 판단해요. 의견이 갈린 개념은 하나로 정하지 않고 갈렸다고 보여 드려요.',
  },
  {
    title: '판단 근거를 함께 보여 드려요',
    description:
      '강점이나 보완할 점마다 어떤 대화와 퀴즈 기록을 보고 판단했는지 확인할 수 있어요.',
  },
];

// 이용약관 제7조와 같은 내용. 기능이 바뀌면 함께 고친다
const LIMITS = [
  'AI가 만든 퀴즈·설명은 틀릴 수 있어요. "AI" 표시와 보라색으로 구분하니, 중요한 내용은 학습 자료 원문으로 확인해 주세요.',
  '숙련도는 공부 방향을 잡기 위한 참고 지표예요. 성적이나 실력을 증명하지 않아요.',
  '근거가 적으면 단정하지 않고 "근거 부족"으로 보여 주고, 퀴즈를 몇 문제 더 풀면 되는지 알려 드려요.',
  'AI 대화는 지금 ChatGPT와 Claude의 공식 내보내기 파일만 가져올 수 있어요.',
];

function AboutPage() {
  return (
    <SubPage
      title="서비스 소개"
      description="AI와 공부한 기록에서 아는 것과 모르는 것을 찾아 드려요"
    >
      <section
        aria-labelledby="about-why-title"
        className="flex flex-col gap-5 rounded-2xl bg-section p-6 md:p-8"
      >
        <div className="flex flex-col gap-3">
          <h2
            id="about-why-title"
            className="text-xl font-extrabold text-heading"
          >
            왜 만들었나요
          </h2>
          <p className="leading-relaxed text-ink-soft">
            AI로 공부하면 대화가 여러 서비스에 흩어지고, 새 대화를 열 때마다
            같은 배경 설명을 반복하게 돼요. 공부는 많이 했는데 무엇을 알고
            어디가 부족한지는 알기 어려워요. 흩어진 학습 기록을 모아 개념별 이해
            정도를 보여 주고, 부족한 부분부터 다시 공부할 수 있게 학습 순서를
            제안해요.
          </p>
        </div>
        <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          {SURVEY_STATS.map((stat) => (
            <li
              key={stat.label}
              className="flex flex-col gap-1.5 rounded-xl bg-surface-raised p-5"
            >
              <p className="text-heading">
                <span className="text-3xl font-extrabold text-primary">
                  {stat.count}명
                </span>
                <span className="ml-1 text-body text-ink-muted">
                  / {SURVEY_RESPONDENTS}명
                </span>
              </p>
              <p className="text-body leading-relaxed text-ink-soft">
                {stat.label}
              </p>
            </li>
          ))}
        </ul>
        <p className="text-caption text-ink-muted">
          2026년 11월 설문, 50명 응답(대학생 35명·고등학생 10명·기타 5명). 위
          문항은 {SURVEY_RESPONDENTS}명이 답했어요.
        </p>
      </section>

      <section
        aria-labelledby="about-steps-title"
        className="flex flex-col gap-4"
      >
        <h2
          id="about-steps-title"
          className="text-xl font-extrabold text-heading"
        >
          이렇게 써요
        </h2>
        <ol className="grid grid-cols-1 gap-4 md:grid-cols-3">
          {STEPS.map((step, index) => (
            <li
              key={step.title}
              className="flex flex-col gap-3 rounded-2xl border border-line p-6"
            >
              <div className="flex items-center gap-3">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
                  {step.icon}
                </span>
                <div className="flex flex-col">
                  <span className="text-caption font-medium text-ink-muted">
                    {index + 1}단계 · {step.menu} 메뉴
                  </span>
                  <h3 className="font-bold text-heading">{step.title}</h3>
                </div>
              </div>
              <p className="text-body leading-relaxed text-ink-soft">
                {step.description}
              </p>
            </li>
          ))}
        </ol>
      </section>

      <section
        aria-labelledby="about-principles-title"
        className="flex flex-col gap-4"
      >
        <h2
          id="about-principles-title"
          className="text-xl font-extrabold text-heading"
        >
          이렇게 지켜요
        </h2>
        <ul className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {PRINCIPLES.map((item) => (
            <li
              key={item.title}
              className="flex flex-col gap-2 rounded-2xl border border-line p-6"
            >
              <h3 className="font-bold text-heading">{item.title}</h3>
              <p className="text-body leading-relaxed text-ink-soft">
                {item.description}
              </p>
            </li>
          ))}
        </ul>
      </section>

      <section
        aria-labelledby="about-limits-title"
        className="flex flex-col gap-3 rounded-2xl border border-line p-6 md:p-8"
      >
        <h2
          id="about-limits-title"
          className="text-xl font-extrabold text-heading"
        >
          이런 한계가 있어요
        </h2>
        <ul className="flex list-disc flex-col gap-1.5 pl-5 leading-relaxed text-ink-soft">
          {LIMITS.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      </section>

      <StartPaths headingAs="h2" />
    </SubPage>
  );
}

export default AboutPage;
