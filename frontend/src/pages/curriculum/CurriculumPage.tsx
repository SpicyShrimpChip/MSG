import { useState } from 'react';
import { useSuspenseQuery } from '@tanstack/react-query';
import { FileText, Route } from 'lucide-react';
import { CURRICULUM_QUERY_KEY, USE_MOCK, getCurriculum } from '@/api';
import {
  AiNote,
  AsyncBoundary,
  Button,
  Card,
  Chip,
  EmptyState,
  FormRow,
  ScoreBar,
  SelectInput,
  SubPage,
} from '@/components';
import { useCourses } from '@/hooks';
import type { CurriculumStage, CurriculumStep } from '@/types';
import { formatSemester } from '@/utils';

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
        <p className="text-lg font-extrabold text-heading">
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
      <div className="flex flex-col gap-1">
        <p className="text-body font-bold text-heading">이 순서인 이유</p>
        <ul className="flex list-inside list-disc flex-col gap-0.5 text-body text-ink">
          {step.reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      </div>
      {step.actions.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {step.actions.map((action) =>
            action.quizMode ? (
              <Button
                key={action.type}
                variant={
                  action.type === 'study_material' ? 'secondary' : 'primary'
                }
                to={`/quizzes/new?courseId=${courseId}&mode=${action.quizMode}&concept=${encodeURIComponent(step.concept)}`}
              >
                {action.label}
              </Button>
            ) : action.type === 'study_material' ? (
              <Button key={action.type} variant="secondary" to="/materials">
                {action.label}
              </Button>
            ) : (
              <a
                key={action.type}
                href="#curriculum-ai-suggestions"
                className="self-center text-body font-bold text-primary underline-offset-4 hover:underline"
              >
                {action.label}
              </a>
            ),
          )}
        </div>
      )}
    </Card>
  );
}

function CourseCurriculum({ courseId }: { courseId: number }) {
  const { data } = useSuspenseQuery({
    queryKey: [...CURRICULUM_QUERY_KEY, courseId],
    queryFn: () => getCurriculum(courseId),
  });
  if (data.steps.length === 0) {
    return (
      <EmptyState
        icon={<Route size={24} aria-hidden="true" />}
        title="과목에 키워드가 없어요"
        description="과목 키워드가 학습 순서의 개념이 돼요. 과목에 키워드를 넣어 주세요."
        action={{ label: '과목 관리', to: '/courses' }}
      />
    );
  }
  return (
    <div className="flex flex-col gap-6">
      <p className="text-body text-ink-soft">
        퀴즈 결과(채점이 끝난 답만), 대화 분석, 내 피드백을 나란히 보고 서버가
        정한 규칙으로 순서를 정했어요. 숙련도 점수가 아니에요.
        {USE_MOCK && ' 지금은 예시 데이터예요.'}
      </p>
      <ol className="flex flex-col gap-4">
        {data.steps.map((step) => (
          <StepCard key={step.conceptId} step={step} courseId={courseId} />
        ))}
      </ol>
      {data.materials.length > 0 && (
        <Card as="section" className="flex flex-col gap-2 p-5">
          <p className="flex items-center gap-2 font-bold text-heading">
            <FileText size={18} className="text-primary" aria-hidden="true" />이
            과목에 올린 자료
          </p>
          <ul className="flex flex-wrap gap-2">
            {data.materials.map((material) => (
              <li key={material.id}>
                <Button variant="secondary" to={`/materials/${material.id}`}>
                  {material.title}
                </Button>
              </li>
            ))}
          </ul>
          <p className="text-caption text-ink-muted">
            자료 본문은 아직 분석하지 않아 개념별로 나누지 않았어요.
          </p>
        </Card>
      )}
      <div id="curriculum-ai-suggestions">
        {data.aiSuggestions.length > 0 ? (
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
              결과는 위 순서를 보세요.
            </p>
          </AiNote>
        ) : (
          <p className="text-caption text-ink-muted">
            자기개발 퀴즈를 풀면 AI가 다음에 공부할 개념을 제안해요.
          </p>
        )}
      </div>
    </div>
  );
}

function CurriculumContent() {
  const { data: courses } = useCourses();
  const [courseId, setCourseId] = useState<number | null>(
    courses[0]?.id ?? null,
  );
  if (courses.length === 0 || courseId === null) {
    return (
      <EmptyState
        icon={<Route size={24} aria-hidden="true" />}
        title="먼저 과목을 만들어 주세요"
        description="과목 키워드와 퀴즈 결과로 공부할 순서를 추천해 드려요."
        action={{ label: '과목 만들기', to: '/courses' }}
      />
    );
  }
  return (
    <div className="flex max-w-3xl flex-col gap-6">
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
      <AsyncBoundary>
        <CourseCurriculum courseId={courseId} />
      </AsyncBoundary>
    </div>
  );
}

function CurriculumPage() {
  return (
    <SubPage
      title="맞춤 커리큘럼"
      description="지금 이해 정도에 맞춰 공부할 순서와 그 이유를 보여 줘요"
    >
      <AsyncBoundary>
        <CurriculumContent />
      </AsyncBoundary>
    </SubPage>
  );
}

export default CurriculumPage;
