import { SubPage } from '@/components';

// 개인정보 처리방침 초안. 항목·순서는 개인정보보호위원회 「2026 개인정보 처리방침 작성지침」(2026-04-24 개정,
// 생성형 AI 부록 포함)의 필수·해당 시·권장 항목을 따르고, 내용은 지금 코드가 실제로 하는 처리
// (docs/backend-design.md, docs/frontend-requests.md)에 맞춘다. 처리 방식이 바뀌면 이 파일도 같은 PR에서 고친다.
// 해당하지 않아 뺀 항목: 민감정보, 가명정보, 국내대리인, 영상정보처리기기
// TODO(미결): LLM 제공사·국가, 호스팅·DB 서비스, 학습 자료 원본의 장기 보관 기간, 만 14세 미만 가입, 보호책임자, 시행일
// → 정해지면 아래 PENDING 문구를 실제 값으로 바꾼다 (서비스 공개 전 필수, 팀·멘토 검토 필요)

type Block =
  | { type: 'p'; text: string }
  | { type: 'ul'; items: string[] }
  | { type: 'table'; head: string[]; rows: string[][] };

interface Section {
  id: string;
  title: string;
  blocks: Block[];
}

const PENDING = '(정해지는 대로 적어 둘게요)';

const SECTIONS: Section[] = [
  {
    id: 'purpose',
    title: '1. 개인정보를 처리하는 목적',
    blocks: [
      {
        type: 'p',
        text: '서비스는 아래 목적으로만 개인정보를 처리해요. 목적이 바뀌면 「개인정보 보호법」 제18조에 따라 따로 동의를 받는 등 필요한 조치를 해요.',
      },
      {
        type: 'ul',
        items: [
          '회원 가입과 관리: 본인 확인, 로그인 유지, 반복 로그인 실패로 인한 부정 이용 방지',
          '학습 분석: 올린 학습 자료와 이용자가 고른 AI 대화로 개념별 숙련도를 계산하고 판단 근거를 보여 주기',
          '학습 도움: 요약·퀴즈·맞춤 학습 순서 만들기',
          '서비스 안내: 공지와 문의 답변',
        ],
      },
    ],
  },
  {
    id: 'items',
    title: '2. 처리하는 개인정보 항목과 근거',
    blocks: [
      {
        type: 'p',
        text: '서비스 제공에 필요한 최소한의 정보만 처리해요. 동의 없이 처리하는 정보와 동의를 받아 처리하는 정보를 나눠 알려 드려요.',
      },
      {
        type: 'table',
        head: ['구분', '항목', '모으는 때', '법적 근거'],
        rows: [
          [
            '동의 없이 처리',
            '이메일, 이름, 비밀번호(암호화해서 저장), 이용약관·개인정보 수집·이용 안내를 확인한 시각',
            '회원가입',
            '「개인정보 보호법」 제15조제1항제4호(계약 체결·이행)',
          ],
          [
            '동의 없이 처리',
            '내 과목(이름·학기·키워드), 올린 학습 자료와 그 내용에서 뽑은 글, 퀴즈 풀이 기록, AI가 만든 요약·퀴즈·설명',
            '서비스를 이용할 때',
            '「개인정보 보호법」 제15조제1항제4호(계약 체결·이행)',
          ],
          [
            '동의 없이 처리',
            '로그인 유지용 토큰, 로그인 시도 기록(접속 IP)',
            '로그인할 때 자동 생성',
            '「개인정보 보호법」 제15조제1항제4호(계약 체결·이행)',
          ],
          [
            '동의를 받아 처리(선택)',
            '이용자가 직접 고른 AI 대화(이메일·전화번호 같은 개인정보를 가린 뒤의 글), AI 대화 분석 동의 시각',
            'AI 대화 분석에 동의하고 대화를 보낼 때',
            '「개인정보 보호법」 제15조제1항제1호(동의)',
          ],
        ],
      },
      {
        type: 'p',
        text: 'AI 대화 분석에 동의하지 않아도 학습 자료로 공부하는 기능은 그대로 쓸 수 있어요. 올리는 자료와 대화에는 본인이나 다른 사람의 주민등록번호·건강 정보 같은 민감한 정보를 넣지 않도록 주의해 주세요.',
      },
    ],
  },
  {
    id: 'on-device',
    title: '3. 내 브라우저 안에서만 처리하는 정보',
    blocks: [
      {
        type: 'p',
        text: 'AI 대화 가져오기는 개인정보를 서버로 보내기 전에 이용자의 브라우저 안에서 먼저 처리해요.',
      },
      {
        type: 'ul',
        items: [
          'ChatGPT·Claude에서 내보낸 대화 파일은 서버로 보내지 않고 브라우저 안에서만 읽어요.',
          '내 과목 키워드가 들어 있는 공부 대화만 고르고, 이메일·전화번호·주민등록번호·카드번호·API 키·IP 주소와 내가 입력한 이름·단어를 가린 뒤 미리 보여 드려요.',
          '원본 파일과 가리기 전의 글은 브라우저 메모리에만 있고, 화면을 나가거나 새로고침하면 바로 사라져요. 브라우저 저장소에도 남기지 않아요.',
          '내가 최종으로 고른 대화만, 가린 상태로 서버에 보내요(2번의 선택 항목). 이미지와 첨부 파일 내용은 보내지 않고 "[이미지]" 표시만 남겨요.',
          '서버도 개인정보 형식이 남아 있는지 한 번 더 검사하고, 남아 있으면 그 대화는 저장하지 않고 알려 드려요.',
        ],
      },
    ],
  },
  {
    id: 'retention',
    title: '4. 개인정보의 처리 및 보유 기간',
    blocks: [
      {
        type: 'ul',
        items: [
          '회원 정보와 학습 기록: 탈퇴할 때까지. 탈퇴하면 바로 삭제해요.',
          '가져온 AI 대화와 그로부터 계산한 분석 결과: AI 대화 분석 동의를 철회하거나 탈퇴할 때까지. 대화 한 건을 지우면 그 대화로 계산한 근거도 함께 지우고 점수를 다시 계산해요.',
          'AI가 만든 요약·퀴즈·설명: 바탕이 된 자료나 대화를 지울 때 함께 지워요.',
          `올린 학습 자료 원본: 요약·퀴즈를 만드는 데 다시 쓰므로 그 자료나 자료가 속한 과목을 지울 때까지 보관하고, 지우면 원본 파일도 함께 지워요. 장기 보관 기간은 정하고 있어요 ${PENDING}`,
          '로그인 유지용 토큰: 14일. 로그아웃하면 바로 폐기해요.',
          '로그인 시도 기록(접속 IP): 반복 실패를 막는 데만 쓰고, 서버 메모리에 15분 동안만 두며 따로 저장하지 않아요.',
        ],
      },
      {
        type: 'p',
        text: '다른 법령에서 보관하라고 정한 정보가 생기면 그 기간과 근거를 여기에 적어요.',
      },
    ],
  },
  {
    id: 'destruction',
    title: '5. 개인정보의 파기 절차 및 방법',
    blocks: [
      {
        type: 'p',
        text: '보유 기간이 끝났거나 처리 목적을 이룬 개인정보는 지체 없이 파기해요. 데이터베이스의 정보는 다시 살릴 수 없는 방법으로 삭제하고, 저장된 파일은 파일째 지워요. 개인정보를 종이로 출력해 보관하지 않아요.',
      },
    ],
  },
  {
    id: 'third-party',
    title: '6. 개인정보의 제3자 제공과 추가적 이용',
    blocks: [
      {
        type: 'p',
        text: '이용자의 개인정보를 다른 사람이나 회사에 제공하지 않아요. 법령에 따라 요구되는 경우에만 예외로 해요. 처음 알린 목적과 다른 목적으로 동의 없이 추가로 이용하거나 제공하지도 않아요.',
      },
    ],
  },
  {
    id: 'outsourcing',
    title: '7. 개인정보 처리업무의 위탁',
    blocks: [
      {
        type: 'table',
        head: ['수탁자', '맡기는 업무'],
        rows: [
          [
            `생성형 AI API 제공사 ${PENDING}`,
            '개념 분류, 판단 근거 설명, 요약·퀴즈 생성',
          ],
          [`서버·데이터베이스 호스팅 ${PENDING}`, '서비스 운영과 데이터 보관'],
        ],
      },
      {
        type: 'ul',
        items: [
          '이용자가 보낸 AI 대화와 학습 자료의 글은 외부 생성형 AI 서비스(API)를 거쳐 처리돼요.',
          `생성형 AI 제공사는 받은 데이터를 자기 모델의 학습이나 성능 개선에 쓰지 않는 조건으로 이용해요. 결과를 만든 뒤 데이터를 언제 지우는지는 제공사가 정해지면 적어요 ${PENDING}`,
          '수탁자가 개인정보를 안전하게 처리하도록 위탁 계약(이용약관 포함)에 필요한 사항을 정하고, 수탁자가 바뀌면 이 처리방침에 알려 드려요.',
        ],
      },
    ],
  },
  {
    id: 'overseas',
    title: '8. 개인정보의 국외 이전',
    blocks: [
      {
        type: 'p',
        text: '7번의 수탁자 서버가 해외에 있으면 아래와 같이 개인정보가 국외로 이전돼요.',
      },
      {
        type: 'table',
        head: ['항목', '내용'],
        rows: [
          ['이전받는 자 (연락처)', PENDING],
          ['이전되는 국가', PENDING],
          [
            '이전되는 항목',
            '개인정보를 가린 AI 대화, 학습 자료에서 뽑은 글 (생성형 AI API), 2번의 모든 항목 (호스팅이 해외인 경우)',
          ],
          [
            '이전 시기와 방법',
            '분석·요약·퀴즈를 만들 때마다 인터넷으로 암호화(TLS)해 전송',
          ],
          ['이용 목적', '7번의 맡기는 업무'],
          [
            '보유·이용 기간',
            `생성형 AI API: 요청을 처리하는 동안(제공사 보관 정책 ${PENDING}), 호스팅: 4번의 보유 기간과 같음`,
          ],
          [
            '법적 근거',
            '「개인정보 보호법」 제28조의8제1항제3호(처리위탁·보관)',
          ],
          [
            '거부 방법과 효과',
            'AI 대화 분석에 동의하지 않거나 학습 자료를 올리지 않으면 생성형 AI API로 이전되지 않아요. 이 경우 해당 분석·요약·퀴즈 기능은 쓸 수 없어요.',
          ],
        ],
      },
    ],
  },
  {
    id: 'generative-ai',
    title: '9. 생성형 AI 이용',
    blocks: [
      {
        type: 'ul',
        items: [
          '쓰는 곳(의도한 용도): 대화를 개념별로 분류하기, 판단 근거와 학습 순서의 이유를 설명하기, 학습 자료의 요약·퀴즈 만들기, 학습 분석 화면의 배치 이유 쓰기',
          'AI에 들어가는 정보: 개인정보를 가린 AI 대화와 학습 자료에서 뽑은 글, 그리고 코드가 계산한 숫자(숙련도 등). 원본 대화 파일과 이미지는 넣지 않아요.',
          'AI가 만든 결과: 요약·퀴즈·설명은 학습 기록으로 저장하고, 화면에는 보라색과 "AI" 표시로 코드가 계산한 값과 구분해서 보여 줘요. 보유 기간은 4번과 같아요.',
          '모델 학습: 서비스는 이용자 정보로 AI 모델을 학습시키지 않고, 이용자 데이터를 학습에 쓰지 않는 조건의 API를 써요. 그래서 따로 학습을 거부(Opt-out)하는 절차가 없고, 이미 학습된 데이터를 지우지 못하는 문제도 생기지 않아요.',
          '개인정보 걸러 내기: AI에 보내기 전에 이메일·전화번호·주민등록번호·카드번호·API 키·IP 주소와 이용자가 지정한 이름·단어를 가리고, 서버가 한 번 더 검사해 남아 있으면 저장하지 않아요(3번).',
          '잘못된 답변: AI 설명이 틀렸거나 민감한 정보가 드러나는 등 부적절하면 12번의 보호책임자에게 알려 주세요. 화면 안의 신고 버튼은 준비 중이에요.',
        ],
      },
    ],
  },
  {
    id: 'automated',
    title: '10. 자동화된 결정',
    blocks: [
      {
        type: 'p',
        text: '이용자의 권리나 의무에 중대한 영향을 주는 결정을 AI나 자동화된 시스템만으로 내리지 않아요. 개념별 숙련도는 AI가 아니라 정해진 규칙의 코드로 계산하고, 어떤 대화·퀴즈 기록을 근거로 했는지 화면에 함께 보여 줘요. 학습 분석 화면의 AI 배치는 "기본 배치로 보기"로 끌 수 있어요.',
      },
    ],
  },
  {
    id: 'rights',
    title: '11. 이용자와 법정대리인의 권리와 행사 방법',
    blocks: [
      {
        type: 'ul',
        items: [
          '이용자(만 14세 미만이면 법정대리인)는 언제든 개인정보 열람·정정·삭제·처리 정지를 요구하고 동의를 철회할 수 있어요.',
          '마이페이지에서 내 정보를 확인하고 AI 대화 분석 동의를 철회할 수 있어요. 철회하면 가져온 AI 대화와 그 대화로 계산한 분석 결과가 함께 지워져요. 탈퇴도 마이페이지에서 할 수 있게 준비하고 있어요.',
          '그 밖의 요청은 12번의 보호책임자에게 연락해 주세요. 받은 요청은 지체 없이 처리하고 결과를 알려 드려요.',
        ],
      },
    ],
  },
  {
    id: 'officer',
    title: '12. 개인정보 보호책임자',
    blocks: [
      {
        type: 'p',
        text: `개인정보 처리에 관한 문의, 불만, 피해 구제는 보호책임자에게 알려 주세요. 담당자 이름(또는 담당 부서)·전화번호·이메일 ${PENDING}`,
      },
    ],
  },
  {
    id: 'security',
    title: '13. 개인정보의 안전성 확보 조치',
    blocks: [
      {
        type: 'table',
        head: ['구분', '조치'],
        rows: [
          [
            '관리적 조치',
            '개인정보는 담당 개발자만 다루고, 서비스 운영에 쓰는 비밀값은 코드에 넣지 않고 환경변수로만 관리해요.',
          ],
          [
            '기술적 조치',
            '비밀번호는 원래 글자로 저장하지 않고 BCrypt로 바꿔 저장해요. 로그인 유지용 토큰은 자바스크립트가 읽을 수 없는 쿠키에 두고, 쓸 때마다 새 토큰으로 바꾸며, 이미 쓴 토큰이 다시 오면 그 로그인을 모두 끊어요. 같은 계정·같은 곳에서 로그인을 여러 번 틀리면 잠시 막아요. 모든 데이터는 로그인한 본인 것만 조회하도록 서버에서 확인해요. 서버 기록(로그)에 대화 본문과 비밀번호를 남기지 않아요. 통신은 HTTPS로 암호화해요.',
          ],
          [
            '물리적 조치',
            `서버는 호스팅 업체의 데이터센터에 두고, 그 업체의 출입 통제를 따라요 ${PENDING}`,
          ],
        ],
      },
    ],
  },
  {
    id: 'cookies',
    title: '14. 자동으로 수집하는 장치와 행태정보',
    blocks: [
      {
        type: 'ul',
        items: [
          '쿠키: 로그인 유지용 토큰 한 가지만 써요(14일, 로그인 기능에서만 전송).',
          '브라우저 저장소: 화면 모드(밝게·어둡게) 선택, "로그인한 적이 있는지" 표시, 내가 저장한 가릴 단어만 저장해요. 가릴 단어는 서버로 보내지 않고, 토큰은 저장하지 않아요.',
          '방문 분석·광고용 추적 도구를 쓰지 않고, 다른 회사가 이용자의 행태정보를 수집하도록 허용하지 않아요.',
          '쿠키 거부: Chrome·Edge는 설정의 개인정보 및 보안(쿠키 및 사이트 권한), Safari는 설정의 개인정보 보호에서 막을 수 있어요. 막으면 로그인 상태가 유지되지 않아요.',
        ],
      },
    ],
  },
  {
    id: 'children',
    title: '15. 만 14세 미만 아동의 개인정보',
    blocks: [
      {
        type: 'p',
        text: `대학생을 위한 서비스예요. 만 14세 미만 아동의 가입을 어떻게 다룰지 정하고 있어요 ${PENDING}`,
      },
    ],
  },
  {
    id: 'remedy',
    title: '16. 권익침해 구제 방법',
    blocks: [
      {
        type: 'p',
        text: '개인정보 침해에 대한 상담이나 분쟁 해결이 필요하면 아래 기관에 문의할 수 있어요.',
      },
      {
        type: 'ul',
        items: [
          '개인정보 분쟁조정위원회: (국번없이) 1833-6972 (www.kopico.go.kr)',
          '개인정보침해 신고센터: (국번없이) 118 (privacy.kisa.or.kr)',
          '경찰청: (국번없이) 182 (ecrm.police.go.kr)',
        ],
      },
    ],
  },
  {
    id: 'changes',
    title: '17. 처리방침의 변경',
    blocks: [
      {
        type: 'ul',
        items: [
          '처리 목적이나 항목처럼 이용자의 권리에 중대한 영향을 주는 변경은 바꾸기 전이나 바꾸는 즉시, 이전 내용과 비교할 수 있게 공지사항으로 알려 드려요.',
          '수탁자 목록처럼 영향이 적은 변경은 일정 기간 모아서 알려 드릴 수 있어요.',
          `이 처리방침은 아직 초안이에요. 시행일과 이전 버전 보기 ${PENDING}`,
        ],
      },
    ],
  },
];

