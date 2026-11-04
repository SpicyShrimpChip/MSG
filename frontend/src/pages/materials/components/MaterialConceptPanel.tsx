import { useState } from 'react';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { BookOpen, LoaderCircle, Lock, Quote } from 'lucide-react';
import {
  ASSESSMENTS_QUERY_KEY,
  MATERIALS_QUERY_KEY,
  USE_MOCK,
  extractMaterialConcepts,
  getMaterialConcepts,
  getQuizAiStatus,
} from '@/api';
import { AiNote, Button, Card, Chip, ErrorText, Notice } from '@/components';
import { useMe } from '@/hooks';
import type { MaterialConcept } from '@/types';

// 본문을 읽을 수 있는 형식 (서버 MaterialTextExtractor와 같다). PDF는 글이 들어 있는 PDF만, 스캔본은 OCR이 없어 못 읽는다
export const READABLE_EXTENSIONS = ['txt', 'md', 'pptx', 'pdf'];
export const isReadable = (fileName: string) =>
  READABLE_EXTENSIONS.includes(fileName.split('.').pop()?.toLowerCase() ?? '');

export const CONTENT_DISABLED_MESSAGE =
  '학습자료 본문을 외부 AI로 보내는 기능은 운영 설정으로 꺼져 있어요. 처리 근거와 안내가 준비되면 켜져요. 지금은 과목 키워드로만 평가를 만들어요.';

function ConceptCard({ concept }: { concept: MaterialConcept }) {
  return (
    <Card as="li" className="flex flex-col gap-3 p-5">
      <div className="flex flex-wrap items-center gap-2">
        <p className="font-bold text-heading">{concept.name}</p>
        <Chip tone={concept.courseKeyword ? 'primary' : 'neutral'}>
          {concept.courseKeyword ? '과목 키워드와 같음' : '자료에서 찾은 개념'}
        </Chip>
      </div>
      {/* 근거 문장은 자료 원문이고 서버가 본문에 있는지 확인했다 (AI 글 아님) */}
      {concept.evidenceQuote && (
        <figure className="flex gap-2 rounded-lg bg-surface-muted p-3 text-caption text-ink">
          <Quote
            size={14}
            className="mt-0.5 shrink-0 text-ink-muted"
            aria-hidden="true"
          />
          <div className="flex flex-col gap-1">
            <blockquote>{concept.evidenceQuote}</blockquote>
            <figcaption className="text-ink-soft">
              근거 · {concept.evidenceLocation ?? '위치 표시 없음'} (자료 원문)
            </figcaption>
          </div>
        </figure>
      )}
      {concept.aiDescription && (
        <AiNote title="이 자료에서는">{concept.aiDescription}</AiNote>
      )}
    </Card>
  );
}

// 자료에서 뽑은 핵심 개념 + 뽑기. 뽑기는 자료 본문 발췌를 외부 AI로 보내므로 운영 설정·AI 퀴즈 처리 동의·이번 요청의 확인이 모두 있어야 한다.
// AI가 낸 개념 중 이름과 근거 문장이 자료 본문에 실제로 있는 것만 서버가 저장하고, 여기에는 저장된 것만 보여 준다
function MaterialConceptPanel({
  materialId,
  fileName,
}: {
  materialId: number;
  fileName: string;
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
      setConfirmed(false);
    },
  });

  const readable = isReadable(fileName);
  const blockedReason = !ai.materialContentEnabled
    ? CONTENT_DISABLED_MESSAGE
    : !readable
      ? '이 형식은 본문을 읽지 못해요. TXT·MD·PPTX·글이 들어 있는 PDF만 읽어요.'
      : !ai.available
        ? ai.message
        : me && !me.aiQuizConsent
          ? '핵심 개념을 뽑으려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.'
          : null;

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
            이 자료에서 뽑은 개념 {concepts.length}개예요. 근거 문장이 자료
            본문에 실제로 있는 개념만 남겼어요. 사전·사후평가는 이 개념으로
            만들어요.
          </p>
          <ul className="grid gap-3 md:grid-cols-2">
            {concepts.map((concept) => (
              <ConceptCard key={concept.conceptId} concept={concept} />
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
          않아요. 다시 뽑으면 지금 개념은 새 결과로 바뀌어요.
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
              목업에서는 AI를 부르지 않아요.
            </span>
          )}
        </div>
      </Card>
    </div>
  );
}

export default MaterialConceptPanel;
