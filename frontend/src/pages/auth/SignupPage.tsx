import { useState, type FormEvent } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useLocation, useNavigate } from 'react-router-dom';
import { signup } from '@/api';
import {
  Button,
  FormRow,
  LearningProfileFields,
  PasswordInput,
  TextInput,
  ErrorText,
  type LearningProfileValue,
} from '@/components';
import { isPasswordValid } from '@/utils';
import AuthCard from './components/AuthCard';
import MockAuthNotice from './components/MockAuthNotice';
import PasswordRules from './components/PasswordRules';
import SignupAgreements from './components/SignupAgreements';

interface SignupLocationState {
  // 가입 뒤 이어서 갈 화면 (메인 시작 안내·로그인 화면이 넘겨 준다)
  from?: string;
}

function SignupPage() {
  const navigate = useNavigate();
  const { from } = (useLocation().state ?? {}) as SignupLocationState;
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  // 이용약관·개인정보 수집·이용 안내 확인(필수)과 AI 대화 분석 동의(선택)
  const [acknowledged, setAcknowledged] = useState(false);
  const [aiConsent, setAiConsent] = useState(false);
  const [aiQuizConsent, setAiQuizConsent] = useState(false);
  // 학습 목적·관심 직무 (선택, 마이페이지에서 바꿀 수 있다)
  const [profile, setProfile] = useState<LearningProfileValue>({
    learningPurpose: 'undecided',
    jobField: null,
    jobFieldOther: '',
  });
  const mismatch = confirm !== '' && confirm !== password;

  const mutation = useMutation({
    mutationFn: signup,
    onSuccess: (user) => {
      // 로그인하면 가입 전에 고른 화면으로 이어진다
      navigate('/login', { state: { signedUpEmail: user.email, from } });
    },
  });

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!isPasswordValid(password) || password !== confirm) return;
    if (mutation.isPending) return;
    // 서버에서도 같은 규칙으로 다시 검사한다 (이메일 중복, 비밀번호 규칙, 필수 동의)
    mutation.mutate({
      email: email.trim(),
      name: name.trim(),
      password,
      passwordConfirm: confirm,
      // 안내 확인 하나로 이용약관·개인정보 안내를 함께 확인한다 (서버 필드는 그대로)
      agreedTerms: acknowledged,
      agreedPrivacy: acknowledged,
      agreedAiAnalysis: aiConsent,
      agreedAiQuiz: aiQuizConsent,
      learningPurpose: profile.learningPurpose,
      jobField: profile.jobField,
      jobFieldOther:
        profile.jobField === 'other' ? profile.jobFieldOther.trim() : null,
    });
  };

  return (
    <AuthCard title="회원가입" wide>
      <form onSubmit={handleSubmit} className="flex flex-col gap-8">
        <MockAuthNotice from={from} />
        <div className="flex flex-col gap-6">
          <FormRow label="이메일" htmlFor="signup-email" required>
            <TextInput
              id="signup-email"
              type="email"
              autoComplete="email"
              required
              placeholder="name@example.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </FormRow>
          <FormRow label="비밀번호" htmlFor="signup-password" required>
            <PasswordInput
              id="signup-password"
              autoComplete="new-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              aria-describedby="signup-password-rules"
            />
            <PasswordRules id="signup-password-rules" password={password} />
          </FormRow>
          <FormRow
            label="비밀번호 확인"
            htmlFor="signup-password-confirm"
            required
            error={
              mismatch ? '비밀번호가 서로 달라요. 다시 확인해 주세요.' : ''
            }
          >
            <PasswordInput
              id="signup-password-confirm"
              autoComplete="new-password"
              required
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              aria-invalid={mismatch ? true : undefined}
              aria-describedby="signup-password-confirm-error"
            />
          </FormRow>
          <FormRow label="이름" htmlFor="signup-name" required>
            <TextInput
              id="signup-name"
              type="text"
              autoComplete="name"
              required
              maxLength={30}
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </FormRow>
        </div>

        <section
          aria-labelledby="signup-learning"
          className="flex flex-col gap-3"
        >
          <h2
            id="signup-learning"
            className="text-lg font-extrabold text-heading"
          >
            학습 목적{' '}
            <span className="text-body font-medium text-ink-muted">(선택)</span>
          </h2>
          <LearningProfileFields
            idPrefix="signup"
            value={profile}
            onChange={setProfile}
          />
        </section>

        <SignupAgreements
          acknowledged={acknowledged}
          onAcknowledgedChange={setAcknowledged}
          aiConsent={aiConsent}
          onAiConsentChange={setAiConsent}
          aiQuizConsent={aiQuizConsent}
          onAiQuizConsentChange={setAiQuizConsent}
        />

        <ErrorText error={mutation.error} />

        <div className="flex">
          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={
              !isPasswordValid(password) ||
              password !== confirm ||
              mutation.isPending
            }
          >
            가입하기
          </Button>
        </div>
      </form>
    </AuthCard>
  );
}

export default SignupPage;
