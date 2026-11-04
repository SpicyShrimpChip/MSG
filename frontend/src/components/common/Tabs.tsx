import {
  useId,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import Chip from './Chip';

export interface TabItem {
  id: string;
  label: string;
  // 탭 이름 옆 작은 표시. 예: '준비 중'
  badge?: string;
  content: ReactNode;
}

interface TabsProps {
  label: string;
  tabs: TabItem[];
  // 처음 열 탭 (주소의 ?tab= 등). 없거나 맞는 탭이 없으면 첫 탭
  initialId?: string | null;
}

// 접근성 탭: 좌우 화살표로 탭 이동, 선택된 탭만 Tab 키 순서에 들어간다
function Tabs({ label, tabs, initialId }: TabsProps) {
  const baseId = useId();
  const [selectedId, setSelectedId] = useState(
    tabs.find((tab) => tab.id === initialId)?.id ?? tabs[0]?.id,
  );
  const tabRefs = useRef<(HTMLButtonElement | null)[]>([]);

  const handleKeyDown = (e: KeyboardEvent, index: number) => {
    const offset = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    if (!offset) return;
    e.preventDefault();
    const next = (index + offset + tabs.length) % tabs.length;
    setSelectedId(tabs[next].id);
    tabRefs.current[next]?.focus();
  };

  return (
    <div className="flex flex-col gap-6">
      <div
        role="tablist"
        aria-label={label}
        className="flex flex-wrap gap-1 border-b border-line"
      >
        {tabs.map((tab, index) => {
          const selected = tab.id === selectedId;
          return (
            <button
              key={tab.id}
              ref={(el) => {
                tabRefs.current[index] = el;
              }}
              type="button"
              role="tab"
              id={`${baseId}-tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`${baseId}-panel-${tab.id}`}
              tabIndex={selected ? 0 : -1}
              onClick={() => setSelectedId(tab.id)}
              onKeyDown={(e) => handleKeyDown(e, index)}
              className={`-mb-px inline-flex items-center gap-1.5 border-b-3 px-4 py-3 font-medium ${
                selected
                  ? 'border-primary text-heading'
                  : 'border-transparent text-ink-muted hover:text-heading'
              }`}
            >
              {tab.label}
              {tab.badge && <Chip className="font-normal">{tab.badge}</Chip>}
            </button>
          );
        })}
      </div>
      {tabs.map((tab) => (
        <div
          key={tab.id}
          role="tabpanel"
          id={`${baseId}-panel-${tab.id}`}
          aria-labelledby={`${baseId}-tab-${tab.id}`}
          hidden={tab.id !== selectedId}
        >
          {tab.content}
        </div>
      ))}
    </div>
  );
}

export default Tabs;
