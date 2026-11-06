import { useDeferredValue, useMemo, useState } from 'react';
import { ChevronDown, Info, Plus, Search, SearchX, X } from 'lucide-react';
import {
  Button,
  Card,
  Chip,
  EmptyState,
  SelectInput,
  Notice,
  TextInput,
} from '@/components';
import { useCourses } from '@/hooks';
import type { Course } from '@/types';
import {
  assignToCourses,
  conversationKey,
  formatDate,
  cn,
  formatSemester,
  partCountOf,
  searchConversation,
  sortConversations,
  type AssignedConversation,
  type ConversationOrder,
  type SearchHit,
  type ParsedConversation,
} from '@/utils';
import ConversationView from './ConversationView';
import CourseQuickForm from './CourseQuickForm';

// 대화마다 들어갈 과목과 보낼지 여부 (키: conversationKey)
export type ConversationChoices = Record<
  string,
  { courseId: number; selected: boolean }
>;

interface SelectStepProps {
  conversations: ParsedConversation[];
  // 이전에 고른 값. 없으면 키워드가 걸린 대화를 모두 고른 상태로 시작한다
  initialChoices: ConversationChoices | null;
  onBack: () => void;
  onNext: (choices: ConversationChoices) => void;
}

function defaultChoices(assigned: AssignedConversation[]): ConversationChoices {
  const choices: ConversationChoices = {};
  for (const { conversation, matches } of assigned) {
    choices[conversationKey(conversation)] = {
      courseId: matches[0].courseId,
      selected: true,
    };
  }
  return choices;
}

