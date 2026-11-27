import { useEffect, type RefObject } from 'react';

// 화면 위에 붙은 묶음(체험 띠 + 상단바)의 높이를 재서 CSS 변수 --sticky-offset에 넣는다.
// index.css의 html scroll-padding-top이 이 값을 읽어, 키보드 포커스·앵커 이동 때 요소가 묶음 밑에 숨지 않게 한다.
// offsetHeight는 transform으로 숨긴 상단바도 자리 높이 그대로라, 다시 나타나도 가리지 않는다
export function useStickyOffset(ref: RefObject<HTMLElement | null>): void {
  useEffect(() => {
    const element = ref.current;
    if (!element) return;
    const root = document.documentElement;
    const update = () =>
      root.style.setProperty('--sticky-offset', `${element.offsetHeight}px`);
    update();
    // 체험 띠가 생기거나 사라질 때, 창 폭이 바뀌어 상단바 높이가 달라질 때 다시 잰다
    const observer = new ResizeObserver(update);
    observer.observe(element);
    return () => {
      observer.disconnect();
      root.style.removeProperty('--sticky-offset');
    };
  }, [ref]);
}
