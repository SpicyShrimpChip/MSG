import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  BookCheck,
  CircleAlert,
  ListChecks,
  LoaderCircle,
  RotateCcw,
} from 'lucide-react';
import {
  CURRICULUM_QUERY_KEY,
  STUDY_QUERY_KEY,
  USE_MOCK,
  completeConceptStudy,
} from '@/api';
import { Button, ErrorText, StudyStatusBadge } from '@/components';
import type { ConceptStudy } from '@/types';
import { STUDY_STATUS_LABEL, checkQuizLink, formatDate } from '@/utils';

// 개념 하나의 학습 상태와 다음 행동: 학습 완료 → 확인 퀴즈 → (복습 필요면) 다시 학습.
// 학습 완료는 공부했다는 기록일 뿐이라 이해 확인·숙련도를 바꾸지 않는다. 상태·이유 문장은 서버 코드 규칙 값(파랑)이다
function ConceptStudyActions({
  study,
  materialId,
  courseId,
  onRestudy,
}: {
  study: ConceptStudy;
  materialId: number;
  courseId: number | null;
  // "다시 학습하기": 설명을 펼친다
  onRestudy: () => void;
}) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: () => completeConceptStudy(materialId, study.conceptId),
    onSuccess: (next) => {
      queryClient.setQueryData<ConceptStudy[]>(
        [...STUDY_QUERY_KEY, materialId],
        (prev) =>
          prev?.map((item) =>
            item.conceptId === next.conceptId ? next : item,
          ),
      );
      // 커리큘럼 진행률을 바로 다시 센다
      void queryClient.invalidateQueries({ queryKey: CURRICULUM_QUERY_KEY });
    },
  });

  const canComplete =
    study.status === 'not_studied' || study.status === 'review_needed';
  const latest = study.latestCheck;
  const quizHref =
    courseId === null
      ? null
      : study.openCheckQuizId !== null
        ? `/quizzes/${study.openCheckQuizId}`
        : checkQuizLink(courseId, materialId, study.conceptId, study.name);

  return (
    <div className="flex flex-col gap-3 rounded-xl border border-line bg-surface-muted p-4">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-body font-bold text-heading">학습 상태</span>
        <StudyStatusBadge status={study.status} />
        {study.studyCount > 1 && (
          <span className="text-caption text-ink-muted">
            학습 {study.studyCount}번
          </span>
        )}
        {study.lastStudiedAt && (
          <span className="text-caption text-ink-muted">
            최근 학습 {formatDate(study.lastStudiedAt)}
          </span>
        )}
      </div>
      <p className="text-body text-ink">{study.reason}</p>

      {study.status === 'review_needed' && latest && (
        <div className="flex flex-col gap-2 rounded-lg bg-weakness-soft p-3 text-body text-ink">
          <p className="flex items-center gap-1.5 font-bold text-weakness">
            <CircleAlert size={16} aria-hidden="true" />
            복습 필요
          </p>
          <p>
            틀린 문제와 AI가 쓴 개선 방향을 확인하고, 설명을 다시 읽은 뒤 "다시
            학습 완료"를 눌러 주세요. 그다음 확인 퀴즈를 다시 풀 수 있어요.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" to={`/quizzes/${latest.quizId}/result`}>
              틀린 문제·개선 방향 보기
            </Button>
            <Button variant="secondary" onClick={onRestudy}>
              <RotateCcw size={16} aria-hidden="true" />
              다시 학습하기
            </Button>
          </div>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        {canComplete ? (
          <Button
            disabled={mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? (
              <LoaderCircle
                size={16}
                className="animate-spin"
                aria-hidden="true"
              />
            ) : (
              <BookCheck size={16} aria-hidden="true" />
            )}
            {study.status === 'review_needed' ? '다시 학습 완료' : '학습 완료'}
          </Button>
        ) : (
          <Button variant="secondary" disabled>
            <BookCheck size={16} aria-hidden="true" />
            학습 완료됨
          </Button>
        )}
        {quizHref && (
          <Button
            variant={study.status === 'studied' ? 'primary' : 'secondary'}
            to={quizHref}
          >
            <ListChecks size={16} aria-hidden="true" />
            {study.openCheckQuizId !== null
              ? `${study.name} 확인 퀴즈 이어 풀기`
              : `${study.name} 확인 퀴즈 풀기`}
          </Button>
        )}
        {latest && study.status !== 'review_needed' && (
          <Button variant="secondary" to={`/quizzes/${latest.quizId}/result`}>
            최근 확인 퀴즈 결과
          </Button>
        )}
      </div>
      <ErrorText error={mutation.error} />

      {study.checkHistory.length > 0 && (
        <div className="flex flex-col gap-1">
          <p className="text-caption font-bold text-ink-soft">
            확인 퀴즈 이력 (최신 순)
          </p>
          <ul className="flex flex-col gap-0.5 text-caption text-ink-soft">
            {study.checkHistory.map((item) => (
              <li key={item.quizId}>
                {formatDate(item.submittedAt)} · {item.score}/{item.maxScore}점
                {item.rate !== null && ` (${item.rate}%)`} ·{' '}
                {STUDY_STATUS_LABEL[item.result]}
                {item.waitingCount > 0 && ` · 대기 ${item.waitingCount}`}
              </li>
            ))}
          </ul>
        </div>
      )}
      <p className="text-caption text-ink-muted">
        "학습 완료"는 공부했다는 기록이에요. 이해했는지는 확인 퀴즈 결과로만
        판단해요.
        {USE_MOCK && ' 체험 모드 기록은 이 브라우저에만 저장돼요.'}
      </p>
    </div>
  );
}

export default ConceptStudyActions;
