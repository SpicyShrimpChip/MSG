import { Link } from 'react-router-dom';
import {
  AI_CONSENT_NOTICE,
  AI_QUIZ_CONSENT_NOTICE,
  NoticeList,
  REQUIRED_NOTICE,
} from './ConsentNotice';

// 회원가입의 이용 안내와 선택 동의.
// 「개인정보 처리 통합 안내서」(2025.7.) Ⅳ 동의를 받는 방법을 따른다:
// - 서비스에 꼭 필요한 정보는 동의가 아니라 계약 이행(법 §15①4)이 근거라서, 필수 동의 대신 "고지 → 확인"으로 받는다
// - 선택 동의(AI 대화 분석, AI 퀴즈 처리)는 각각 따로 두고, 목적·항목·보유 기간·거부할 권리와 불이익을 함께 알리며, 처음에는 체크하지 않은 상태
// 서버 API는 그대로다: 확인 체크 하나가 agreedTerms·agreedPrivacy를 함께 true로 보낸다 (docs/frontend-requests.md)

interface SignupAgreementsProps {
  acknowledged: boolean;
  onAcknowledgedChange: (checked: boolean) => void;
  aiConsent: boolean;
  onAiConsentChange: (checked: boolean) => void;
  aiQuizConsent: boolean;
  onAiQuizConsentChange: (checked: boolean) => void;
}

function SignupAgreements({
  acknowledged,
  onAcknowledgedChange,
  aiConsent,
  onAiConsentChange,
  aiQuizConsent,
  onAiQuizConsentChange,
}: SignupAgreementsProps) {
  return (
    <div className="flex flex-col gap-5">
      <section
        aria-labelledby="signup-notice-title"
        className="flex flex-col gap-3 rounded-2xl border border-line p-6"
      >
        <h2 id="signup-notice-title" className="font-bold text-heading">
          개인정보 수집·이용 안내
        </h2>
        <NoticeList items={REQUIRED_NOTICE} />
        <p className="text-caption text-ink-muted">
          자세한 내용은{' '}
          <Link to="/privacy" className="font-bold text-primary underline">
            개인정보 처리방침
          </Link>
          에서 볼 수 있어요. 서비스 이용약관 본문은 준비 중이에요.
        </p>
        <label className="flex items-start gap-3 font-medium text-heading">
          <input
            type="checkbox"
            required
            checked={acknowledged}
            onChange={(e) => onAcknowledgedChange(e.target.checked)}
            className="mt-1 size-4 accent-primary"
          />
          <span>
            서비스 이용약관과 위 안내를 확인했어요{' '}
            <span className="text-primary">(필수)</span>
          </span>
        </label>
      </section>

      <section
        aria-labelledby="signup-ai-title"
        className="flex flex-col gap-3 rounded-2xl border border-line p-6"
      >
        <h2 id="signup-ai-title" className="font-bold text-heading">
          AI 대화 분석 동의{' '}
          <span className="font-medium text-ink-muted">(선택)</span>
        </h2>
        <NoticeList items={AI_CONSENT_NOTICE} />
        <p className="text-body leading-relaxed text-ink-soft">
          동의하지 않아도 돼요. 동의하지 않으면 AI 대화 가져오기만 쓸 수 없고,
          학습 자료로 공부하는 기능은 그대로 쓸 수 있어요. 동의한 뒤에도
          마이페이지에서 언제든 철회할 수 있어요.
        </p>
        <label className="flex items-start gap-3 font-medium text-heading">
          <input
            type="checkbox"
            checked={aiConsent}
            onChange={(e) => onAiConsentChange(e.target.checked)}
            className="mt-1 size-4 accent-primary"
          />
          <span>AI 대화 분석에 동의해요</span>
        </label>
      </section>

      <section
        aria-labelledby="signup-ai-quiz-title"
        className="flex flex-col gap-3 rounded-2xl border border-line p-6"
      >
        <h2 id="signup-ai-quiz-title" className="font-bold text-heading">
          AI 퀴즈 처리 동의{' '}
          <span className="font-medium text-ink-muted">(선택)</span>
        </h2>
        <NoticeList items={AI_QUIZ_CONSENT_NOTICE} />
        <p className="text-body leading-relaxed text-ink-soft">
          동의하지 않아도 돼요. 동의하지 않으면 AI로 퀴즈를 만들거나
          주관식·서술형 답을 AI로 채점할 수 없고, 객관식 채점과 다른 기능은
          그대로 쓸 수 있어요. AI 대화 분석 동의와는 따로이고, 마이페이지에서
          언제든 바꿀 수 있어요.
        </p>
        <label className="flex items-start gap-3 font-medium text-heading">
          <input
            type="checkbox"
            checked={aiQuizConsent}
            onChange={(e) => onAiQuizConsentChange(e.target.checked)}
            className="mt-1 size-4 accent-primary"
          />
          <span>AI 퀴즈 처리에 동의해요</span>
        </label>
      </section>
    </div>
  );
}

export default SignupAgreements;
