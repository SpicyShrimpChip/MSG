import { LoaderCircle } from 'lucide-react';

// 기다리는 동안 도는 아이콘. 꾸밈용이라 화면 낭독기에는 숨긴다(진행 상태는 옆 글자로 알린다)
function Spinner({ size = 16 }: { size?: number }) {
  return (
    <LoaderCircle size={size} className="animate-spin" aria-hidden="true" />
  );
}

export default Spinner;
