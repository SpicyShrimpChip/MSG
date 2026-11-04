import { useEffect, useRef, useState } from 'react';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { BookOpen, ChevronDown, LoaderCircle, Lock, Quote } from 'lucide-react';
import {
  ASSESSMENTS_QUERY_KEY,
  CURRICULUM_QUERY_KEY,
  MATERIALS_QUERY_KEY,
  STUDY_QUERY_KEY,
  USE_MOCK,
  createConceptStudyNote,
  extractMaterialConcepts,
  getConceptStudies,
  getMaterialConcepts,
  getQuizAiStatus,
} from '@/api';
import {
  AiNote,
  Button,
  Card,
  Chip,
  ErrorText,
  Notice,
  StudyStatusBadge,
} from '@/components';
import { useMe } from '@/hooks';
import type { ConceptStudy, MaterialConcept, QuizAiStatus } from '@/types';
import { cn } from '@/utils';
import ConceptStudyActions from './ConceptStudyActions';

// 본문을 읽을 수 있는 형식 (서버 MaterialTextExtractor와 같다). PDF는 글이 들어 있는 PDF만, 스캔본은 OCR이 없어 못 읽는다
export const READABLE_EXTENSIONS = ['txt', 'md', 'pptx', 'pdf'];
export const isReadable = (fileName: string) =>
  READABLE_EXTENSIONS.includes(fileName.split('.').pop()?.toLowerCase() ?? '');

export const CONTENT_DISABLED_MESSAGE =
  '학습자료 본문을 외부 AI로 보내는 기능은 운영 설정으로 꺼져 있어요. 처리 근거와 안내가 준비되면 켜져요. 지금은 과목 키워드로만 평가를 만들어요.';

// 자료 원문 인용 (서버가 본문에 있는지 확인한 문장, AI 글 아님 → 회색)
function SourceQuote({
  quote,
  location,
  label,
}: {
  quote: string;
  location: string | null;
  label: string;
}) {
  return (
    <figure className="flex gap-2 rounded-lg bg-surface-muted p-3 text-caption text-ink">
      <Quote
        size={14}
        className="mt-0.5 shrink-0 text-ink-muted"
        aria-hidden="true"
      />
      <div className="flex flex-col gap-1">
        <blockquote>{quote}</blockquote>
        <figcaption className="text-ink-soft">
          {label} · {location ?? '위치 표시 없음'} (자료 원문)
        </figcaption>
      </div>
    </figure>
  );
}

// 자세한 설명 (AI). 자료와의 연결 문장만 자료 원문이다
function StudyNoteView({ concept }: { concept: MaterialConcept }) {
  const note = concept.studyNote;
  if (!note) return null;
  return (
    <div className="flex flex-col gap-3">
      <AiNote title="자세한 설명">
        <p className="whitespace-pre-line">{note.explanation}</p>
      </AiNote>
      {(note.materialContext || note.contextQuote) && (
        <AiNote title="이 자료에서는 어떻게 이어지나요">
          <div className="flex flex-col gap-2">
            {note.materialContext && <p>{note.materialContext}</p>}
            {note.contextQuote && (
              <SourceQuote
                quote={note.contextQuote}
                location={note.contextLocation}
                label="연결되는 부분"
              />
            )}
          </div>
        </AiNote>
      )}
      {note.importance && (
        <AiNote title="왜 중요한가요">
          <p>{note.importance}</p>
        </AiNote>
      )}
      {note.example && (
        <AiNote title="예시로 이해하기">
          <p>{note.example}</p>
        </AiNote>
      )}
      <p className="text-caption text-ink-muted">
        {note.generatedBy.toUpperCase()}가 자료 발췌를 근거로 쓴 설명이에요.
        틀릴 수 있으니 자료 원문(회색 인용)과 함께 확인해 주세요.
      </p>
    </div>
  );
}

