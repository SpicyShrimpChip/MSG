import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  useMutation,
  useQuery,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { FileText, LoaderCircle, Lock, Sparkles, Target } from 'lucide-react';
import {
  ANALYSIS_QUERY_KEY,
  ME_QUERY_KEY,
  QUIZZES_QUERY_KEY,
  USE_MOCK,
  createQuiz,
  getGenerationStatus,
  getMe,
  getProviders,
} from '@/api';
import {
  AsyncBoundary,
  Button,
  Card,
  Chip,
  EmptyState,
  ErrorText,
  FormRow,
  Notice,
  SelectInput,
  SubPage,
  TextInput,
} from '@/components';
import {
  DIFFICULTIES,
  JOB_FIELDS,
  JOB_FIELD_OTHER_MAX,
  QUESTION_TYPE_SETS,
  QUIZ_MAX_QUESTIONS,
  QUIZ_MODES,
} from '@/constants';
import { useCourses } from '@/hooks';
import type { JobField, QuizDifficulty, QuizMode, User } from '@/types';
import { cn, conceptStudyLink, formatSemester } from '@/utils';

const EXAM_SCOPE_MAX = 200;
const MOCK_EXAM_MAX_QUESTIONS = 20;
// 만들기를 보낸 요청 키 (이 탭에서만). 새로 고치거나 다른 화면에 다녀와도 같은 요청의 결과를 찾는다
const PENDING_KEY = 'ai-lms-quiz-generation-pending';

function loadPendingKey(): string | null {
  try {
    return sessionStorage.getItem(PENDING_KEY);
  } catch {
    return null;
  }
}

function savePendingKey(key: string | null) {
  try {
    if (key) sessionStorage.setItem(PENDING_KEY, key);
    else sessionStorage.removeItem(PENDING_KEY);
  } catch {
    // 저장소를 쓸 수 없으면 이 화면에서만 기억한다
  }
}

function isQuizMode(value: string | null): value is QuizMode {
  return QUIZ_MODES.some((mode) => mode.value === value);
}

// 퀴즈 만들기: 학습 모드·과목·문제 유형·난이도·문제 수를 골라 서버에 AI 생성을 부탁한다.
// 서버가 구조·정답·채점 기준을 검증하고 통과한 퀴즈만 저장한다. 실패하면 서버 message를 그대로 보여 준다
// 이전에 보낸 만들기 요청이 남아 있으면 끝날 때까지 상태를 확인한다 (서버 마감 150초)
function PendingGeneration({
  requestKey,
  onDone,
}: {
  requestKey: string;
  onDone: (message: string | null) => void;
}) {
  const navigate = useNavigate();
  const { data, error } = useQuery({
    queryKey: [...QUIZZES_QUERY_KEY, 'generation', requestKey],
    queryFn: () => getGenerationStatus(requestKey),
    refetchInterval: (query) =>
      query.state.data?.status === 'running' ? 3000 : false,
  });
  useEffect(() => {
    if (error) {
      savePendingKey(null);
      onDone(null);
    } else if (data?.status === 'succeeded' && data.quizId) {
      savePendingKey(null);
      navigate(`/quizzes/${data.quizId}`);
    } else if (data && data.status !== 'running') {
      savePendingKey(null);
      onDone(data.message);
    }
  }, [data, error, navigate, onDone]);
  return (
    <Notice icon={LoaderCircle} iconClassName="animate-spin text-primary">
      이전에 보낸 퀴즈 만들기 요청을 확인하고 있어요. 끝나면 바로 문제 화면으로
      이동해요.
    </Notice>
  );
}

