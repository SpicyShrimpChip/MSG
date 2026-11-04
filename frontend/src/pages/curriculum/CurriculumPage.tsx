import { useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useSuspenseQuery } from '@tanstack/react-query';
import {
  ArrowLeft,
  ArrowRight,
  BookOpen,
  Check,
  ClipboardCheck,
  FileText,
  ListChecks,
  RotateCcw,
  Route,
  Trophy,
} from 'lucide-react';
import { CURRICULUM_QUERY_KEY, USE_MOCK, getCurriculum } from '@/api';
import {
  AiNote,
  AsyncBoundary,
  Button,
  Card,
  Chip,
  EmptyState,
  FormRow,
  LevelBadge,
  ScoreBar,
  SelectInput,
  StudyStatusBadge,
  SubPage,
} from '@/components';
import { useCourses } from '@/hooks';
import type {
  Curriculum,
  CurriculumFlow,
  CurriculumStage,
  CurriculumStep,
  FlowAssessment,
  FlowConcept,
  FlowStepKey,
} from '@/types';
import {
  checkQuizLink,
  cn,
  conceptStudyLink,
  formatDate,
  formatSemester,
} from '@/utils';

const STAGE_LABEL: Record<CurriculumStage, string> = {
  priority_study: '더 공부할래요',
  review: '복습 필요',
  analysis_weak: '대화 분석상 보완',
  needs_check: '추가 확인 필요',
  not_assessed: '아직 근거 없음',
  ready: '다음 단계 준비',
};

const SUGGESTION_LABEL = {
  next: '다음 단계',
  prerequisite: '선수 지식',
  related: '연관 분야',
} as const;

const STEP_ICON: Record<FlowStepKey, ReactNode> = {
  pre_test: <ClipboardCheck size={18} aria-hidden="true" />,
  concept_study: <BookOpen size={18} aria-hidden="true" />,
  concept_quiz: <ListChecks size={18} aria-hidden="true" />,
  post_test: <Trophy size={18} aria-hidden="true" />,
};

const STATUS_LABEL = {
  done: '완료',
  current: '지금 할 단계',
  upcoming: '다음에 할 단계',
} as const;

const anchor = (key: FlowStepKey) => `curriculum-step-${key}`;

// 개념 공부 화면: 자료 상세 "핵심 개념" 탭에서 그 개념을 펼친다
const conceptLink = (concept: FlowConcept) =>
  concept.materialId === null
    ? null
    : `/materials/${concept.materialId}?tab=concepts&concept=${encodeURIComponent(concept.conceptId)}`;

const quizLink = (courseId: number, mode: string, concept: string) =>
  `/quizzes/new?courseId=${courseId}&mode=${mode}&concept=${encodeURIComponent(concept)}`;

// ---------- 단계 표시 (맨 위) ----------

function FlowStepper({ flow }: { flow: CurriculumFlow }) {
  return (
    <nav aria-label="학습 단계">
      <ol className="grid gap-2 sm:grid-cols-4">
        {flow.stages.map((stage) => (
          <li key={stage.key}>
            <a
              href={`#${anchor(stage.key)}`}
              aria-current={stage.status === 'current' ? 'step' : undefined}
              className={cn(
                'flex h-full flex-col gap-1 rounded-xl border p-3 hover:border-primary',
                stage.status === 'current'
                  ? 'border-primary bg-primary-soft'
                  : 'border-line bg-surface-raised',
              )}
            >
              <span className="flex items-center gap-2 text-caption font-bold text-ink-soft">
                <span
                  className={cn(
                    'flex size-6 items-center justify-center rounded-full text-caption',
                    stage.status === 'done'
                      ? 'bg-primary text-on-primary'
                      : stage.status === 'current'
                        ? 'border-2 border-primary text-primary'
                        : 'border border-line-strong text-ink-muted',
                  )}
                  aria-hidden="true"
                >
                  {stage.status === 'done' ? <Check size={14} /> : stage.order}
                </span>
                STEP {stage.order}
              </span>
              <span className="font-bold text-heading">{stage.title}</span>
              <span
                className={cn(
                  'text-caption',
                  stage.status === 'current'
                    ? 'text-primary'
                    : 'text-ink-muted',
                )}
              >
                {STATUS_LABEL[stage.status]}
              </span>
            </a>
          </li>
        ))}
      </ol>
    </nav>
  );
}

