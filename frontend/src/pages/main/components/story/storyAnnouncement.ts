// 메인 스크롤 소개 한 단계를 화면 낭독기가 읽을 한 문장으로 만든다.
// storySteps.ts(장면 컴포넌트를 가져온다)는 가져오지 않는다(node 테스트에서 화면 코드를 불러오지 않게)
export function storyStepText(
  step: { title: string; headline: string; body: string },
  index: number,
  total: number,
): string {
  const headline = step.headline.replace(/\n/g, ' ');
  return `${index + 1}단계 · 전체 ${total}단계, ${step.title}. ${headline} ${step.body}`;
}