function ConceptCard({
  concept,
  materialId,
  courseId,
  blockedReason,
  defaultOpen,
  study,
}: {
  concept: MaterialConcept;
  materialId: number;
  courseId: number | null;
  blockedReason: string | null;
  defaultOpen: boolean;
  study: ConceptStudy | undefined;
}) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(defaultOpen);
  const [confirmed, setConfirmed] = useState(false);
  const ref = useRef<HTMLLIElement>(null);

  // 커리큘럼·검색에서 이 개념으로 왔으면 화면에 보이게 내린다
  useEffect(() => {
    if (defaultOpen) ref.current?.scrollIntoView({ block: 'start' });
  }, [defaultOpen]);

  const mutation = useMutation({
    mutationFn: () =>
      createConceptStudyNote(materialId, {
        conceptId: concept.conceptId,
        materialContentConsent: true,
      }),
    onSuccess: (next) => {
      queryClient.setQueryData<MaterialConcept[]>(
        [...MATERIALS_QUERY_KEY, materialId, 'concepts'],
        (prev) =>
          prev?.map((item) =>
            item.conceptId === next.conceptId ? next : item,
          ),
      );
      void queryClient.invalidateQueries({
        queryKey: [...MATERIALS_QUERY_KEY, materialId],
        exact: true,
      });
      void queryClient.invalidateQueries({ queryKey: CURRICULUM_QUERY_KEY });
      setConfirmed(false);
    },
  });

  const panelId = `concept-${materialId}-${concept.conceptId}`;

  return (
    <li ref={ref} className="scroll-mt-28">
      <Card
        className={cn(
          'flex flex-col gap-3 p-5',
          defaultOpen && 'border-primary',
        )}
      >
        <div className="flex flex-wrap items-center gap-2">
          <p className="text-lg font-bold text-heading">{concept.name}</p>
          <Chip tone={concept.courseKeyword ? 'primary' : 'neutral'}>
            {concept.courseKeyword
              ? '과목 키워드와 같음'
              : '자료에서 찾은 개념'}
          </Chip>
          {concept.studyNote && <Chip tone="neutral">자세한 설명 있음</Chip>}
          {study && <StudyStatusBadge status={study.status} />}
        </div>
        {/* 근거 문장은 자료 원문이고 서버가 본문에 있는지 확인했다 (AI 글 아님) */}
        {concept.evidenceQuote && (
          <SourceQuote
            quote={concept.evidenceQuote}
            location={concept.evidenceLocation}
            label="근거"
          />
        )}
        {concept.aiDescription && (
          <AiNote title="한 줄 요약">{concept.aiDescription}</AiNote>
        )}

        <div className="flex flex-wrap gap-2">
          <Button
            variant={open ? 'secondary' : 'primary'}
            aria-expanded={open}
            aria-controls={panelId}
            onClick={() => setOpen((value) => !value)}
          >
            <ChevronDown
              size={16}
              aria-hidden="true"
              className={cn('transition-transform', open && 'rotate-180')}
            />
            {open ? '개념 설명 접기' : '개념 자세히 공부하기'}
          </Button>
        </div>

        {open && (
          <div id={panelId} className="flex flex-col gap-3">
            {concept.studyNote ? (
              <StudyNoteView concept={concept} />
            ) : (
              <Notice icon={BookOpen} iconClassName="text-primary">
                아직 이 개념의 자세한 설명이 없어요. 아래에서 만들면 자세한
                설명, 자료와의 연결, 중요한 이유, 예시를 볼 수 있어요.
              </Notice>
            )}

            <div className="flex flex-col gap-2 rounded-xl border border-line p-4">
              <p className="text-body font-bold text-heading">
                {concept.studyNote ? '설명 다시 만들기' : '자세한 설명 만들기'}
              </p>
              {blockedReason ? (
                <p className="text-caption text-ink-soft">{blockedReason}</p>
              ) : (
                <label className="flex items-start gap-3 text-caption text-heading">
                  <input
                    type="checkbox"
                    checked={confirmed}
                    onChange={(e) => setConfirmed(e.target.checked)}
                    className="mt-0.5 size-4 accent-primary"
                  />
                  <span>
                    이 자료의 본문 발췌(최대 12,000자)를 외부 생성형 AI로 보내는
                    것을 확인했어요. 본인이 이용 권한이 있는 자료예요.
                  </span>
                </label>
              )}
              <ErrorText error={mutation.error} />
              <div className="flex flex-wrap items-center gap-2">
                <Button
                  variant="secondary"
                  disabled={
                    blockedReason !== null || !confirmed || mutation.isPending
                  }
                  onClick={() => mutation.mutate()}
                >
                  {mutation.isPending && (
                    <LoaderCircle
                      size={16}
                      className="animate-spin"
                      aria-hidden="true"
                    />
                  )}
                  {concept.studyNote ? '다시 만들기' : '설명 만들기'}
                </Button>
                {mutation.isPending && (
                  <span
                    className="text-caption text-ink-soft"
                    aria-live="polite"
                  >
                    AI가 설명을 쓰는 중이에요. 1분 정도 걸릴 수 있어요.
                  </span>
                )}
              </div>
            </div>
          </div>
        )}

        {study && (
          <ConceptStudyActions
            study={study}
            materialId={materialId}
            courseId={courseId}
            onRestudy={() => {
              setOpen(true);
              ref.current?.scrollIntoView({ block: 'start' });
            }}
          />
        )}
      </Card>
    </li>
  );
}

