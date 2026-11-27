import { useState, type KeyboardEvent } from 'react';
import { Chip, FIELD_CLASS } from '@/components';
import { addKeywords, cn } from '@/utils';

interface KeywordInputProps {
  id: string;
  keywords: string[];
  onChange: (keywords: string[]) => void;
}

// 칩 방식 키워드 입력: Enter·쉼표·칸 밖으로 나가면 추가, 빈 칸에서 Backspace면 마지막 칩 삭제
function KeywordInput({ id, keywords, onChange }: KeywordInputProps) {
  const [draft, setDraft] = useState('');
  const [error, setError] = useState<string>();

  const commit = () => {
    if (!draft.trim()) return;
    const result = addKeywords(keywords, draft);
    onChange(result.keywords);
    setError(result.error);
    setDraft('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중 Enter는 글자 확정용이라 무시한다
    if (e.nativeEvent.isComposing) return;
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault();
      commit();
    } else if (e.key === 'Backspace' && !draft && keywords.length > 0) {
      onChange(keywords.slice(0, -1));
    }
  };

  const remove = (keyword: string) => {
    onChange(keywords.filter((item) => item !== keyword));
    setError(undefined);
  };

  return (
    <div className="flex flex-col gap-1.5">
      <div
        className={cn(
          'flex min-h-10 flex-wrap items-center gap-1.5 px-2 py-1.5',
          FIELD_CLASS,
        )}
      >
        {keywords.map((keyword) => (
          <Chip
            key={keyword}
            onRemove={() => remove(keyword)}
            removeLabel={`${keyword} 키워드 삭제`}
          >
            {keyword}
          </Chip>
        ))}
        <input
          id={id}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={handleKeyDown}
          onBlur={commit}
          placeholder={keywords.length === 0 ? '프로세스, 스케줄링' : ''}
          aria-describedby={`${id}-error`}
          className="h-7 min-w-24 flex-1 bg-transparent px-1 text-body outline-hidden placeholder:text-ink-muted"
        />
      </div>
      <p
        id={`${id}-error`}
        aria-live="polite"
        className="text-caption text-danger"
      >
        {error}
      </p>
    </div>
  );
}

export default KeywordInput;