function SelectStep({
  conversations,
  initialChoices,
  onBack,
  onNext,
}: SelectStepProps) {
  const { data: courses } = useCourses();
  const { assigned, unmatchedCount } = useMemo(
    () => assignToCourses(conversations, courses),
    [conversations, courses],
  );
  // 사용자가 바꾼 값만 들고, 나머지는 기본값(키워드가 걸린 첫 과목, 선택)으로 채운다.
  // 이 화면에서 키워드를 더하면 새로 걸린 대화가 생기므로 처음 한 번만 정해 두지 않는다
  const [edited, setEdited] = useState<ConversationChoices>(
    () => initialChoices ?? {},
  );
  const choices = useMemo(() => {
    const merged = defaultChoices(assigned);
    for (const { conversation, matches } of assigned) {
      const key = conversationKey(conversation);
      const choice = edited[key];
      // 키워드를 지워 그 과목에 더는 안 걸리면 기본 과목으로 돌린다
      if (choice && matches.some((match) => match.courseId === choice.courseId))
        merged[key] = choice;
    }
    return merged;
  }, [assigned, edited]);
  // 이 자리에서 과목을 추가하거나 키워드를 더하는 폼 (화면을 나가면 읽은 파일이 사라진다)
  const [courseFormOpen, setCourseFormOpen] = useState(false);
  const [query, setQuery] = useState('');
  // 본문까지 찾으므로 입력이 밀리지 않게 검색은 한 박자 늦게 따라간다
  const deferredQuery = useDeferredValue(query);
  const [order, setOrder] = useState<ConversationOrder>('latest');
  const [openKey, setOpenKey] = useState<string | null>(null);
  // 나눠 보낼 대화가 있는지 (어떤 대화인지는 보내기 단계에서 보여 준다)
  const hasLongConversation = useMemo(
    () => assigned.some(({ conversation }) => partCountOf(conversation) > 1),
    [assigned],
  );

  const hasKeywords = courses.some((course) => course.keywords.length > 0);
  // 파일 고르기 전에 과목을 등록하게 하지만, 다른 창에서 키워드를 모두 지웠을 때를 대비해 남겨 둔다
  if (!hasKeywords) {
    return (
      <Card
        as="section"
        aria-labelledby="import-select"
        className="flex flex-col gap-5 p-6 md:p-8"
      >
        <h2 id="import-select" className="text-xl font-extrabold text-heading">
          먼저 과목과 키워드를 등록해 주세요
        </h2>
        <p className="text-body text-ink-soft">
          내 과목에 등록한 키워드가 들어 있는 대화만 골라서 가져와요. 읽은
          파일은 그대로 두니 다시 올리지 않아도 돼요.
        </p>
        <CourseQuickForm courses={courses} />
      </Card>
    );
  }

  const selectedCount = Object.values(choices).filter(
    (choice) => choice.selected,
  ).length;

  const update = (key: string, patch: Partial<ConversationChoices[string]>) =>
    setEdited((prev) => ({
      ...prev,
      [key]: { ...(prev[key] ?? choices[key]), ...patch },
    }));

  // 제목이나 본문에 검색어가 있는 대화만, 고른 순서로
  const hits = new Map<string, SearchHit>();
  for (const { conversation } of assigned) {
    const hit = searchConversation(conversation, deferredQuery);
    if (hit) hits.set(conversationKey(conversation), hit);
  }
  const visible = sortConversations(
    assigned.filter(({ conversation }) =>
      hits.has(conversationKey(conversation)),
    ),
    (item) => item.conversation,
    order,
  );

  return (
    <Card
      as="section"
      aria-labelledby="import-select"
      className="flex flex-col gap-6 p-6 md:p-8"
    >
      <div className="flex flex-col gap-2">
        <h2 id="import-select" className="text-xl font-extrabold text-heading">
          공부한 대화 고르기
        </h2>
        <p className="text-body text-ink-soft">
          내 과목 키워드가 들어 있는 대화 {assigned.length}개를 과목별로
          나눴어요. 보내지 않을 대화는 선택을 풀어 주세요.
        </p>
        {hasLongConversation && (
          <Notice icon={Info} size="caption" className="mt-1">
            <p>
              메시지가 아주 많은 대화는 보낼 때 나눠서 보내요. 빠지는 메시지는
              없고, 어떤 대화인지는 보내기 단계에서 알려 드려요.
            </p>
          </Notice>
        )}
      </div>

      {assigned.length === 0 ? (
        <EmptyState
          icon={<SearchX size={24} aria-hidden="true" />}
          title="공부한 대화를 찾지 못했어요"
          description="등록한 키워드가 들어 있는 대화가 없어요. 아래에서 키워드를 더하거나, 다른 내보내기 파일을 골라 보세요."
        />
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <TextInput
              type="search"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="제목이나 내용으로 찾기"
              aria-label="제목이나 내용으로 찾기"
              className="max-w-80 min-w-0 flex-1"
            />
            <div
              role="group"
              aria-label="정렬"
              className="inline-flex overflow-hidden rounded-lg border border-line-strong"
            >
              {(
                [
                  ['latest', '최신순'],
                  ['title', '가나다순'],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  aria-pressed={order === value}
                  onClick={() => setOrder(value)}
                  className={cn(
                    'px-3 py-2 text-caption',
                    order === value
                      ? 'bg-primary-pill font-bold text-heading'
                      : 'text-ink-soft hover:bg-surface-muted',
                  )}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          {courses.map((course) => (
            <CourseGroup
              key={course.id}
              course={course}
              courses={courses}
              items={visible.filter(
                ({ conversation }) =>
                  choices[conversationKey(conversation)]?.courseId ===
                  course.id,
              )}
              choices={choices}
              hits={hits}
              query={deferredQuery}
              openKey={openKey}
              onToggleOpen={(key) => setOpenKey(openKey === key ? null : key)}
              onUpdate={update}
              onToggleAll={(keys, selected) =>
                setEdited((prev) => {
                  const next = { ...prev };
                  for (const key of keys)
                    next[key] = { ...(next[key] ?? choices[key]), selected };
                  return next;
                })
              }
            />
          ))}
          {visible.length === 0 && (
            <p className="text-body text-ink-muted">
              검색어가 들어간 대화가 없어요. 다른 단어로 찾아 보세요.
            </p>
          )}
        </>
      )}

      {unmatchedCount > 0 && (
        <p className="text-body text-ink-muted">
          키워드가 들어 있지 않은 대화 {unmatchedCount}개는 목록에 넣지
          않았어요. 빠진 대화가 있다면 과목에 키워드를 더해 보세요. 읽은 파일은
          그대로 두고 다시 나눠요.
        </p>
      )}

      {courseFormOpen ? (
        <div className="flex flex-col gap-4 rounded-xl border border-line-strong p-5">
          <div className="flex items-center justify-between gap-3">
            <h3 className="font-bold text-heading">과목·키워드 더하기</h3>
            <Button
              variant="secondary"
              onClick={() => setCourseFormOpen(false)}
            >
              <X size={16} aria-hidden="true" />
              닫기
            </Button>
          </div>
          <CourseQuickForm
            courses={courses}
            onDone={() => setCourseFormOpen(false)}
          />
        </div>
      ) : (
        (unmatchedCount > 0 || assigned.length === 0) && (
          <div>
            <Button variant="secondary" onClick={() => setCourseFormOpen(true)}>
              <Plus size={18} aria-hidden="true" />
              과목·키워드 더하기
            </Button>
          </div>
        )
      )}

      <div className="flex flex-wrap justify-between gap-3">
        <Button variant="secondary" onClick={onBack}>
          이전
        </Button>
        <Button onClick={() => onNext(choices)} disabled={selectedCount === 0}>
          다음: 가린 내용 확인 ({selectedCount}개)
        </Button>
      </div>
    </Card>
  );
}

interface CourseGroupProps {
  course: Course;
  courses: Course[];
  items: AssignedConversation[];
  choices: ConversationChoices;
  // 대화별 검색 결과 (키: conversationKey)
  hits: Map<string, SearchHit>;
  query: string;
  // 펼쳐서 보고 있는 대화
  openKey: string | null;
  onToggleOpen: (key: string) => void;
  onUpdate: (key: string, patch: Partial<ConversationChoices[string]>) => void;
  onToggleAll: (keys: string[], selected: boolean) => void;
}

function CourseGroup({
  course,
  courses,
  items,
  choices,
  hits,
  query,
  openKey,
  onToggleOpen,
  onUpdate,
  onToggleAll,
}: CourseGroupProps) {
  if (items.length === 0) return null;
  const keys = items.map(({ conversation }) => conversationKey(conversation));
  const selectedCount = keys.filter((key) => choices[key].selected).length;
  const allSelected = selectedCount === keys.length;
  const headingId = `import-course-${course.id}`;

  return (
    <section aria-labelledby={headingId} className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end justify-between gap-2 border-b border-line pb-2">
        <h3 id={headingId} className="text-lg font-bold text-heading">
          {course.name}{' '}
          <span className="text-body font-normal text-ink-muted">
            {formatSemester(course.semester)}
          </span>
        </h3>
        <div className="flex items-center gap-3">
          <span className="text-caption text-ink-muted">
            {keys.length}개 중 {selectedCount}개 선택
          </span>
          <button
            type="button"
            onClick={() => onToggleAll(keys, !allSelected)}
            className="text-caption font-medium text-primary underline"
          >
            {allSelected ? '모두 선택 해제' : '모두 선택'}
          </button>
        </div>
      </div>
      <ul className="flex flex-col divide-y divide-line">
        {items.map(({ conversation, matches }) => {
          const key = conversationKey(conversation);
          const choice = choices[key];
          const match =
            matches.find((item) => item.courseId === choice.courseId) ??
            matches[0];
          const checkboxId = `import-${key}`;
          const date = formatDate(conversation.createdAt);
          const snippet = hits.get(key)?.snippet;
          const open = openKey === key;
          const panelId = `import-view-${key}`;
          return (
            <li key={key} className="flex flex-col gap-3 py-3">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="flex min-w-0 items-start gap-3">
                  <input
                    id={checkboxId}
                    type="checkbox"
                    checked={choice.selected}
                    onChange={(e) =>
                      onUpdate(key, { selected: e.target.checked })
                    }
                    className="mt-1 size-4 shrink-0"
                  />
                  <div className="flex min-w-0 flex-col gap-1.5">
                    <label
                      htmlFor={checkboxId}
                      className="font-medium text-heading"
                    >
                      {conversation.title}
                    </label>
                    <p className="flex flex-wrap gap-x-3 text-caption text-ink-muted">
                      <span>
                        {conversation.source === 'chatgpt'
                          ? 'ChatGPT'
                          : 'Claude'}
                      </span>
                      {date && <span>{date}</span>}
                      <span>메시지 {conversation.messages.length}개</span>
                    </p>
                    <ul
                      aria-label="찾은 키워드"
                      className="flex flex-wrap gap-1.5"
                    >
                      {match.matchedKeywords.map((keyword) => (
                        <li key={keyword}>
                          <Chip>{keyword}</Chip>
                        </li>
                      ))}
                    </ul>
                    {snippet && (
                      <p className="flex min-w-0 items-center gap-1.5 text-caption text-ink-soft">
                        <Search
                          size={13}
                          className="shrink-0"
                          aria-label="내용에서 찾음"
                        />
                        <span className="truncate">
                          {snippet.before}
                          <mark className="rounded bg-highlight px-0.5 text-on-highlight">
                            {snippet.match}
                          </mark>
                          {snippet.after}
                        </span>
                      </p>
                    )}
                  </div>
                </div>
                <div className="flex shrink-0 items-center gap-3 sm:flex-col sm:items-end">
                  {matches.length > 1 && (
                    <SelectInput
                      aria-label={`${conversation.title} 과목 바꾸기`}
                      value={choice.courseId}
                      onChange={(e) =>
                        onUpdate(key, { courseId: Number(e.target.value) })
                      }
                      className="w-full sm:w-44"
                    >
                      {matches.map(({ courseId }) => (
                        <option key={courseId} value={courseId}>
                          {courses.find((item) => item.id === courseId)?.name}
                        </option>
                      ))}
                    </SelectInput>
                  )}
                  <button
                    type="button"
                    aria-expanded={open}
                    aria-controls={panelId}
                    onClick={() => onToggleOpen(key)}
                    className="flex items-center gap-1 text-caption font-medium whitespace-nowrap text-primary"
                  >
                    {open ? '접기' : '대화 보기'}
                    <ChevronDown
                      size={16}
                      aria-hidden="true"
                      className={open ? 'rotate-180' : undefined}
                    />
                  </button>
                </div>
              </div>
              {open && (
                <div id={panelId} className="sm:pl-7">
                  <ConversationView
                    source={conversation.source}
                    messages={conversation.messages}
                    keywords={[...match.matchedKeywords, query]}
                  />
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export default SelectStep;