function blockedReasonOf(
  ai: QuizAiStatus,
  readable: boolean,
  aiQuizConsent: boolean | undefined,
): string | null {
  if (!ai.materialContentEnabled) return CONTENT_DISABLED_MESSAGE;
  if (!readable)
    return '이 형식은 본문을 읽지 못해요. TXT·MD·PPTX·글이 들어 있는 PDF만 읽어요.';
  if (!ai.available) return ai.message;
  if (aiQuizConsent === false)
    return 'AI로 개념을 뽑거나 설명을 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.';
  return null;
}

// 자료에서 뽑은 핵심 개념 + 개념별 자세한 설명 + 뽑기. 뽑기·설명은 자료 본문 발췌를 외부 AI로 보내므로
// 운영 설정·AI 퀴즈 처리 동의·이번 요청의 확인이 모두 있어야 한다. 개념은 이름과 근거 문장이 자료 본문에 실제로 있는 것만 서버가 저장한다
function MaterialConceptPanel({
  materialId,
  fileName,
  courseId = null,
  focusConceptId = null,
}: {
  materialId: number;
  fileName: string;
  courseId?: number | null;
  focusConceptId?: string | null;
}) {
  const queryClient = useQueryClient();
  const { data: me } = useMe();
  const { data: concepts } = useSuspenseQuery({
    queryKey: [...MATERIALS_QUERY_KEY, materialId, 'concepts'],
    queryFn: () => getMaterialConcepts(materialId),
  });
  const { data: ai } = useSuspenseQuery({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'ai-status'],
    queryFn: getQuizAiStatus,
  });
  // 개념별 학습 기록·확인 퀴즈 결과 (실제 모드는 서버 DB, 체험 모드는 이 브라우저)
  const { data: studies } = useSuspenseQuery({
    queryKey: [...STUDY_QUERY_KEY, materialId],
    queryFn: () => getConceptStudies(materialId),
  });
  const [confirmed, setConfirmed] = useState(false);

  const mutation = useMutation({
    mutationFn: () =>
      extractMaterialConcepts(materialId, { materialContentConsent: true }),
    onSuccess: (next) => {
      queryClient.setQueryData(
        [...MATERIALS_QUERY_KEY, materialId, 'concepts'],
        next,
      );
      void queryClient.invalidateQueries({
        queryKey: [...MATERIALS_QUERY_KEY, materialId],
        exact: true,
      });
      void queryClient.invalidateQueries({ queryKey: CURRICULUM_QUERY_KEY });
      // 다시 뽑으면 개념 목록이 바뀌므로 학습 상태도 다시 읽는다 (기록은 개념 키로 이어진다)
      void queryClient.invalidateQueries({
        queryKey: [...STUDY_QUERY_KEY, materialId],
      });
      setConfirmed(false);
    },
  });

  const blockedReason = blockedReasonOf(
    ai,
    isReadable(fileName),
    me?.aiQuizConsent,
  );
  const withNotes = concepts.filter((concept) => concept.studyNote).length;

  return (
    <div className="flex flex-col gap-4">
      {concepts.length === 0 ? (
        <Notice icon={BookOpen} iconClassName="text-primary">
          아직 이 자료에서 뽑은 핵심 개념이 없어요. 개념을 뽑기 전에는 사전·사후
          평가를 과목 키워드로 만들어요.
        </Notice>
      ) : (
        <>
          <p className="text-body text-ink-soft">
            이 자료에서 뽑은 개념 {concepts.length}개예요(자세한 설명{' '}
            {withNotes}개). 근거 문장이 자료 본문에 실제로 있는 개념만 남겼고,
            사전·사후평가도 이 개념으로 만들어요. 개념을 눌러 자세히 공부해
            보세요.
          </p>
          <ul className="flex flex-col gap-3">
            {concepts.map((concept) => (
              <ConceptCard
                key={concept.conceptId}
                concept={concept}
                materialId={materialId}
                courseId={courseId}
                blockedReason={blockedReason}
                defaultOpen={concept.conceptId === focusConceptId}
                study={studies.find(
                  (item) => item.conceptId === concept.conceptId,
                )}
              />
            ))}
          </ul>
        </>
      )}

      <Card as="section" className="flex flex-col gap-3 p-5">
        <h3 className="text-body font-bold text-heading">
          {concepts.length === 0 ? '핵심 개념 뽑기' : '핵심 개념 다시 뽑기'}
        </h3>
        <Notice icon={Lock} size="caption" iconClassName="text-primary">
          자료 본문 앞부분(최대 12,000자, PDF는 100쪽까지)을 외부 생성형 AI로
          보내 개념 후보를 받아요. 이메일·전화번호 같은 개인정보가 보이면 보내지
          않아요. 다시 뽑으면 지금 개념과 자세한 설명은 새 결과로 바뀌어요.
        </Notice>
        {blockedReason ? (
          <p className="text-body text-ink-soft">{blockedReason}</p>
        ) : (
          <label className="flex items-start gap-3 text-body text-heading">
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
              className="mt-1 size-4 accent-primary"
            />
            <span>
              이 자료의 본문 발췌를 외부 생성형 AI로 보내는 것을 확인했어요.
              본인이 이용 권한이 있는 자료예요.
            </span>
          </label>
        )}
        <ErrorText error={mutation.error} />
        <div className="flex flex-wrap items-center gap-2">
          <Button
            disabled={
              blockedReason !== null || !confirmed || mutation.isPending
            }
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending && (
              <LoaderCircle
                size={16}
                className="animate-spin"
                aria-hidden="true"
              />
            )}
            핵심 개념 뽑기
          </Button>
          {mutation.isPending && (
            <span className="text-caption text-ink-soft" aria-live="polite">
              AI가 개념을 찾는 중이에요. 최대 2분 30초 걸릴 수 있어요.
            </span>
          )}
          {USE_MOCK && (
            <span className="text-caption text-ink-muted">
              목업에서는 AI를 부르지 않아요. 예시 개념의 설명은 미리 써 둔
              예시예요.
            </span>
          )}
        </div>
      </Card>
    </div>
  );
}

export default MaterialConceptPanel;
