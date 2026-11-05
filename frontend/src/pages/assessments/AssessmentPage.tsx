import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { ClipboardCheck, FileText, LoaderCircle, Lock } from 'lucide-react';
import {
  ASSESSMENTS_QUERY_KEY,
  MATERIALS_QUERY_KEY,
  ME_QUERY_KEY,
  QUIZZES_QUERY_KEY,
  USE_MOCK,
  createAssessment,
  getAssessmentComparison,
  getMaterials,
  getMe,
  getQuizAiStatus,
} from '@/api';
import {
  AsyncBoundary,
  Button,
  Card,
  EmptyState,
  ErrorText,
  FormRow,
  Notice,
  SelectInput,
  SubPage,
} from '@/components';
import { DIFFICULTIES, QUESTION_TYPE_SETS } from '@/constants';
import MaterialConceptPanel, {
  CONTENT_DISABLED_MESSAGE,
  isReadable,
} from '@/pages/materials/components/MaterialConceptPanel';
import type {
  AssessmentKind,
  AssessmentSnapshot,
  MaterialContentNote,
  MaterialSummary,
  Quiz,
  QuizDifficulty,
  User,
} from '@/types';

const NOTE_MESSAGE: Record<MaterialContentNote, string> = {
  NOT_SUPPORTED:
    '이 형식은 본문을 읽지 못해 과목 개념으로만 문제를 만들었어요.',
  NO_TEXT:
    '이 PDF에는 글이 없어요(스캔본이면 글자 인식(OCR)이 필요한데 지원하지 않아요). 과목 개념으로만 문제를 만들었어요.',
  ENCRYPTED:
    '암호가 걸린 PDF라 본문을 읽지 못해 과목 개념으로만 문제를 만들었어요.',
  DISABLED:
    '자료 본문 전송 기능이 지금 꺼져 있어 본문 없이 같은 개념으로만 만들었어요. 사전평가와 조건이 달라 비교에 주의가 필요해요.',
  EMPTY: '자료에서 읽을 글이 없어 과목 개념으로만 문제를 만들었어요.',
  FAILED: '자료 본문을 읽지 못해 과목 개념으로만 문제를 만들었어요.',
  PERSONAL_INFO:
    '자료 본문에 이메일·전화번호 같은 개인정보가 있어 보내지 않고 과목 개념으로만 문제를 만들었어요.',
};

function SnapshotLine({
  label,
  snapshot,
}: {
  label: string;
  snapshot: AssessmentSnapshot | null;
}) {
  if (!snapshot) {
    return (
      <p className="text-body text-ink-soft">
        <span className="font-bold text-heading">{label}</span> 아직 없어요
      </p>
    );
  }
  return (
    <p className="flex flex-wrap items-center gap-2 text-body text-ink">
      <span className="font-bold text-heading">{label}</span>
      {snapshot.submissionId === null ? (
        <span className="text-ink-soft">만들었지만 아직 안 풀었어요</span>
      ) : (
        <span className="tabular-nums">
          {snapshot.score}/{snapshot.maxScore}점 (채점 끝난{' '}
          {snapshot.gradedCount}
          문제)
        </span>
      )}
      <Link
        to={
          snapshot.submissionId === null
            ? `/quizzes/${snapshot.quizId}`
            : `/quizzes/${snapshot.quizId}/result`
        }
        className="font-bold text-primary underline-offset-4 hover:underline"
      >
        {snapshot.submissionId === null ? '풀기' : '결과 보기'}
      </Link>
    </p>
  );
}

