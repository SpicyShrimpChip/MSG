import { useState, type SubmitEvent, type ReactNode } from 'react';
import { Button, Chip, TextInput } from '@/components';

interface WordListProps {
  id: string;
  label: string;
  hint: string;
  placeholder: string;
  words: string[];
  onChange: (words: string[]) => void;
  // 칸 아래에 붙는 안내·체크 상자 (저장한 단어 불러옴, 이 브라우저에 기억하기 등)
  children?: ReactNode;
}

// 내 이름·가릴 단어처럼 여러 개를 넣고 빼는 칸
function WordList({
  id,
  label,
  hint,
  placeholder,
  words,
  onChange,
  children,
}: WordListProps) {
  const [draft, setDraft] = useState('');

  const add = (e: SubmitEvent) => {
    e.preventDefault();
    const word = draft.trim();
    if (word && !words.includes(word)) onChange([...words, word]);
    setDraft('');
  };

  return (
    <form onSubmit={add} className="flex flex-col gap-2">
      <label htmlFor={id} className="text-body font-bold text-heading">
        {label}
      </label>
      <p id={`${id}-hint`} className="text-caption text-ink-muted">
        {hint}
      </p>
      <div className="flex gap-2">
        <TextInput
          id={id}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder={placeholder}
          aria-describedby={`${id}-hint`}
          className="min-w-0 flex-1"
        />
        <Button type="submit" variant="secondary" disabled={!draft.trim()}>
          더하기
        </Button>
      </div>
      {words.length > 0 && (
        <ul aria-label={label} className="flex flex-wrap gap-1.5">
          {words.map((word) => (
            <li key={word}>
              <Chip
                onRemove={() => onChange(words.filter((w) => w !== word))}
                removeLabel={`${word} 빼기`}
              >
                {word}
              </Chip>
            </li>
          ))}
        </ul>
      )}
      {children}
    </form>
  );
}

export default WordList;
