import type { AnalysisRun } from '@/types';
import { countJudgingModels } from '@/utils';
import Panel from './Panel';

interface AnalysisSummaryPanelProps {
  run: AnalysisRun;
  layoutReason?: string | null;
}

// "11월 2일 16:20"처럼 (내 컴퓨터 시간대 기준)
function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  });
}

// 이번 분석에 쓴 기록의 양 (코드가 센 값)
function AnalysisSummaryPanel({
  run,
  layoutReason,
}: AnalysisSummaryPanelProps) {
  const models = countJudgingModels(run);
  const items = [
    { label: '분석한 대화', value: `${run.input.conversationCount}개` },
    { label: '읽은 메시지', value: `${run.input.messageCount}개` },
    { label: '판단한 모델', value: `${models.total}개 중 ${models.judged}개` },
    {
      label: '마지막 분석',
      value: formatDateTime(run.completedAt ?? run.createdAt),
    },
  ];

  return (
    <Panel title="분석 요약" layoutReason={layoutReason}>
      <dl className="grid grid-cols-2 gap-4 md:grid-cols-4">
        {items.map((item) => (
          <div key={item.label} className="flex flex-col gap-1">
            <dt className="text-caption text-ink-soft">{item.label}</dt>
            <dd className="text-xl font-extrabold text-heading">
              {item.value}
            </dd>
          </div>
        ))}
      </dl>
      {run.input.truncatedMessageCount > 0 && (
        <p className="text-caption text-ink-muted">
          너무 긴 메시지 {run.input.truncatedMessageCount}개는 앞부분만
          읽었어요.
        </p>
      )}
    </Panel>
  );
}

export default AnalysisSummaryPanel;
