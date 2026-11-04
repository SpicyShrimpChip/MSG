import { useSuspenseQuery } from '@tanstack/react-query';
import { CURRICULUM_QUERY_KEY, getCurriculum } from '@/api';
import { Button, ScoreBar } from '@/components';
import { formatDate } from '@/utils';
import Panel from './Panel';

// 개념별 학습 진도 (맞춤 커리큘럼 응답의 flow.progress를 그대로 쓴다). 학습 완료 기록·확인 퀴즈 결과로 서버가 센 값이고,
// 대화 분석 판단·내 피드백과 섞지 않는다. "학습 완료"는 공부했다는 기록일 뿐 숙련도가 아니다
function StudyProgressPanel({ courseId }: { courseId: number }) {
  const { data } = useSuspenseQuery({
    queryKey: [...CURRICULUM_QUERY_KEY, courseId],
    queryFn: () => getCurriculum(courseId),
  });
  const { progress } = data.flow;
  const stats = [
    { label: '학습 완료', value: progress.studiedConcepts },
    { label: '확인 퀴즈 응시', value: progress.checkedConcepts },
    { label: '이해 확인', value: progress.understoodConcepts },
    { label: '복습 필요', value: progress.reviewNeededConcepts },
    { label: '추가 확인 필요', value: progress.needsCheckConcepts },
  ];
  return (
    <Panel
      title="개념별 학습 진도"
      description="학습 완료 기록과 개념 확인 퀴즈 결과로 센 값이에요. 대화 분석 결과와는 따로 봐요."
    >
      {progress.studiableConcepts === 0 || progress.percent === null ? (
        <p className="text-body text-ink-soft">
          자료에서 핵심 개념을 뽑으면 개념마다 학습 완료와 확인 퀴즈 결과를
          기록해요.
        </p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1.5">
            <ScoreBar value={progress.percent} />
            <p className="text-body text-ink">
              총 {progress.studiableConcepts}개 개념 중{' '}
              <strong className="text-primary">
                {progress.studiedConcepts}개 학습 완료 ({progress.percent}%)
              </strong>
            </p>
          </div>
          <dl className="grid grid-cols-2 gap-2 sm:grid-cols-5">
            {stats.map((item) => (
              <div
                key={item.label}
                className="flex flex-col gap-0.5 rounded-lg bg-surface-muted p-3"
              >
                <dt className="text-caption text-ink-soft">{item.label}</dt>
                <dd className="text-lg font-extrabold text-heading">
                  {item.value}개
                </dd>
              </div>
            ))}
          </dl>
          {progress.lastStudiedConcept && progress.lastStudiedAt && (
            <p className="text-caption text-ink-muted">
              최근 학습: {progress.lastStudiedConcept} (
              {formatDate(progress.lastStudiedAt)})
            </p>
          )}
        </div>
      )}
      <div>
        <Button variant="secondary" to={`/curriculum?courseId=${courseId}`}>
          맞춤 커리큘럼에서 이어 공부하기
        </Button>
      </div>
    </Panel>
  );
}

export default StudyProgressPanel;
