import { ArrowLeft, ChevronRight, Lock } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { Button, Card, Chip, Notice, SubPage } from '@/components';
import NotFoundPage from '@/pages/NotFoundPage';
import { cn } from '@/utils';

// 회사별 대화 내보내기 도움말. 로그인 없이 열린다(가입 전에 미리 신청해 둘 수 있게)
// 다른 회사 화면 캡처는 넣지 않고 메뉴 이름만 글자로 보여 준다. 화면·메일·파일 구성은 공식 보장이 없어서
// 실제로 받아 본 날짜를 checkedAt에 적고, 바뀌면 다시 받아 보고 고친다
// ChatGPT는 준비 완료 메일(제목·버튼·걸린 시간)을 확인한 뒤 같은 모양으로 추가한다

interface HelpStep {
  title: string;
  description?: string;
  // 설정 메뉴 경로. 마지막 칸이 누를 버튼
  path?: string[];
  // 메일함에서 찾기 쉽게 메일 제목은 원문 그대로
  mailSubject?: string;
}

interface ExportHelp {
  name: string;
  summary: string;
  facts: { label: string; value: string; note: string }[];
  steps: HelpStep[];
  // 주의할 점. 걱정되는 사람만 읽도록 단계와 따로 모은다
  cautions: string[];
  checkedAt: string;
  officialUrl: string;
}

const EXPORT_HELP: Record<string, ExportHelp> = {
  // 2026-11-02 한국어 화면·메일로 확인. [Download data]는 zip이 아니라 링크 목록(manifest-....json, 6개)을 준다(2026-10-31 실제 파일). 공식 도움말: 웹·데스크톱만, 링크 24시간, Team·Enterprise는 소유자가 허용
  claude: {
    name: 'Claude',
    summary: 'Claude에서 내 대화 파일을 받는 방법이에요.',
    facts: [
      {
        label: '어디서',
        value: '웹 · 데스크톱 앱',
        note: '모바일 앱에서는 안 돼요',
      },
      {
        label: '기다리는 시간',
        value: '보통 몇 시간',
        note: '길면 12시간까지 걸려요',
      },
      {
        label: '받는 링크',
        value: '24시간 동안',
        note: '지나면 다시 신청해야 해요',
      },
    ],
    steps: [
      {
        title: '설정에서 데이터 내보내기를 눌러요',
        path: ['설정', '개인정보보호', '내 데이터', '데이터 내보내기'],
      },
      {
        title: '메일이 오면 [Download data]를 눌러요',
        description: 'json 파일 하나가 받아져요.',
        mailSubject: 'Your data is ready for download',
      },
      {
        title: '받은 json 파일을 가져오기 화면에 올려요',
        description:
          '대화 파일을 받는 버튼이 나와요. 링크는 서버로 보내지 않아요.',
      },
      {
        title: '버튼으로 받은 zip 파일을 올려요',
        description: '압축은 풀지 않아도 돼요.',
      },
    ],
    cautions: [
      '링크는 이 사이트 서버로 보내지 않아요. 파일은 브라우저에서만 읽고, 버튼을 누르면 claude.ai에서 바로 받아져요.',
      '링크는 24시간 동안만 열리고, 한 번 열면 닫힐 수 있어요. 받기가 끝날 때까지 창을 닫지 마세요.',
      '모바일 앱에서는 신청할 수 없어요.',
      '학교·회사 계정은 관리자가 허용해야 버튼이 보여요.',
      '메일은 영어로 와요. 안 보이면 스팸함도 확인해 주세요.',
      '대화에 올렸던 파일 내용은 내보내기에 들어 있지 않아요.',
      '링크가 열리지 않으면 1번부터 다시 신청해 주세요.',
    ],
    checkedAt: '2026년 11월 2일',
    officialUrl:
      'https://support.claude.com/en/articles/9450526-export-your-claude-data',
  },
};

// 서비스 바꾸기. 도움말이 아직 없는 서비스는 "준비 중"으로 보여 준다
const SERVICES = [
  { id: 'chatgpt', name: 'ChatGPT' },
  { id: 'claude', name: 'Claude' },
];

function StepItem({ step, number }: { step: HelpStep; number: number }) {
  return (
    <li className="flex gap-4 border-b border-line py-5 last:border-b-0">
      <span
        aria-hidden="true"
        className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary font-extrabold text-on-primary"
      >
        {number}
      </span>
      <div className="flex min-w-0 flex-col gap-2.5">
        <p className="font-bold text-heading">
          <span className="sr-only">{number}단계. </span>
          {step.title}
        </p>
        {step.description && (
          <p className="text-body leading-relaxed text-ink-soft">
            {step.description}
          </p>
        )}
        {step.path && (
          <p className="flex flex-wrap items-center gap-1.5 text-body">
            {step.path.map((item, index) => {
              const last = index === step.path!.length - 1;
              return (
                <span key={item} className="flex items-center gap-1.5">
                  {index > 0 && (
                    <ChevronRight
                      size={14}
                      className="text-ink-muted"
                      aria-hidden="true"
                    />
                  )}
                  <span
                    className={cn(
                      'rounded-lg px-2.5 py-1 font-medium',
                      last
                        ? 'bg-primary font-bold text-on-primary'
                        : 'bg-surface-muted text-heading',
                    )}
                  >
                    {item}
                  </span>
                </span>
              );
            })}
          </p>
        )}
        {step.mailSubject && (
          <p className="rounded-lg border border-line-strong px-3.5 py-2.5 text-body font-medium text-heading">
            메일 제목 · {step.mailSubject}
          </p>
        )}
      </div>
    </li>
  );
}

