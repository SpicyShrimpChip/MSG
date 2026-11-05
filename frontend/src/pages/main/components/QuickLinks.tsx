import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChartColumn, ListChecks, MessageSquare, Upload } from 'lucide-react';

const LINKS: {
  to: string;
  title: string;
  description: string;
  icon: ReactNode;
}[] = [
  {
    to: '/materials/upload',
    title: '자료 올리기',
    description: '필기·노트로 시작',
    icon: <Upload size={22} aria-hidden="true" />,
  },
  {
    to: '/analysis/import',
    title: 'AI 대화 가져오기',
    description: 'ChatGPT·Claude 파일',
    icon: <MessageSquare size={22} aria-hidden="true" />,
  },
  {
    to: '/analysis',
    title: '학습 분석',
    description: '숙련도와 근거 보기',
    icon: <ChartColumn size={22} aria-hidden="true" />,
  },
  {
    to: '/curriculum',
    title: '맞춤 커리큘럼',
    description: '보완할 개념부터',
    icon: <ListChecks size={22} aria-hidden="true" />,
  },
];

// 첫 화면 띠 아래쪽에 겹쳐 놓는 바로가기 4칸 (주 메뉴와 같은 학습 흐름 순서: 모으기 → 진단 → 공부)
function QuickLinks() {
  return (
    <nav
      aria-label="바로가기"
      className="relative mx-auto -mt-14 max-w-page px-6"
    >
      {/* 칸 사이 1px 틈으로 구분선을 그린다 (열 수가 바뀌어도 선이 맞는다) */}
      <ul className="grid grid-cols-1 gap-px overflow-hidden rounded-2xl bg-line shadow-xl shadow-brand/10 sm:grid-cols-2 lg:grid-cols-4">
        {LINKS.map((link) => (
          <li key={link.title} className="bg-surface-raised">
            <Link
              to={link.to}
              className="flex h-full items-center gap-3.5 px-6 py-6 hover:bg-surface-muted"
            >
              <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
                {link.icon}
              </span>
              <span>
                <span className="block font-bold text-heading">
                  {link.title}
                </span>
                <span className="mt-0.5 block text-caption text-ink-muted">
                  {link.description}
                </span>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}

export default QuickLinks;