function MaterialAssessment({
  material,
  me,
}: {
  material: MaterialSummary;
  me: User | null;
}) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: comparison } = useSuspenseQuery({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'comparison', 'material', material.id],
    queryFn: () => getAssessmentComparison({ materialId: material.id }),
  });
  const { data: ai } = useSuspenseQuery({
    queryKey: [...ASSESSMENTS_QUERY_KEY, 'ai-status'],
    queryFn: getQuizAiStatus,
  });
  const [typeSet, setTypeSet] = useState(3); // 객관식 + 주관식
  const [count, setCount] = useState(6);
  const [difficulty, setDifficulty] = useState<QuizDifficulty>('medium');
  const [useContent, setUseContent] = useState(false);
  const [contentConsent, setContentConsent] = useState(false);
  const [created, setCreated] = useState<Quiz | null>(null);
  const readable = isReadable(material.fileName);
  const contentAllowed = ai.materialContentEnabled && readable;
  const types = QUESTION_TYPE_SETS[typeSet].types;

  const mutation = useMutation({
    mutationFn: (kind: AssessmentKind) =>
      createAssessment({
        requestKey: crypto.randomUUID(),
        materialId: material.id,
        kind,
        questionTypes: types,
        questionCount: count,
        difficulty,
        useMaterialContent: useContent || undefined,
        materialContentConsent: useContent ? contentConsent : undefined,
      }),
    onSuccess: (quiz) => {
      queryClient.setQueryData([...QUIZZES_QUERY_KEY, quiz.id], quiz);
      void queryClient.invalidateQueries({ queryKey: ASSESSMENTS_QUERY_KEY });
      void queryClient.invalidateQueries({
        queryKey: QUIZZES_QUERY_KEY,
        exact: true,
      });
      // 자료 본문을 못 썼으면 이유를 먼저 보여 주고, 아니면 바로 풀기 화면으로
      if (quiz.materialContentNote) setCreated(quiz);
      else navigate(`/quizzes/${quiz.id}`);
    },
  });

  const preReady = comparison.pre !== null;
  const blockedReason = USE_MOCK
    ? null
    : !ai.available
      ? ai.message
      : me && !me.aiQuizConsent
        ? 'AI로 평가를 만들려면 AI 퀴즈 처리 동의가 필요해요. 마이페이지에서 동의할 수 있어요.'
        : material.courseId === null
          ? '과목에 연결된 자료로만 평가를 만들 수 있어요.'
          : null;
  const contentBlocked = useContent && !contentConsent;
  const tooFew = count < types.length;

  return (
    <div className="flex flex-col gap-5">
      <Card as="section" className="flex flex-col gap-3 p-5">
        <h2 className="text-lg font-extrabold text-heading">지금 상태</h2>
        <SnapshotLine label="사전평가" snapshot={comparison.pre} />
        <SnapshotLine label="사후평가" snapshot={comparison.post} />
        <p className="text-caption text-ink-muted">
          사후평가는 최신 사전평가와 같은 개념·문제 유형·문항 수·난이도로
          만들어요. 비교 그래프는 학습 대시보드 맨 위에 있어요.
        </p>
      </Card>

      <section className="flex flex-col gap-3">
        <h2 className="text-lg font-extrabold text-heading">
          평가할 개념 (자료에서 뽑은 개념)
        </h2>
        <AsyncBoundary>
          <MaterialConceptPanel
            materialId={material.id}
            fileName={material.fileName}
            courseId={material.courseId}
          />
        </AsyncBoundary>
      </section>

      <Card as="section" className="flex flex-col gap-4 p-5">
        <h2 className="text-lg font-extrabold text-heading">평가 만들기</h2>
        <div className="grid gap-4 sm:grid-cols-3">
          <FormRow label="문제 유형 (사전평가)" htmlFor="assess-types">
            <SelectInput
              id="assess-types"
              value={typeSet}
              onChange={(e) => setTypeSet(Number(e.target.value))}
            >
              {QUESTION_TYPE_SETS.map((item, index) => (
                <option key={item.label} value={index}>
                  {item.label}
                </option>
              ))}
            </SelectInput>
          </FormRow>
          <FormRow
            label="문제 수"
            htmlFor="assess-count"
            error={tooFew ? `${types.length}문제 이상이어야 해요.` : ''}
          >
            <SelectInput
              id="assess-count"
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
            >
              {Array.from({ length: 10 }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>
                  {n}문제
                </option>
              ))}
            </SelectInput>
          </FormRow>
          <FormRow label="난이도" htmlFor="assess-difficulty">
            <SelectInput
              id="assess-difficulty"
              value={difficulty}
              onChange={(e) => setDifficulty(e.target.value as QuizDifficulty)}
            >
              {DIFFICULTIES.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </SelectInput>
          </FormRow>
        </div>

        <fieldset className="flex flex-col gap-2 rounded-xl border border-line p-4">
          <legend className="px-1 text-body font-bold text-heading">
            자료 본문 쓰기 (선택)
          </legend>
          <label className="flex items-start gap-3 text-body text-heading">
            <input
              type="checkbox"
              checked={useContent}
              disabled={!contentAllowed}
              onChange={(e) => {
                setUseContent(e.target.checked);
                if (!e.target.checked) setContentConsent(false);
              }}
              className="mt-1 size-4 accent-primary"
            />
            <span>
              자료 본문(앞부분 12,000자까지)으로 문제 만들기
              <span className="block text-caption text-ink-soft">
                {!ai.materialContentEnabled
                  ? CONTENT_DISABLED_MESSAGE
                  : readable
                    ? 'TXT·MD·PPTX와 글이 들어 있는 PDF(100쪽까지)를 읽어요. 스캔한 PDF는 읽지 못해요. 체크하지 않으면 개념 목록으로만 만들어요.'
                    : '이 형식은 본문을 읽지 못해요. 개념 목록으로만 만들어요.'}
              </span>
            </span>
          </label>
          {useContent && (
            <label className="flex items-start gap-3 text-body text-heading">
              <input
                type="checkbox"
                checked={contentConsent}
                onChange={(e) => setContentConsent(e.target.checked)}
                className="mt-1 size-4 accent-primary"
              />
              <span>
                이 자료의 본문을 문제를 만들기 위해 외부 생성형 AI로 보내는 것을
                확인했어요. 본인이 이용 권한이 있는 자료예요.
                <span className="block text-caption text-ink-soft">
                  이메일·전화번호 같은 개인정보가 보이면 보내지 않아요. 교수님
                  강의자료·시험 문제 원문은 올리지 말아 주세요.
                </span>
              </span>
            </label>
          )}
        </fieldset>

        <Notice icon={Lock} size="caption" iconClassName="text-primary">
          외부 AI로는 과목 이름·개념{useContent ? '·자료 본문 발췌' : ''}만
          보내요(사후평가는 겹치지 않게 사전평가 문제 글도 보내요). 가져온
          대화는 보내지 않아요.{' '}
          {ai.available && ai.generation
            ? `문제는 ${ai.generation.provider.toUpperCase()}가 만들어요.`
            : ''}
        </Notice>

        {blockedReason && (
          <p role="alert" className="text-body text-danger">
            {blockedReason}
          </p>
        )}
        <ErrorText error={mutation.error} />
        {created?.materialContentNote && (
          <Notice icon={FileText} size="caption">
            {NOTE_MESSAGE[created.materialContentNote]}{' '}
            <Link
              to={`/quizzes/${created.id}`}
              className="font-bold text-primary underline-offset-4 hover:underline"
            >
              문제 풀기
            </Link>
          </Notice>
        )}

        <div className="flex flex-wrap items-center gap-2">
          <Button
            disabled={
              blockedReason !== null ||
              contentBlocked ||
              tooFew ||
              mutation.isPending
            }
            onClick={() => mutation.mutate('pre')}
          >
            {mutation.isPending && mutation.variables === 'pre' && (
              <LoaderCircle
                size={16}
                className="animate-spin"
                aria-hidden="true"
              />
            )}
            사전평가 만들기
          </Button>
          <Button
            variant="secondary"
            disabled={
              !preReady ||
              blockedReason !== null ||
              contentBlocked ||
              mutation.isPending
            }
            onClick={() => mutation.mutate('post')}
          >
            {mutation.isPending && mutation.variables === 'post' && (
              <LoaderCircle
                size={16}
                className="animate-spin"
                aria-hidden="true"
              />
            )}
            사후평가 만들기
          </Button>
          {mutation.isPending && (
            <span className="text-caption text-ink-soft" aria-live="polite">
              AI가 문제를 만드는 중이에요. 최대 2분 30초 걸릴 수 있어요.
            </span>
          )}
          {!preReady && (
            <span className="text-caption text-ink-muted">
              사후평가는 사전평가를 만든 뒤에 볼 수 있어요.
            </span>
          )}
        </div>
      </Card>
    </div>
  );
}