function ExportHelpPage() {
  const { service = '' } = useParams();
  const help = Object.hasOwn(EXPORT_HELP, service)
    ? EXPORT_HELP[service]
    : undefined;
  if (!help) return <NotFoundPage />;

  return (
    <SubPage
      narrow="reading"
      title={`${help.name} 대화 내보내기`}
      description={help.summary}
    >
      <div className="-mt-4 flex flex-wrap items-center justify-between gap-3">
        <Link
          to="/analysis/import"
          className="inline-flex items-center gap-1 text-body font-medium text-ink-soft hover:text-primary"
        >
          <ArrowLeft size={16} aria-hidden="true" />
          대화 가져오기
        </Link>
        <nav
          aria-label="AI 서비스 고르기"
          className="flex gap-1 rounded-xl border border-line-strong bg-surface-raised p-1"
        >
          {SERVICES.map(({ id, name }) =>
            id === service ? (
              <span
                key={id}
                aria-current="page"
                className="rounded-lg bg-primary px-4 py-1.5 text-body font-bold text-on-primary"
              >
                {name}
              </span>
            ) : Object.hasOwn(EXPORT_HELP, id) ? (
              <Link
                key={id}
                to={`/analysis/import/help/${id}`}
                className="rounded-lg px-4 py-1.5 text-body font-medium text-ink-soft hover:text-primary"
              >
                {name}
              </Link>
            ) : (
              <span
                key={id}
                className="flex items-center gap-1.5 px-3 py-1.5 text-body text-ink-muted"
              >
                {name}
                <Chip>준비 중</Chip>
              </span>
            ),
          )}
        </nav>
      </div>

      <div className="flex flex-col gap-10">
        <section aria-labelledby="help-before" className="flex flex-col gap-3">
          <h2 id="help-before" className="text-lg font-extrabold text-heading">
            시작하기 전에
          </h2>
          <ul className="grid gap-3 sm:grid-cols-3">
            {help.facts.map((fact) => (
              <li
                key={fact.label}
                className="flex flex-col gap-1.5 rounded-xl border border-line bg-surface-muted p-4"
              >
                <span className="text-caption font-bold text-ink-soft">
                  {fact.label}
                </span>
                <span className="font-extrabold text-heading">
                  {fact.value}
                </span>
                <span className="text-caption text-ink-soft">{fact.note}</span>
              </li>
            ))}
          </ul>
        </section>

        <section aria-labelledby="help-steps" className="flex flex-col gap-3">
          <h2 id="help-steps" className="text-lg font-extrabold text-heading">
            받는 순서
          </h2>
          <Card className="px-5 md:px-6">
            <ol>
              {help.steps.map((step, index) => (
                <StepItem key={step.title} step={step} number={index + 1} />
              ))}
            </ol>
          </Card>
          <Button to="/analysis/import" size="lg" className="mt-2">
            받은 파일 올리러 가기
          </Button>
        </section>

        <section aria-labelledby="help-caution" className="flex flex-col gap-3">
          <h2 id="help-caution" className="text-lg font-extrabold text-heading">
            주의할 점
          </h2>
          <ul className="flex list-disc flex-col gap-2 rounded-xl border border-line-strong py-4 pr-4 pl-9 text-body leading-relaxed text-ink-soft">
            {help.cautions.map((caution) => (
              <li key={caution}>{caution}</li>
            ))}
          </ul>
        </section>

        <Notice icon={Lock} iconClassName="text-primary">
          <p>
            원본 파일은 서버로 보내지 않아요. 내가 고른 대화만 개인정보를 가린
            뒤 보내요.
          </p>
        </Notice>

        <footer className="flex flex-col gap-1.5 border-t border-line pt-5 text-caption text-ink-soft">
          <p className="font-bold">마지막 확인 · {help.checkedAt}</p>
          <p className="leading-relaxed">
            화면이 다르게 보이면{' '}
            <a
              href={help.officialUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="font-bold text-primary hover:underline"
            >
              {help.name} 공식 도움말(영어, 새 탭)
            </a>
            을 확인해 주세요.
          </p>
        </footer>
      </div>
    </SubPage>
  );
}

export default ExportHelpPage;
