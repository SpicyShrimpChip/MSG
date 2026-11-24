import { useState, type SubmitEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import {
  ApiError,
  ME_QUERY_KEY,
  completeSocialSignup,
  getPendingSocialSignup,
} from '@/api';
import {
  Button,
  ErrorText,
  FormRow,
  LearningProfileFields,
  LoadingFallback,
  TextInput,
  type LearningProfileValue,
} from '@/components';
import { useSingleFlightMutation } from '@/hooks';
import type { PendingSocialSignup } from '@/types';
import {
  buildSocialSignupInput,
  initialSocialName,
  safeReturnTo,
} from '@/utils';
import AuthCard from './components/AuthCard';
import { KAKAO_SIGNUP_NOTICE } from './components/ConsentNotice';
import SignupAgreements from './components/SignupAgreements';
import SocialPendingError from './components/SocialPendingError';

// 카카오에서 처음 온 사용자의 가입 마무리 화면 (/signup/social?returnTo=).
// 서버는 아직 아무것도 저장하지 않았고, 가입 마무리 쿠키(10분)로 카카오 이메일·닉네임만 알려 준다.
// 약관 안내를 확인하면 그때 계정을 만들고 바로 로그인한다
function SocialSignupPage() {
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnTo(searchParams.get('returnTo'));
  const pending = useQuery({
    queryKey: ['social-signup-pending'],
    queryFn: getPendingSocialSignup,
    retry: false,
    // 가입 정보는 이 화면에서 한 번만 읽는다 (창을 다시 볼 때 다시 묻지 않는다)
    refetchOnWindowFocus: false,
    gcTime: 0,
  });

  return (
    <AuthCard title="카카오로 가입하기" wide>
      {pending.isPending ? (
        <LoadingFallback />
      ) : pending.isError ? (
        <SocialPendingError error={pending.error} />
      ) : (
        <SocialSignupForm pending={pending.data} returnTo={returnTo} />
      )}
    </AuthCard>
  );
}

interface SocialSignupFormProps {
  pending: PendingSocialSignup;
  returnTo: string;
}

function SocialSignupForm({ pending, returnTo }: SocialSignupFormProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [name, setName] = useState(() => initialSocialName(pending.nickname));
  const [acknowledged, setAcknowledged] = useState(false);
  const [aiConsent, setAiConsent] = useState(false);
  const [aiQuizConsent, setAiQuizConsent] = useState(false);
  const [profile, setProfile] = useState<LearningProfileValue>({
    learningPurpose: 'undecided',
    jobField: null,
    jobFieldOther: '',
  });

  const mutation = useSingleFlightMutation({
    mutationFn: completeSocialSignup,
    onSuccess: ({ user }) => {
      // 일반 로그인과 같이 이전 화면 데이터를 비우고 받은 내 정보를 넣어 둔다
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, user);
      navigate(returnTo, { replace: true });
    },
  });
  // 이미 가입했거나(다른 탭에서 마침) 같은 이메일 계정이 있거나(409), 가입 정보가 10분이 지나 만료됐으면(401)
  // 이 화면에서는 더 할 수 있는 게 없으니 로그인 화면(카카오 로그인 다시 하기)으로 안내한다
  const restartNeeded =
    mutation.error instanceof ApiError &&
    (mutation.error.status === 409 || mutation.error.status === 401);

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (mutation.isPending || !acknowledged || name.trim() === '') return;
    mutation.mutate(
      buildSocialSignupInput({
        name,
        acknowledged,
        aiConsent,
        aiQuizConsent,
        profile,
      }),
    );
  };

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-8">
      <p className="text-body text-ink-soft">
        카카오 계정으로 처음 오셨어요. 아래 안내를 확인하면 가입이 끝나요.
        비밀번호는 만들지 않고, 다음부터 카카오 로그인으로 들어와요.
      </p>
      <div className="flex flex-col gap-6">
        <FormRow label="이메일 (카카오 계정)" htmlFor="social-signup-email">
          <TextInput
            id="social-signup-email"
            type="email"
            value={pending.email}
            readOnly
            aria-readonly="true"
            className="bg-surface-muted"
          />
        </FormRow>
        <FormRow label="이름" htmlFor="social-signup-name" required>
          <TextInput
            id="social-signup-name"
            type="text"
            autoComplete="name"
            required
            maxLength={30}
            value={name}
            onChange={(e) => {
              setName(e.target.value);
              if (mutation.isError) mutation.reset();
            }}
          />
        </FormRow>
      </div>

      <section
        aria-labelledby="social-signup-learning"
        className="flex flex-col gap-3"
      >
        <h2
          id="social-signup-learning"
          className="text-lg font-extrabold text-heading"
        >
          학습 목적{' '}
          <span className="text-body font-medium text-ink-muted">(선택)</span>
        </h2>
        <LearningProfileFields
          idPrefix="social-signup"
          value={profile}
          onChange={setProfile}
        />
      </section>

      <SignupAgreements
        requiredNotice={KAKAO_SIGNUP_NOTICE}
        acknowledged={acknowledged}
        onAcknowledgedChange={setAcknowledged}
        aiConsent={aiConsent}
        onAiConsentChange={setAiConsent}
        aiQuizConsent={aiQuizConsent}
        onAiQuizConsentChange={setAiQuizConsent}
      />

      <ErrorText error={mutation.error} />
      {restartNeeded && (
        <Link
          to="/login"
          replace
          className="-mt-6 font-medium text-primary underline-offset-4 hover:underline"
        >
          로그인 화면으로 가기
        </Link>
      )}

      <Button
        type="submit"
        size="lg"
        className="w-full"
        disabled={!acknowledged || name.trim() === '' || mutation.isPending}
      >
        가입하기
      </Button>
    </form>
  );
}

export default SocialSignupPage;