// ---------- 단계 하나의 틀 ----------

function StepSection({
  flow,
  stepKey,
  children,
}: {
  flow: CurriculumFlow;
  stepKey: FlowStepKey;
  children: ReactNode;
}) {
  const index = flow.stages.findIndex((stage) => stage.key === stepKey);
  const stage = flow.stages[index];
  const prev = flow.stages[index - 1];
  const next = flow.stages[index + 1];
  const titleId = `${anchor(stepKey)}-title`;
  return (
    <Card
      as="section"
      aria-labelledby={titleId}
      className={cn(
        'flex scroll-mt-28 flex-col gap-4 p-5 md:p-6',
        stage.status === 'current' && 'border-2 border-primary',
      )}
    >
      <div id={anchor(stepKey)} className="flex flex-wrap items-center gap-2">
        <span className="text-primary">{STEP_ICON[stepKey]}</span>
        <h2 id={titleId} className="text-lg font-extrabold text-heading">
          STEP {stage.order}. {stage.title}
        </h2>
        <Chip tone={stage.status === 'upcoming' ? 'neutral' : 'primary'}>
          {STATUS_LABEL[stage.status]}
        </Chip>
      </div>
      <p className="text-body text-ink">{stage.summary}</p>
      {children}
      <div className="flex flex-wrap justify-between gap-2 border-t border-line pt-3 text-caption">
        {prev ? (
          <a
            href={`#${anchor(prev.key)}`}
            className="inline-flex items-center gap-1 font-bold text-primary underline-offset-4 hover:underline"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            이전: {prev.title}
          </a>
        ) : (
          <span />
        )}
        {next && (
          <a
            href={`#${anchor(next.key)}`}
            className="inline-flex items-center gap-1 font-bold text-primary underline-offset-4 hover:underline"
          >
            다음: {next.title}
            <ArrowRight size={14} aria-hidden="true" />
          </a>
        )}
      </div>
    </Card>
  );
}

function AssessmentScore({ test }: { test: FlowAssessment }) {
  if (test.status !== 'submitted') return null;
  return (
    <div className="flex flex-col gap-1.5">
      {test.rate !== null && <ScoreBar value={test.rate} />}
      <p className="text-caption text-ink-soft">
        {test.score}/{test.maxScore}점{test.rate !== null && ` (${test.rate}%)`}
        {test.pendingCount > 0 && ` · 채점 대기·확인 ${test.pendingCount}문제`}
      </p>
    </div>
  );
}

function PreStatusBadge({ concept }: { concept: FlowConcept }) {
  if (concept.preStatus === 'weak') return <LevelBadge level="weakness" />;
  if (concept.preStatus === 'ok')
    return <Chip tone="primary">사전 {concept.preRate}% · 기준 이상</Chip>;
  return <Chip tone="neutral">사전 결과 없음</Chip>;
}

// ---------- STEP 1 ----------

function PreTestStep({ flow }: { flow: CurriculumFlow }) {
  const { preTest, assessmentMaterial } = flow;
  return (
    <StepSection flow={flow} stepKey="pre_test">
      <AssessmentScore test={preTest} />
      <div className="flex flex-wrap gap-2">
        {preTest.status === 'none' &&
          (assessmentMaterial ? (
            <Button to={`/assessments?materialId=${assessmentMaterial.id}`}>
              사전 테스트 보러 가기
            </Button>
          ) : (
            <Button to="/materials/upload">자료 올리기</Button>
          ))}
        {preTest.status === 'not_submitted' && preTest.quizId !== null && (
          <Button to={`/quizzes/${preTest.quizId}`}>사전 테스트 풀기</Button>
        )}
        {preTest.status === 'submitted' && preTest.quizId !== null && (
          <Button variant="secondary" to={`/quizzes/${preTest.quizId}/result`}>
            사전 테스트 결과 보기
          </Button>
        )}
      </div>
      {assessmentMaterial && (
        <p className="text-caption text-ink-muted">
          평가 자료: {assessmentMaterial.title}
        </p>
      )}
    </StepSection>
  );
}