function SectionBlock({ block }: { block: Block }) {
  if (block.type === 'p') {
    return <p className="leading-relaxed text-ink-soft">{block.text}</p>;
  }
  if (block.type === 'ul') {
    return (
      <ul className="flex list-disc flex-col gap-1.5 pl-5 leading-relaxed text-ink-soft">
        {block.items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    );
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-150 border-collapse border-t-2 border-heading text-body">
        <thead>
          <tr className="bg-surface-muted text-caption text-ink-soft">
            {block.head.map((cell) => (
              <th
                key={cell}
                scope="col"
                className="border-b border-line px-3.5 py-3 text-left font-bold"
              >
                {cell}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {block.rows.map((row) => (
            <tr key={row.join('|')}>
              {row.map((cell, index) => (
                <td
                  key={block.head[index]}
                  className={`border-b border-line px-3.5 py-3 align-top leading-relaxed ${index === 0 ? 'font-bold text-heading' : 'text-ink-soft'}`}
                >
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function PrivacyPage() {
  return (
    <SubPage
      title="ai-lms 개인정보 처리방침"
      description="어떤 정보를 왜 모으고, 얼마나 보관하고, 어떻게 지울 수 있는지 알려 드려요"
    >
      <p className="max-w-3xl leading-relaxed text-ink-soft">
        ai-lms(이하 "서비스")는 이용자의 자유와 권리를 지키기 위해 「개인정보
        보호법」과 관계 법령을 지키며 개인정보를 적법하게 처리하고 안전하게
        관리해요. 「개인정보 보호법」 제30조에 따라 개인정보를 어떻게 처리하고
        보호하는지 알리고, 관련 고충을 빠르게 처리하기 위해 이 개인정보
        처리방침을 정해 공개해요.
      </p>

      <p className="rounded-xl bg-surface-muted px-4 py-3 text-body text-ink-soft">
        초안이에요. 아직 정하지 않은 부분은 {PENDING}로 표시했고, 정해지는 대로
        고쳐서 공지할게요.
      </p>

      <nav aria-label="목차" className="rounded-2xl border border-line p-5">
        <p className="mb-2 font-bold text-heading">목차</p>
        <ol className="grid gap-x-6 gap-y-1.5 text-body sm:grid-cols-2">
          {SECTIONS.map((section) => (
            <li key={section.id}>
              <a
                href={`#${section.id}`}
                className="text-ink-soft hover:text-primary hover:underline"
              >
                {section.title}
              </a>
            </li>
          ))}
        </ol>
      </nav>

      <div className="flex max-w-3xl flex-col gap-10">
        {SECTIONS.map((section) => (
          <section
            key={section.id}
            id={section.id}
            aria-labelledby={`${section.id}-title`}
            className="flex scroll-mt-6 flex-col gap-3"
          >
            <h2
              id={`${section.id}-title`}
              className="text-xl font-extrabold text-heading"
            >
              {section.title}
            </h2>
            {section.blocks.map((block, index) => (
              // 고정된 문서 내용이라 순서가 바뀌지 않는다
              // eslint-disable-next-line react-x/no-array-index-key
              <SectionBlock key={index} block={block} />
            ))}
          </section>
        ))}
      </div>
    </SubPage>
  );
}

export default PrivacyPage;
