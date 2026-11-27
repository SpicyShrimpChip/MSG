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
    icon: <Upload size={20} aria-hidden="true" />,
  },
  {
    to: '/analysis/import',
    title: 'AI 대화 가져오기',
    description: 'ChatGPT·Claude 파일',
    icon: <MessageSquare size={20} aria-hidden="true" />,
  },
  {
    to: '/analysis',
    title: '학습 분석',
    description: '숙련도와 근거 보기',
    icon: <ChartColumn size={20} aria-hidden="true" />,
  },
  {
    to: '/curriculum',
    title: '맞춤 커리큘럼',
    description: '보완할 개념부터',
    icon: <ListChecks size={20} aria-hidden="true" />,
  },
];

// 첫 화면 아래 바로가기 4칸 (주 메뉴와 같은 학습 흐름 순서: 모으기 → 진단 → 공부).
// 첫 화면 장면이 주인공이라 상자·그림자 없이 위 구분선 하나로 조용하게 둔다
function QuickLinks() {
  return (
    <nav aria-label="바로가기" className="mx-auto max-w-page px-6">
      <ul className="grid grid-cols-2 border-t border-line lg:grid-cols-4">
        {LINKS.map((link) => (
          <li key={link.title}>
            <Link
              to={link.to}
              className="group flex h-full items-start gap-3 py-5 pr-4 md:py-6"
            >
              <span className="mt-0.5 shrink-0 text-ink-muted group-hover:text-primary">
                {link.icon}
              </span>
              <span>
                <span className="block font-semibold text-heading group-hover:text-primary">
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
