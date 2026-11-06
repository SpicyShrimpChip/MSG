import { Button, SubPage } from '@/components';

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

function AboutPage() {
  return (
    <SubPage
      title="서비스 소개"
      description="AI와 공부한 기록에서 아는 것과 모르는 것을 찾아 드려요"
    >
      <section className="flex flex-col gap-3 rounded-2xl bg-section p-6 md:p-8">
        <h2 className="text-xl font-extrabold text-heading">왜 만들었나요</h2>
        <p className="leading-relaxed text-ink-soft">
          AI로 공부하면 대화가 여러 서비스에 흩어지고, 새 대화를 열 때마다 같은
          배경 설명을 반복하게 돼요. 공부는 많이 했는데 무엇을 알고 어디가
          부족한지는 알기 어려워요. 흩어진 학습 기록을 모아 개념별 이해 정도를
          보여 주고, 부족한 부분부터 다시 공부할 수 있게 학습 순서를 제안해요.
        </p>
        <p className="leading-relaxed text-ink-soft">
          2026년 11월에 50명(대학생 35명·고등학생 10명·기타 5명)에게 물어보니,
          해당 문항에 답한 49명 중 41명이 새 대화를 열 때 어디까지 배웠는지 다시
          설명한 적이 있었고, 35명은 다음에 무엇을 공부할지 막막했던 적이
          있었어요.
        </p>
      </section>

      <section className="flex flex-col gap-4">
        <h2 className="text-xl font-extrabold text-heading">이렇게 지켜요</h2>
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

      <div className="flex flex-wrap gap-3">
        <Button to="/analysis/import" size="lg">
          대화 가져오기
        </Button>
        <Button to="/materials/upload" variant="secondary" size="lg">
          자료 올리기
        </Button>
      </div>
    </SubPage>
  );
}

export default AboutPage;
