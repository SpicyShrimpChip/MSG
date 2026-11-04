// 회원가입과 마이페이지가 함께 쓰는 개인정보 안내 문구 (항목·목적·근거·보유 기간)
interface Notice {
  label: string;
  value: string;
}

export const REQUIRED_NOTICE: Notice[] = [
  { label: '항목', value: '이메일, 이름, 비밀번호(암호화해서 저장)' },
  { label: '목적', value: '회원 관리와 학습 서비스 제공' },
  {
    label: '근거',
    value: '서비스 이용 계약의 이행(「개인정보 보호법」 제15조제1항제4호)',
  },
  { label: '보유 기간', value: '탈퇴할 때까지' },
];

export const AI_CONSENT_NOTICE: Notice[] = [
  {
    label: '항목',
    value: '내가 직접 고른 AI 대화(이메일·전화번호 같은 개인정보를 가린 글)',
  },
  { label: '목적', value: '개념별 숙련도 분석과 판단 근거 보여 주기' },
  { label: '보유 기간', value: '동의를 철회하거나 탈퇴할 때까지' },
];

// AI 퀴즈 처리 동의(선택). 대화 분석 동의와 목적·항목이 달라 따로 받는다.
// 받는 곳(제공사)·국가·보관 기간은 아직 정하지 않아 처리방침에서 "정해지면 적음"으로 둔다 (팀 검토 항목)
export const AI_QUIZ_CONSENT_NOTICE: Notice[] = [
  {
    label: '항목',
    value:
      '퀴즈·사전·사후평가를 만들 때 고른 과목 이름·개념·시험 범위·관심 직무 분야, 사후평가 때 사전평가 문제 글, 주관식·서술형 퀴즈에 쓴 내 답(이메일·전화번호 같은 개인정보가 있으면 보내지 않음). 학습 자료 본문은 이 동의만으로 보내지 않아요(기능이 켜졌을 때 요청마다 따로 확인)',
  },
  {
    label: '목적',
    value: '학습 목적에 맞는 퀴즈 문제 만들기, 주관식·서술형 답 채점',
  },
  {
    label: '받는 곳',
    value: '외부 생성형 AI 서비스(API). 제공사는 개인정보 처리방침에 적어요',
  },
  {
    label: '보유 기간',
    value:
      '만든 퀴즈와 채점 결과는 내가 지우거나 탈퇴할 때까지. 외부 AI 제공사의 보관 기간은 제공사가 정해지면 개인정보 처리방침에 적어요',
  },
];

export function NoticeList({ items }: { items: Notice[] }) {
  return (
    <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 rounded-xl bg-surface-muted px-4 py-3 text-body">
      {items.map((item) => (
        <div key={item.label} className="contents">
          <dt className="font-bold text-heading">{item.label}</dt>
          <dd className="text-ink-soft">{item.value}</dd>
        </div>
      ))}
    </dl>
  );
}