// ---------- STEP 2 ----------

// 학습 진행률: 실제 "학습 완료" 기록으로 센 서버 값 (페이지를 열거나 퀴즈를 만든 것만으로는 세지 않음)
function StudyProgressBar({ flow }: { flow: CurriculumFlow }) {
  const { progress } = flow;
  if (progress.studiableConcepts === 0 || progress.percent === null) {
    return (
      <p className="text-caption text-ink-soft">
        자료에서 핵심 개념을 뽑으면 개념마다 "학습 완료"를 기록하고 진행률을 볼
        수 있어요.
      </p>
    );
  }
  return (
    <div className="flex flex-col gap-1.5">
      <ScoreBar value={progress.percent} />
      <p className="text-body text-ink">
        총 {progress.studiableConcepts}개 개념 중{' '}
        <strong className="text-primary">
          {progress.studiedConcepts}개 학습 완료 ({progress.percent}%)
        </strong>
        {progress.reviewNeededConcepts > 0 &&
          ` · 복습 필요 ${progress.reviewNeededConcepts}개`}
      </p>
      {progress.lastStudiedConcept && progress.lastStudiedAt && (
        <p className="text-caption text-ink-muted">
          최근 학습: {progress.lastStudiedConcept} (
          {formatDate(progress.lastStudiedAt)})
        </p>
      )}
    </div>
  );
}

function ConceptStudyStep({ flow }: { flow: CurriculumFlow }) {
  const ordered = [
    ...flow.concepts.filter((concept) => concept.priority),
    ...flow.concepts.filter((concept) => !concept.priority),
  ];
  return (
    <StepSection flow={flow} stepKey="concept_study">
      <StudyProgressBar flow={flow} />
      {flow.conceptSource === 'course' && (
        <p className="text-caption text-ink-soft">
          지금 개념은 과목 키워드예요. 자료 상세의 "핵심 개념" 탭에서 개념을
          뽑으면 자료를 근거로 한 자세한 설명으로 공부할 수 있어요.
        </p>
      )}
      {ordered.length > 0 && (
        <ul className="grid gap-3 md:grid-cols-2">
          {ordered.map((concept) => {
            const link = conceptLink(concept);
            const review = concept.study?.status === 'review_needed';
            return (
              <li
                key={concept.conceptId}
                className={cn(
                  'flex flex-col gap-2 rounded-xl border p-4',
                  concept.priority || review
                    ? 'border-weakness/40 bg-weakness-soft'
                    : 'border-line',
                )}
              >
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-bold text-heading">{concept.name}</p>
                  {concept.priority && <Chip tone="primary">먼저 공부</Chip>}
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {concept.study ? (
                    <StudyStatusBadge status={concept.study.status} />
                  ) : (
                    <Chip tone="neutral">학습 기록 없음 (과목 키워드)</Chip>
                  )}
                  <PreStatusBadge concept={concept} />
                  {concept.hasStudyNote && (
                    <Chip tone="neutral">자세한 설명 있음</Chip>
                  )}
                </div>
                {concept.materialTitle && (
                  <p className="flex items-center gap-1 text-caption text-ink-muted">
                    <FileText size={12} aria-hidden="true" />
                    {concept.materialTitle}
                  </p>
                )}
                {link ? (
                  <Button
                    variant={
                      concept.priority || review ? 'primary' : 'secondary'
                    }
                    to={link}
                  >
                    {review ? (
                      <RotateCcw size={16} aria-hidden="true" />
                    ) : (
                      <BookOpen size={16} aria-hidden="true" />
                    )}
                    {review ? '다시 학습하기' : '개념 보기'}
                  </Button>
                ) : (
                  <p className="text-caption text-ink-soft">
                    이 과목에 자료를 올리면 자료로 개념을 공부할 수 있어요.
                  </p>
                )}
              </li>
            );
          })}
        </ul>
      )}
    </StepSection>
  );
}

// ---------- STEP 3 ----------

