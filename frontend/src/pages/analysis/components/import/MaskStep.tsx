import { useMemo, useState } from 'react';
import { ChevronDown, Eye, EyeOff, Lock } from 'lucide-react';
import { Button, Card, Chip } from '@/components';
import { cn } from '@/utils/cn';
import {
  MASK_LABELS,
  collectMaskedSpots,
  conversationKey,
  maskConversation,
  type MaskKind,
  type MaskOptions,
  type MaskedSpot,
  type ParsedConversation,
} from '@/utils';
import ConversationView from './ConversationView';
import WordList from './WordList';

interface MaskStepProps {
  conversations: ParsedConversation[];
  options: MaskOptions;
  onChangeOptions: (options: MaskOptions) => void;
  // 마이페이지에서 이 브라우저에 저장한 가릴 단어. 로그인 전이면 null (기억하기를 숨긴다)
  savedTerms: string[] | null;
  remember: boolean;
  onChangeRemember: (remember: boolean) => void;
  onBack: () => void;
  onNext: () => void;
}

// 가린 곳 앞뒤 글은 한 줄 미리 보기라서 마크다운 기호(**, `, # 등)를 뺀다
const withoutMarkdown = (text: string) => text.replace(/[*_`#>~]+/g, '');

interface MaskPreviewProps {
  id: string;
  original: ParsedConversation;
  // 가린 뒤의 대화
  masked: ParsedConversation;
  options: MaskOptions;
}

// 대화 하나를 펼쳤을 때: 가린 곳 목록(원래 글자는 눌러야 보임) + 채팅창 모양 미리 보기
function MaskPreview({ id, original, masked, options }: MaskPreviewProps) {
  const spots = useMemo(
    () => collectMaskedSpots(original, options),
    [original, options],
  );
  const [showOriginal, setShowOriginal] = useState(false);
  // "보기"로 고른 줄 하나. 가리는 규칙이 바뀌어 목록이 다시 만들어지면 고른 줄은 풀린다
  const [selected, setSelected] = useState<{
    spots: MaskedSpot[];
    index: number;
    nonce: number;
  } | null>(null);
  const selectedIndex = selected?.spots === spots ? selected.index : null;
  const selectedSpot = selectedIndex === null ? null : spots[selectedIndex];
  const focus = useMemo(
    () =>
      selected && selectedSpot && selectedSpot.messageIndex >= 0
        ? {
            index: selectedSpot.messageIndex,
            nonce: selected.nonce,
            markIndex: selectedSpot.labelIndex,
          }
        : null,
    [selected, selectedSpot],
  );

  // 같은 줄을 다시 누르면 풀고, 다른 줄을 누르면 앞 줄은 풀린다
  const toggleSpot = (index: number) =>
    setSelected((prev) =>
      prev?.spots === spots && prev.index === index
        ? null
        : { spots, index, nonce: (prev?.nonce ?? 0) + 1 },
    );

  return (
    <div id={id} className="mt-3 flex flex-col gap-3">
      {spots.length > 0 && (
        <div className="flex flex-col gap-2 rounded-xl bg-surface-muted p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <p className="font-bold text-heading">가린 곳 {spots.length}개</p>
            <button
              type="button"
              aria-pressed={showOriginal}
              onClick={() => setShowOriginal(!showOriginal)}
              className="flex items-center gap-1 text-caption font-medium text-primary"
            >
              {showOriginal ? (
                <EyeOff size={16} aria-hidden="true" />
              ) : (
                <Eye size={16} aria-hidden="true" />
              )}
              {showOriginal ? '원래 글자 숨기기' : '원래 글자 보기'}
            </button>
          </div>
          <ul className="flex flex-col divide-y divide-line border-t border-line">
            {spots.map((spot, i) => (
              <li
                // 가린 곳 순서는 바뀌지 않는다
                // eslint-disable-next-line react-x/no-array-index-key
                key={i}
                className={cn(
                  'grid grid-cols-[minmax(0,9rem)_minmax(0,1fr)_auto] items-center gap-3 py-2 text-caption',
                  selectedIndex === i &&
                    '-mx-2 rounded-lg bg-surface-raised px-2',
                )}
              >
                <span
                  className="truncate font-mono text-heading"
                  aria-label={
                    showOriginal || selectedIndex === i
                      ? undefined
                      : '숨긴 원래 글자'
                  }
                >
                  {showOriginal || selectedIndex === i
                    ? spot.original
                    : '••••••••'}
                </span>
                <span className="truncate text-ink-soft">
                  {spot.messageIndex < 0 && '제목: '}
                  {withoutMarkdown(spot.before)}
                  <mark className="rounded bg-primary-soft px-0.5 font-medium text-heading">
                    {MASK_LABELS[spot.kind]}
                  </mark>
                  {withoutMarkdown(spot.after)}
                </span>
                <button
                  type="button"
                  aria-pressed={selectedIndex === i}
                  aria-label={`${MASK_LABELS[spot.kind]} 보기`}
                  onClick={() => toggleSpot(i)}
                  className="font-medium text-primary"
                >
                  보기
                </button>
              </li>
            ))}
          </ul>
          <p className="flex items-start gap-1.5 text-caption text-ink-muted">
            <Lock size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
            원래 글자는 이 화면에서만 보여요. 서버로는 가린 글만 가요.
          </p>
        </div>
      )}
      <ConversationView
        source={masked.source}
        messages={masked.messages}
        focus={focus}
      />
    </div>
  );
}

function MaskStep({
  conversations,
  options,
  onChangeOptions,
  savedTerms,
  remember,
  onChangeRemember,
  onBack,
  onNext,
}: MaskStepProps) {
  const [openId, setOpenId] = useState<string | null>(null);

  const masked = useMemo(
    () =>
      conversations.map((conversation) => ({
        original: conversation,
        ...maskConversation(conversation, options),
      })),
    [conversations, options],
  );

  const totals = useMemo(() => {
    const counts: Partial<Record<MaskKind, number>> = {};
    for (const item of masked) {
      for (const [kind, count] of Object.entries(item.counts)) {
        counts[kind as MaskKind] = (counts[kind as MaskKind] ?? 0) + count;
      }
    }
    return counts;
  }, [masked]);
  const totalCount = masked.reduce((sum, item) => sum + item.totalCount, 0);

  return (
    <Card
      as="section"
      aria-labelledby="import-mask"
      className="flex flex-col gap-6 p-6 md:p-8"
    >
      <div className="flex flex-col gap-2">
        <h2 id="import-mask" className="text-xl font-extrabold text-heading">
          가린 내용 확인
        </h2>
        <p className="text-body text-ink-soft">
          이메일, 전화번호 같은 개인정보는 자동으로 가렸어요. 내 이름이나 더
          가리고 싶은 단어를 넣고, 보내기 전에 대화를 직접 확인해 주세요.
        </p>
      </div>

      <div className="grid gap-5 md:grid-cols-2">
        <WordList
          id="mask-names"
          label="내 이름"
          hint="대화 속에 쓴 이름을 [이름]으로 가려요. 영문 이름이나 별명도 함께 넣어 주세요."
          placeholder="예: 홍길동"
          words={options.names ?? []}
          onChange={(names) => onChangeOptions({ ...options, names })}
        />
        <WordList
          id="mask-terms"
          label="더 가릴 단어"
          hint="학교, 회사, 주소처럼 자동으로 찾지 못한 단어를 [가린 단어]로 가려요."
          placeholder="예: OO대학교"
          words={options.terms ?? []}
          onChange={(terms) => onChangeOptions({ ...options, terms })}
        >
          {savedTerms && (
            <div className="flex flex-col gap-2">
              <p className="text-caption text-ink-muted">
                {savedTerms.length > 0
                  ? `마이페이지에 저장한 단어 ${savedTerms.length}개를 불러왔어요. 새로 넣은 단어는 이번에만 써요.`
                  : '다른 기기에서 저장했다면 여기서 한 번 더 넣어 주세요. 새로 넣은 단어는 이번에만 써요.'}
              </p>
              <label className="flex items-start gap-2 text-body text-ink">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => onChangeRemember(e.target.checked)}
                  className="mt-1"
                />
                <span>이 브라우저에 기억하기</span>
              </label>
            </div>
          )}
        </WordList>
      </div>

      <div
        aria-live="polite"
        className="flex flex-col gap-2 rounded-xl bg-primary-soft p-4"
      >
        <p className="font-bold text-heading">
          대화 {conversations.length}개에서 {totalCount}곳을 가렸어요.
        </p>
        {totalCount > 0 && (
          <ul aria-label="가린 종류" className="flex flex-wrap gap-1.5">
            {(Object.keys(MASK_LABELS) as MaskKind[])
              .filter((kind) => totals[kind])
              .map((kind) => (
                <li key={kind}>
                  <Chip tone="primary">
                    {MASK_LABELS[kind]} {totals[kind]}개
                  </Chip>
                </li>
              ))}
          </ul>
        )}
      </div>

      <ul className="flex flex-col divide-y divide-line border-y border-line">
        {masked.map(({ original, conversation, totalCount: count }) => {
          const key = conversationKey(original);
          const open = openId === key;
          const panelId = `mask-preview-${key}`;
          return (
            <li key={key} className="py-3">
              <button
                type="button"
                aria-expanded={open}
                aria-controls={panelId}
                onClick={() => setOpenId(open ? null : key)}
                className="flex w-full items-center justify-between gap-3 text-left"
              >
                <span className="flex min-w-0 flex-col gap-0.5">
                  <span className="truncate font-medium text-heading">
                    {conversation.title}
                  </span>
                  <span className="text-caption text-ink-muted">
                    메시지 {conversation.messages.length}개 ·{' '}
                    {count > 0 ? `${count}곳 가림` : '가린 곳 없음'}
                  </span>
                </span>
                <span className="flex shrink-0 items-center gap-1 text-caption font-medium text-primary">
                  {open ? '접기' : '미리 보기'}
                  <ChevronDown
                    size={16}
                    aria-hidden="true"
                    className={open ? 'rotate-180' : undefined}
                  />
                </span>
              </button>
              {open && (
                <MaskPreview
                  id={panelId}
                  original={original}
                  masked={conversation}
                  options={options}
                />
              )}
            </li>
          );
        })}
      </ul>

      <p className="flex items-start gap-2 text-body text-ink-soft">
        <EyeOff size={18} className="mt-0.5 shrink-0" aria-hidden="true" />
        가리기 전의 글은 이 화면에서만 쓰고 서버로 보내지 않아요.
      </p>

      <div className="flex flex-wrap justify-between gap-3">
        <Button variant="secondary" onClick={onBack}>
          이전
        </Button>
        <Button onClick={onNext}>다음: 보내기</Button>
      </div>
    </Card>
  );
}

export default MaskStep;