function MaterialPicker({ me }: { me: User | null }) {
  const [params] = useSearchParams();
  const { data: materials } = useSuspenseQuery({
    queryKey: MATERIALS_QUERY_KEY,
    queryFn: getMaterials,
  });
  const linked = materials.filter((item) => item.courseId !== null);
  const initial = Number(params.get('materialId'));
  const [materialId, setMaterialId] = useState<number | null>(
    linked.some((item) => item.id === initial)
      ? initial
      : (linked[0]?.id ?? null),
  );
  const material = linked.find((item) => item.id === materialId) ?? null;

  if (linked.length === 0 || !material) {
    return (
      <EmptyState
        icon={<ClipboardCheck size={24} aria-hidden="true" />}
        title="과목에 연결된 학습자료가 없어요"
        description="자료를 과목에 연결해 올리면 그 자료로 사전평가와 사후평가를 볼 수 있어요."
        action={{ label: '자료 올리기', to: '/materials/upload' }}
      />
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <FormRow label="학습자료" htmlFor="assess-material">
        <SelectInput
          id="assess-material"
          value={material.id}
          onChange={(e) => setMaterialId(Number(e.target.value))}
        >
          {linked.map((item) => (
            <option key={item.id} value={item.id}>
              {item.title} ({item.fileName})
            </option>
          ))}
        </SelectInput>
      </FormRow>
      <AsyncBoundary key={material.id}>
        <MaterialAssessment material={material} me={me} />
      </AsyncBoundary>
    </div>
  );
}

function WithProfile() {
  const { data: me } = useSuspenseQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
  });
  return <MaterialPicker me={me} />;
}

// 학습자료 기반 사전평가(학습 전 수준 파악) → 공부 → 사후평가(같은 개념으로 향상 확인)
function AssessmentPage() {
  return (
    <SubPage
      narrow="reading"
      title="사전·사후평가"
      description="학습자료로 공부 전 수준을 확인하고, 공부한 뒤 같은 개념으로 다시 평가해 얼마나 나아졌는지 비교해요"
    >
      <AsyncBoundary>
        {USE_MOCK ? <MaterialPicker me={null} /> : <WithProfile />}
      </AsyncBoundary>
    </SubPage>
  );
}

export default AssessmentPage;