const PRACTICE_LABEL: Record<FlowConcept['practiceStatus'], string> = {
  not_started: '아직 안 풂',
  pending: '채점 대기',
  weak: '기준 미만',
  ok: '기준 이상',
};

// 자료 개념: 개념 확인 퀴즈 결과(학습 상태)로 보여 준다
function StudiedConceptRow({
  concept,
  courseId,
}: {
  concept: FlowConcept;
  courseId: number;
}) {
  const study = concept.study!;
  const latest = study.latestCheck;
  const quizHref =
    study.openCheckQuizId !== null
      ? `/quizzes/${study.openCheckQuizId}`
      : checkQuizLink(courseId, study.materialId, study.conceptId, study.name);
  return (
    <li className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-bold text-heading">{concept.name}</p>
          {concept.priority && <Chip tone="primary">먼저 확인</Chip>}
          <StudyStatusBadge status={study.status} />
        </div>
        {latest ? (
          <div className="flex max-w-80 flex-col gap-1">
            {latest.rate !== null && (
              <ScoreBar
                value={latest.rate}
                level={study.status === 'review_needed' ? 'weakness' : 'normal'}
              />
            )}
            <p className="text-caption text-ink-soft">
              최근 확인 퀴즈 {latest.score}/{latest.maxScore}점
              {latest.rate !== null && ` (${latest.rate}%)`}
              {latest.waitingCount > 0 && ` · 대기 ${latest.waitingCount}`} ·
              응시 {study.checkHistory.length}번
            </p>
          </div>
        ) : (
          <p className="text-caption text-ink-soft">
            {study.studied
              ? '학습을 완료했어요. 확인 퀴즈를 풀어 보세요.'
              : '아직 확인 퀴즈를 풀지 않았어요. 먼저 개념을 공부하면 좋아요.'}
          </p>
        )}
      </div>
      <div className="flex flex-wrap gap-2">
        {study.status === 'review_needed' ? (
          <>
            <Button to={conceptStudyLink(study.materialId, study.conceptId)}>
              <RotateCcw size={16} aria-hidden="true" />
              다시 학습하기
            </Button>
            {latest && (
              <Button
                variant="secondary"
                to={`/quizzes/${latest.quizId}/result`}
              >
                결과 보기
              </Button>
            )}
          </>
        ) : (
          <>
            <Button
              variant={study.status === 'studied' ? 'primary' : 'secondary'}
              to={quizHref}
            >
              <ListChecks size={16} aria-hidden="true" />
              {study.openCheckQuizId !== null
                ? '이어 풀기'
                : latest
                  ? '다시 풀기'
                  : '확인 퀴즈 풀기'}
            </Button>
            {latest && (
              <Button
                variant="secondary"
                to={`/quizzes/${latest.quizId}/result`}
              >
                결과 보기
              </Button>
            )}
          </>
        )}
      </div>
    </li>
  );
}

// 과목 키워드뿐인 개념: 예전처럼 일반 퀴즈 결과로 보여 준다
function KeywordConceptRow({
  concept,
  courseId,
  mode,
}: {
  concept: FlowConcept;
  courseId: number;
  mode: string;
}) {
  const rate =
    concept.practiceMaxScore === 0
      ? 0
      : Math.round((concept.practiceScore / concept.practiceMaxScore) * 100);
  return (
    <li className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <p className="font-bold text-heading">{concept.name}</p>
          {concept.priority && <Chip tone="primary">먼저 확인</Chip>}
          {concept.practiceStatus === 'weak' ? (
            <LevelBadge level="weakness" />
          ) : (
            <Chip
              tone={concept.practiceStatus === 'ok' ? 'primary' : 'neutral'}
            >
              {PRACTICE_LABEL[concept.practiceStatus]}
            </Chip>
          )}
        </div>
        {concept.practiceGradedCount > 0 && (
          <div className="flex max-w-80 flex-col gap-1">
            <ScoreBar
              value={rate}
              level={concept.practiceStatus === 'weak' ? 'weakness' : 'normal'}
            />
            <p className="text-caption text-ink-soft">
              퀴즈 {concept.practiceGradedCount}문제 · {concept.practiceScore}/
              {concept.practiceMaxScore}점 ({rate}%)
            </p>
          </div>
        )}
      </div>
      <Button variant="secondary" to={quizLink(courseId, mode, concept.name)}>
        <ListChecks size={16} aria-hidden="true" />
        {concept.practiceGradedCount > 0 ? '다시 풀기' : '퀴즈 풀기'}
      </Button>
    </li>
  );
}

