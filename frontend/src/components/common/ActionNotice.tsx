import { useEffect, useRef } from 'react';

interface ActionNoticeProps {
  // id는 알릴 때마다 늘리는 수(같은 문구를 연달아 알려도 다시 포커스하게)
  notice: { id: number; text: string } | null;
}

// 삭제처럼 누른 버튼이 사라지는 동작 뒤에 결과를 알리는 한 줄.
// 그려질 때마다 이 문단으로 포커스를 옮겨, 포커스가 body로 빠지지 않고 화면 낭독기가 문구를 읽게 한다.
// 포커스로 읽히므로 role="status"는 달지 않는다(두 번 읽힌다)
function ActionNotice({ notice }: ActionNoticeProps) {
  const ref = useRef<HTMLParagraphElement>(null);
  const id = notice?.id;

  useEffect(() => {
    if (id !== undefined) ref.current?.focus();
  }, [id]);

  if (!notice) return null;
  return (
    <p ref={ref} tabIndex={-1} className="text-body text-ink-soft">
      {notice.text}
    </p>
  );
}

export default ActionNotice;