function QuizCreateForm({ me }: { me: User | null }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [params] = useSearchParams();
  const { data: courses } = useCourses();
  const [pendingKey, setPendingKey] = useState(loadPendingKey);
  const [recoveredMessage, setRecoveredMessage] = useState<string | null>(null);
  const { data: providers } = useSuspenseQuery({
    queryKey: [...ANALYSIS_QUERY_KEY, 'providers'],
    queryFn: getProviders,
  });

  // 확인 퀴즈 링크(대시보드 "이미 알아요")는 과목·모드·개념을 주소로 넘긴다
  const paramMode = params.get('mode');
  const defaultMode: QuizMode = isQuizMode(paramMode)
    ? paramMode
    : me && isQuizMode(me.learningPurpose)
      ? me.learningPurpose
      : 'exam_prep';
  const paramCourse = Number(params.get('courseId'));
  // 개념 확인 퀴즈(자료 상세·커리큘럼에서 옴): 자료의 개념 하나만 다룬다. 과목·개념은 바꾸지 않는다
  const checkMaterialId = Number(params.get('checkMaterialId'));
  const checkConceptId = params.get('checkConceptId');
  const check =
    checkMaterialId > 0 && checkConceptId
      ? {
          materialId: checkMaterialId,
          conceptId: checkConceptId,
          name: params.get('concept') ?? checkConceptId,
        }
      : null;
  const [mode, setMode] = useState<QuizMode>(defaultMode);
  const [courseId, setCourseId] = useState<number | null>(
    courses.some((course) => course.id === paramCourse)
      ? paramCourse
      : (courses[0]?.id ?? null),
  );
  const [typeSet, setTypeSet] = useState(defaultMode === 'work' ? 2 : 6);
  const [difficulty, setDifficulty] = useState<QuizDifficulty>('medium');
  const [count, setCount] = useState(check ? 3 : 5);
  const [concepts, setConcepts] = useState<string[]>(
    !check && params.get('concept') ? [params.get('concept')!] : [],
  );
  const [examScope, setExamScope] = useState('');
  const [jobField, setJobField] = useState<JobField | null>(
    me?.jobField ?? null,
  );
  const [jobFieldOther, setJobFieldOther] = useState(me?.jobFieldOther ?? '');
  const [mockExam, setMockExam] = useState(false);
  // 같은 요청을 두 번 보내도 퀴즈가 하나만 생기도록 화면을 열 때·실패한 뒤마다 새 키를 만든다
  const [requestKey, setRequestKey] = useState(() => crypto.randomUUID());

  const course = courses.find((item) => item.id === courseId) ?? null;
  const types = QUESTION_TYPE_SETS[typeSet].types;
  const configured = providers.filter((item) => item.configured);
  const tooFew = count < types.length;
  const maxCount =
    mode === 'exam_prep' && mockExam
      ? MOCK_EXAM_MAX_QUESTIONS
      : QUIZ_MAX_QUESTIONS;
  const keywords = useMemo(() => course?.keywords ?? [], [course]);

  const mutation = useMutation({
    mutationFn: createQuiz,
    onMutate: (input) => savePendingKey(input.requestKey),
    onSuccess: (quiz) => {
      savePendingKey(null);
      queryClient.setQueryData([...QUIZZES_QUERY_KEY, quiz.id], quiz);
      void queryClient.invalidateQueries({
        queryKey: QUIZZES_QUERY_KEY,
        exact: true,
      });
      navigate(`/quizzes/${quiz.id}`);
    },
    // 실패하면 그 키는 끝났으니 다시 만들 때는 새 키를 쓴다
    onError: () => {
      savePendingKey(null);
      setRequestKey(crypto.randomUUID());
    },
  });

  if (courses.length === 0) {
    return (
      <EmptyState
        icon={<FileText size={24} aria-hidden="true" />}
        title="먼저 과목을 만들어 주세요"
        description="과목 키워드가 퀴즈의 개념이 돼요."
        action={{ label: '과목 만들기', to: '/courses' }}
      />
    );
  }

  const blocked =
    !course ||
    (!check && keywords.length === 0) ||
    tooFew ||
    count > maxCount ||
    pendingKey !== null ||
    (!USE_MOCK && (configured.length === 0 || !me?.aiQuizConsent));

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (blocked || mutation.isPending || !course) return;
    mutation.mutate({
      requestKey,
      courseId: course.id,
      mode,
      questionTypes: types,
      questionCount: count,
      difficulty,
      mockExam: !check && mode === 'exam_prep' && mockExam ? true : undefined,
      concepts: check
        ? undefined
        : concepts.filter((concept) => keywords.includes(concept)),
      materialId: check?.materialId,
      checkConceptId: check?.conceptId,
      examScope:
        mode === 'exam_prep' && examScope.trim() ? examScope.trim() : undefined,
      jobField: mode === 'work' && jobField ? jobField : undefined,
      jobFieldOther:
        mode === 'work' && jobField === 'other'
          ? jobFieldOther.trim()
          : undefined,
    });
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6">
      {check && (
        <Notice icon={Target} iconClassName="text-primary">
          <span className="font-bold text-heading">
            ‘{check.name}’ 확인 퀴즈예요.
          </span>{' '}
          이 개념 문제만 나오고, 채점 결과로 이 개념의 학습 상태(이해 확인·복습
          필요)가 정해져요.{' '}
          <Link
            to={conceptStudyLink(check.materialId, check.conceptId)}
            className="font-bold text-primary underline-offset-4 hover:underline"
          >
            개념으로 돌아가기
          </Link>
        </Notice>
      )}
      <fieldset className="flex flex-col gap-3">
        <legend className="mb-1 font-bold text-heading">학습 모드</legend>
        <div className="grid gap-2 md:grid-cols-3">
          {QUIZ_MODES.map((item) => {
            const checked = mode === item.value;
            return (
              <label
                key={item.value}
                className={cn(
                  'flex cursor-pointer flex-col gap-1.5 rounded-xl border px-4 py-3',
                  checked
                    ? 'border-2 border-primary bg-primary-soft'
                    : 'border-line hover:bg-surface-muted',
                )}
              >
                <span className="flex items-center gap-2 font-bold text-heading">
                  <input
                    type="radio"
                    name="quiz-mode"
                    checked={checked}
                    onChange={() => {
                      setMode(item.value);
                      // 업무 활용은 상황 중심 서술형이 기본
                      if (item.value === 'work') setTypeSet(2);
                    }}
                  />
                  {item.label}
                </span>
                <span className="text-caption leading-relaxed text-ink-soft">
                  {item.description}
                </span>
              </label>
            );
          })}
        </div>
      </fieldset>

      <Card className="flex flex-col gap-5 p-6">
        <FormRow label="과목" htmlFor="quiz-course" required>
          <SelectInput
            id="quiz-course"
            disabled={check !== null}
            value={courseId ?? ''}
            onChange={(e) => {
              setCourseId(Number(e.target.value));
              setConcepts([]);
            }}
          >
            {courses.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name} · {formatSemester(item.semester)}
              </option>
            ))}
          </SelectInput>
        </FormRow>

        {check ? (
          <div className="flex flex-col gap-1">
            <p className="text-body font-bold text-heading">다룰 개념</p>
            <p className="text-body text-ink">
              {check.name}{' '}
              <span className="text-caption text-ink-muted">
                (자료에서 뽑은 개념 하나만)
              </span>
            </p>
          </div>
        ) : (
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-1 text-body font-bold text-heading">
              다룰 개념{' '}
              <span className="font-medium text-ink-muted">
                (고르지 않으면 과목 키워드 전체)
              </span>
            </legend>
            {keywords.length === 0 ? (
              <p className="text-body text-danger">
                이 과목에 키워드가 없어요. 과목에 키워드를 먼저 넣어 주세요.
              </p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {keywords.map((keyword) => {
                  const on = concepts.includes(keyword);
                  return (
                    <button
                      key={keyword}
                      type="button"
                      aria-pressed={on}
                      onClick={() =>
                        setConcepts((prev) =>
                          on
                            ? prev.filter((item) => item !== keyword)
                            : [...prev, keyword],
                        )
                      }
                      className={cn(
                        'rounded-full border px-3 py-1.5 text-caption font-medium',
                        on
                          ? 'border-primary bg-primary-soft text-primary'
                          : 'border-line-strong text-ink-soft hover:bg-surface-muted',
                      )}
                    >
                      {keyword}
                    </button>
                  );
                })}
              </div>
            )}
          </fieldset>
        )}

        <div className="grid gap-5 sm:grid-cols-3">
          <FormRow label="문제 유형" htmlFor="quiz-types" required>
            <SelectInput
              id="quiz-types"
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
          <FormRow label="난이도" htmlFor="quiz-difficulty" required>
            <SelectInput
              id="quiz-difficulty"
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
          <FormRow
            label="문제 수"
            htmlFor="quiz-count"
            required
            error={
              tooFew
                ? `유형을 ${types.length}가지 골랐으니 ${types.length}문제 이상이어야 해요.`
                : ''
            }
          >
            <SelectInput
              id="quiz-count"
              value={count}
              onChange={(e) => setCount(Number(e.target.value))}
            >
              {Array.from({ length: maxCount }, (_, i) => i + 1).map((n) => (
                <option key={n} value={n}>
                  {n}문제
                </option>
              ))}
            </SelectInput>
          </FormRow>
        </div>
        {types.length > 1 && !tooFew && (
          <p className="text-caption text-ink-muted">
            고른 유형이 모두 한 문제 이상 들어가도록 서버가 나눠요.
          </p>
        )}

        {mode === 'exam_prep' && !check && (
          <label className="flex items-start gap-3 text-body text-heading">
            <input
              type="checkbox"
              checked={mockExam}
              onChange={(e) => {
                setMockExam(e.target.checked);
                if (!e.target.checked && count > QUIZ_MAX_QUESTIONS)
                  setCount(QUIZ_MAX_QUESTIONS);
              }}
              className="mt-1 size-4 accent-primary"
            />
            <span>
              <span className="font-bold">모의시험으로 만들기</span>
              <span className="block text-caption text-ink-soft">
                범위 전체를 실제 시험처럼 고르게 다뤄요(
                {MOCK_EXAM_MAX_QUESTIONS}
                문제까지). 정답·해설은 제출한 뒤에 보여 줘요. 실제 출제를
                보장하지 않아요.
              </span>
            </span>
          </label>
        )}
        {mode === 'exam_prep' && (
          <FormRow
            label="시험 범위 (선택)"
            htmlFor="quiz-scope"
            hint={`예: 3~5주차, NAT와 VLAN (${EXAM_SCOPE_MAX}자까지)`}
          >
            <TextInput
              id="quiz-scope"
              type="text"
              maxLength={EXAM_SCOPE_MAX}
              value={examScope}
              onChange={(e) => setExamScope(e.target.value)}
            />
          </FormRow>
        )}

        {mode === 'work' && (
          <>
            <FormRow
              label="관심 직무"
              htmlFor="quiz-job"
              hint="고르지 않으면 특정 직무에 치우치지 않은 일반 실무 상황으로 만들어요. 기본값은 마이페이지에서 바꿀 수 있어요."
            >
              <SelectInput
                id="quiz-job"
                value={jobField ?? ''}
                onChange={(e) =>
                  setJobField((e.target.value || null) as JobField | null)
                }
              >
                <option value="">고르지 않음 (일반 실무)</option>
                {JOB_FIELDS.map((item) => (
                  <option key={item.value} value={item.value}>
                    {item.label}
                  </option>
                ))}
              </SelectInput>
            </FormRow>
            {jobField === 'other' && (
              <FormRow label="분야 이름" htmlFor="quiz-job-other" required>
                <TextInput
                  id="quiz-job-other"
                  type="text"
                  required
                  maxLength={JOB_FIELD_OTHER_MAX}
                  value={jobFieldOther}
                  onChange={(e) => setJobFieldOther(e.target.value)}
                />
              </FormRow>
            )}
          </>
        )}
      </Card>

      <Notice icon={Lock} size="caption" iconClassName="text-primary">
        퀴즈를 만들 때 외부 AI로는 과목 이름·개념·적은 시험 범위·직무 분야만
        보내요. 가져온 대화와 학습자료 본문은 보내지 않아요. 자료 본문을 읽고
        만드는 기능은 아직 없어서, 과목과 개념으로만 문제를 만들어요.
      </Notice>

      {!USE_MOCK && configured.length === 0 && (
        <p role="alert" className="text-body text-danger">
          연결된 AI 모델이 없어 지금은 퀴즈를 만들 수 없어요.
        </p>
      )}
      {!USE_MOCK && !me?.aiQuizConsent && (
        <p role="alert" className="text-body text-danger">
          AI로 퀴즈를 만들려면 AI 퀴즈 처리 동의가 필요해요(대화 분석 동의와
          별도). 마이페이지에서 동의할 수 있어요.
        </p>
      )}
      {pendingKey && (
        <PendingGeneration
          requestKey={pendingKey}
          onDone={(message) => {
            setPendingKey(null);
            setRecoveredMessage(message);
          }}
        />
      )}
      {recoveredMessage && (
        <p role="alert" className="text-body text-danger">
          {recoveredMessage}
        </p>
      )}
      {USE_MOCK && (
        <p className="text-body text-ink-soft">
          예시 데이터예요. 목업에서는 AI를 부르지 않고 예시 문제를 보여 줘요.
        </p>
      )}

      <ErrorText error={mutation.error} />

      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="submit"
          size="lg"
          disabled={blocked || mutation.isPending}
        >
          {mutation.isPending ? (
            <LoaderCircle
              size={18}
              aria-hidden="true"
              className="animate-spin"
            />
          ) : (
            <Sparkles size={18} aria-hidden="true" />
          )}
          {mutation.isPending ? 'AI가 문제를 만드는 중…' : '퀴즈 만들기'}
        </Button>
        {mutation.isPending && (
          <p className="text-body text-ink-soft" aria-live="polite">
            최대 2분 30초 걸릴 수 있어요. 화면을 새로 고쳐도 결과를 다시 찾아
            줘요.
          </p>
        )}
        {configured.length > 0 && (
          <span className="flex flex-wrap items-center gap-1.5 text-caption text-ink-muted">
            연결된 모델
            {configured.map((item) => (
              <Chip key={item.provider}>{item.provider.toUpperCase()}</Chip>
            ))}
          </span>
        )}
      </div>
    </form>
  );
}

// 내 정보(학습 목적·직무·동의)를 다 불러온 뒤에 기본값을 정한다 (불러오기 전 값으로 시작하지 않게)
function QuizCreateWithProfile() {
  const { data: me } = useSuspenseQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
  });
  return <QuizCreateForm me={me} />;
}

function QuizCreatePage() {
  return (
    <SubPage
      narrow="reading"
      title="퀴즈 만들기"
      description="공부하는 목적에 맞춰 AI가 문제를 만들고, 채점은 서버가 기준대로 해요"
    >
      <AsyncBoundary>
        {USE_MOCK ? <QuizCreateForm me={null} /> : <QuizCreateWithProfile />}
      </AsyncBoundary>
    </SubPage>
  );
}

export default QuizCreatePage;