function ConceptQuizStep({
  flow,
  courseId,
  mode,
}: {
  flow: CurriculumFlow;
  courseId: number;
  mode: string;
}) {
  return (
    <StepSection flow={flow} stepKey="concept_quiz">
      {flow.concepts.length > 0 && (
        <ul className="flex flex-col divide-y divide-line rounded-xl border border-line">
          {flow.concepts.map((concept) =>
            concept.study ? (
              <StudiedConceptRow
                key={concept.conceptId}
                concept={concept}
                courseId={courseId}
              />
            ) : (
              <KeywordConceptRow
                key={concept.conceptId}
                concept={concept}
                courseId={courseId}
                mode={mode}
              />
            ),
          )}
        </ul>
      )}
      <p className="text-caption text-ink-muted">
        확인 퀴즈는 그 개념 문제만 나와요. 채점이 끝난 답만 점수에 넣고, 채점을
        기다리거나 AI 판단이 엇갈린 답은 틀린 답으로 세지 않아 "추가 확인
        필요"로 보여 줘요. 기준은 정답률 60%예요.
      </p>
    </StepSection>
  );
}

// ---------- STEP 4 ----------

function PostTestStep({ flow }: { flow: CurriculumFlow }) {
  const { preTest, postTest, assessmentMaterial, overallDelta } = flow;
  const preDone = preTest.status === 'submitted';
  const compared = flow.concepts.filter((concept) => concept.postRate !== null);
  // 복습 추천: 확인 퀴즈에서 복습이 필요하다고 나온 개념 (서버 상태 그대로)
  const review = flow.concepts.filter(
    (concept) => concept.study?.status === 'review_needed',
  );
  return (
    <StepSection flow={flow} stepKey="post_test">
      {review.length > 0 && (
        <div className="flex flex-col gap-2 rounded-xl bg-weakness-soft p-4">
          <p className="text-body font-bold text-heading">
            사후 평가 전에 복습하면 좋은 개념
          </p>
          <div className="flex flex-wrap gap-2">
            {review.map((concept) => (
              <Button
                key={concept.conceptId}
                variant="secondary"
                to={conceptStudyLink(
                  concept.study!.materialId,
                  concept.study!.conceptId,
                )}
              >
                <RotateCcw size={16} aria-hidden="true" />
                {concept.name} 다시 학습하기
              </Button>
            ))}
          </div>
        </div>
      )}
      {postTest.status === 'submitted' && (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-baseline gap-3">
            <p className="text-body text-ink-soft">
              사전 {preTest.rate ?? '-'}% → 사후 {postTest.rate ?? '-'}%
            </p>
            {overallDelta !== null && (
              <p className="text-2xl font-extrabold text-primary">
                {overallDelta >= 0 ? '+' : ''}
                {overallDelta}%p
              </p>
            )}
          </div>
          {compared.length > 0 && (
            <ul className="flex flex-col gap-1 text-body">
              {compared.map((concept) => (
                <li key={concept.conceptId} className="flex flex-wrap gap-2">
                  <span className="font-bold text-heading">{concept.name}</span>
                  <span className="text-ink-soft">
                    {concept.preRate ?? '-'}% → {concept.postRate}%
                    {concept.delta !== null &&
                      ` (${concept.delta >= 0 ? '+' : ''}${concept.delta}%p)`}
                  </span>
                  {concept.postStatus === 'still_weak' && (
                    <LevelBadge level="weakness" />
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="flex flex-wrap gap-2">
        {!preDone ? (
          <p className="text-caption text-ink-soft">
            사전 테스트를 먼저 봐야 같은 기준으로 비교할 수 있어요.
          </p>
        ) : postTest.status === 'none' ? (
          assessmentMaterial && (
            <Button to={`/assessments?materialId=${assessmentMaterial.id}`}>
              사후 평가 보러 가기
            </Button>
          )
        ) : postTest.status === 'not_submitted' && postTest.quizId !== null ? (
          <Button to={`/quizzes/${postTest.quizId}`}>사후 평가 풀기</Button>
        ) : (
          <>
            <Button variant="secondary" to="/analysis">
              대시보드에서 비교 보기
            </Button>
            {postTest.quizId !== null && (
              <Button
                variant="secondary"
                to={`/quizzes/${postTest.quizId}/result`}
              >
                사후 평가 결과 보기
              </Button>
            )}
          </>
        )}
      </div>
      <p className="text-caption text-ink-muted">
        사후 평가는 사전 테스트와 같은 개념·유형·문항 수·난이도로 새 문제를
        만들어요. 점수는 채점이 끝난 답만 센 값이고 숙련도 점수가 아니에요.
      </p>
    </StepSection>
  );
}

// ---------- 기존 개념별 복습 우선순위 (근거) ----------

function StepCard({
  step,
  courseId,
}: {
  step: CurriculumStep;
  courseId: number;
}) {
  const { evidence } = step;
  const percent =
    evidence.maxScore === 0
      ? 0
      : Math.round((evidence.score / evidence.maxScore) * 100);
  return (
    <Card as="li" className="flex flex-col gap-3 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-extrabold text-heading">
          <span className="mr-2 text-primary">{step.order}.</span>
          {step.concept}
        </p>
        <Chip tone="primary">{STAGE_LABEL[step.stage]}</Chip>
      </div>
      {evidence.gradedCount > 0 && (
        <div className="flex flex-col gap-1.5">
          <ScoreBar
            value={percent}
            level={step.stage === 'review' ? 'weakness' : 'normal'}
          />
          <p className="text-caption text-ink-soft">
            퀴즈 {evidence.gradedCount}문제 채점 · {evidence.score}/
            {evidence.maxScore}점 · 만점 {evidence.correctCount}문제
            {evidence.pendingCount + evidence.reviewCount > 0 &&
              ` · 대기·확인 ${evidence.pendingCount + evidence.reviewCount}`}
          </p>
        </div>
      )}
      <ul className="flex list-inside list-disc flex-col gap-0.5 text-body text-ink">
        {step.reasons.map((reason) => (
          <li key={reason}>{reason}</li>
        ))}
      </ul>
      {step.actions.some((action) => action.quizMode) && (
        <div className="flex flex-wrap gap-2">
          {step.actions
            .filter((action) => action.quizMode)
            .map((action) => (
              <Button
                key={action.type}
                variant="secondary"
                to={quizLink(courseId, action.quizMode!, step.concept)}
              >
                {action.label}
              </Button>
            ))}
        </div>
      )}
    </Card>
  );
}

function PriorityDetails({ data }: { data: Curriculum }) {
  if (data.steps.length === 0) return null;
  return (
    <details className="group rounded-2xl border border-line bg-surface-raised p-5">
      <summary className="cursor-pointer font-bold text-heading">
        개념별 복습 우선순위와 근거 보기
        <span className="ml-2 text-caption font-medium text-ink-muted">
          모든 퀴즈·대화 분석·내 피드백 기준
        </span>
      </summary>
      <div className="mt-4 flex flex-col gap-3">
        <p className="text-caption text-ink-soft">
          퀴즈 결과(채점이 끝난 답만, 사전·사후 평가 포함), 대화 분석, 내
          피드백을 나란히 보고 서버가 정한 규칙으로 순서를 정했어요. 숙련도
          점수가 아니에요.
        </p>
        <ol className="flex flex-col gap-3">
          {data.steps.map((step) => (
            <StepCard
              key={step.conceptId}
              step={step}
              courseId={data.courseId}
            />
          ))}
        </ol>
      </div>
    </details>
  );
}

// ---------- 과목 하나 ----------

function CourseCurriculum({ courseId }: { courseId: number }) {
  const { data } = useSuspenseQuery({
    queryKey: [...CURRICULUM_QUERY_KEY, courseId],
    queryFn: () => getCurriculum(courseId),
  });
  const { flow } = data;
  if (flow.concepts.length === 0 && data.steps.length === 0) {
    return (
      <EmptyState
        icon={<Route size={24} aria-hidden="true" />}
        title="공부할 개념이 없어요"
        description="과목 키워드를 넣거나 자료에서 핵심 개념을 뽑으면 사전 테스트부터 사후 평가까지 순서대로 안내해 드려요."
        action={{ label: '과목 관리', to: '/courses' }}
      />
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <p className="text-body text-ink-soft">
        사전 테스트 → 핵심 개념 학습 → 개념별 확인 퀴즈 → 사후 평가 순서로
        공부해요. 지금 단계는 제출·채점 기록으로 서버가 정한 것이고, 어느
        단계로든 바로 갈 수 있어요.
        {USE_MOCK && ' 지금은 예시 데이터예요.'}
      </p>
      <FlowStepper flow={flow} />
      {flow.currentStep === 'done' && (
        <Card className="flex items-center gap-3 border-primary bg-primary-soft p-4">
          <Trophy size={20} className="text-primary" aria-hidden="true" />
          <p className="text-body text-heading">
            4단계를 모두 마쳤어요. 아직 부족한 개념은 확인 퀴즈로 다시 풀어
            보세요.
          </p>
        </Card>
      )}
      <PreTestStep flow={flow} />
      <ConceptStudyStep flow={flow} />
      <ConceptQuizStep
        flow={flow}
        courseId={courseId}
        mode={data.recommendedMode}
      />
      <PostTestStep flow={flow} />

      <PriorityDetails data={data} />

      {data.aiSuggestions.length > 0 && (
        <AiNote title="다음에 공부할 개념 제안">
          <ul className="flex flex-col gap-1">
            {data.aiSuggestions.map((item) => (
              <li key={`${item.kind}-${item.concept}`}>
                <span className="font-bold">
                  [{SUGGESTION_LABEL[item.kind]}] {item.concept}
                </span>
                {item.aiReason && ` — ${item.aiReason}`}
              </li>
            ))}
          </ul>
          <p className="mt-2 text-caption text-ink-soft">
            AI가 자기개발 퀴즈를 만들 때 함께 제안한 개념이에요. 실제 학습
            결과는 위 단계를 보세요.
          </p>
        </AiNote>
      )}
    </div>
  );
}

function CurriculumContent() {
  const { data: courses } = useCourses();
  const [params] = useSearchParams();
  const requested = Number(params.get('courseId'));
  const [courseId, setCourseId] = useState<number | null>(
    courses.some((course) => course.id === requested)
      ? requested
      : (courses[0]?.id ?? null),
  );
  if (courses.length === 0 || courseId === null) {
    return (
      <EmptyState
        icon={<Route size={24} aria-hidden="true" />}
        title="먼저 과목을 만들어 주세요"
        description="과목을 만들고 자료를 올리면 사전 테스트부터 사후 평가까지 학습 순서를 안내해 드려요."
        action={{ label: '과목 만들기', to: '/courses' }}
      />
    );
  }
  return (
    <div className="flex max-w-4xl flex-col gap-6">
      <FormRow label="과목" htmlFor="curriculum-course">
        <SelectInput
          id="curriculum-course"
          value={courseId}
          onChange={(e) => setCourseId(Number(e.target.value))}
        >
          {courses.map((course) => (
            <option key={course.id} value={course.id}>
              {course.name} · {formatSemester(course.semester)}
            </option>
          ))}
        </SelectInput>
      </FormRow>
      <AsyncBoundary key={courseId}>
        <CourseCurriculum courseId={courseId} />
      </AsyncBoundary>
    </div>
  );
}

function CurriculumPage() {
  return (
    <SubPage
      title="맞춤 커리큘럼"
      description="사전 테스트로 지금 수준을 확인하고, 부족한 개념부터 공부한 뒤 사후 평가로 얼마나 나아졌는지 확인해요"
    >
      <AsyncBoundary>
        <CurriculumContent />
      </AsyncBoundary>
    </SubPage>
  );
}

export default CurriculumPage;
