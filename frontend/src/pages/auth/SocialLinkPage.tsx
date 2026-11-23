import { useState, type SubmitEvent } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ME_QUERY_KEY, getPendingSocialSignup, linkSocialAccount } from '@/api';
import {
  Button,
  ErrorText,
  FormRow,
  LoadingFallback,
  PasswordInput,
  TextInput,
} from '@/components';
import { useSingleFlightMutation } from '@/hooks';
import type { PendingSocialSignup } from '@/types';
import { safeReturnTo } from '@/utils';
import AuthCard from './components/AuthCard';
import SocialPendingError from './components/SocialPendingError';

// 카카오 계정 연결 화면 (/auth/social/link?returnTo=).
// 카카오가 확인한 이메일로 이미 비밀번호 계정이 있을 때 온다. 비밀번호 가입은 이메일 주인을 확인하지 않아
// 남이 미리 만든 계정일 수 있으므로, 그 계정의 비밀번호를 확인한 뒤에야 카카오를 연결하고 로그인한다
function SocialLinkPage() {
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnTo(searchParams.get('returnTo'));
  const pending = useQuery({
    queryKey: ['social-link-pending'],
    queryFn: getPendingSocialSignup,
    retry: false,
    refetchOnWindowFocus: false,
    gcTime: 0,
  });

  return (
    <AuthCard title="카카오 계정 연결" description="이미 가입한 계정이 있어요">
      {pending.isPending ? (
        <LoadingFallback />
      ) : pending.isError ? (
        <SocialPendingError error={pending.error} />
      ) : (
        <SocialLinkForm pending={pending.data} returnTo={returnTo} />
      )}
    </AuthCard>
  );
}

interface SocialLinkFormProps {
  pending: PendingSocialSignup;
  returnTo: string;
}

function SocialLinkForm({ pending, returnTo }: SocialLinkFormProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [password, setPassword] = useState('');
  const mutation = useSingleFlightMutation({
    mutationFn: linkSocialAccount,
    onSuccess: ({ user }) => {
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, user);
      navigate(returnTo, { replace: true });
    },
  });

  const handleSubmit = (e: SubmitEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (mutation.isPending || password === '') return;
    mutation.mutate(password);
  };

  return (
    <form onSubmit={handleSubmit} className="flex w-full flex-col gap-5">
      <p className="text-body leading-relaxed text-ink-soft">
        이 이메일로 가입한 계정이 있어요. 계정의 비밀번호를 한 번 확인하면
        카카오를 연결하고, 다음부터는 카카오로 바로 로그인할 수 있어요.
      </p>
      <FormRow label="이메일" htmlFor="social-link-email">
        <TextInput
          id="social-link-email"
          type="email"
          autoComplete="username"
          value={pending.email}
          readOnly
          aria-readonly="true"
          className="bg-surface-muted"
        />
      </FormRow>
      <FormRow label="계정 비밀번호" htmlFor="social-link-password" required>
        <PasswordInput
          id="social-link-password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => {
            setPassword(e.target.value);
            if (mutation.isError) mutation.reset();
          }}
        />
      </FormRow>
      <ErrorText error={mutation.error} />
      <Button
        type="submit"
        size="lg"
        disabled={password === '' || mutation.isPending}
      >
        연결하고 로그인
      </Button>
      <p className="text-body text-ink-soft">
        연결하지 않으려면{' '}
        <Link
          to="/login"
          replace
          className="font-medium text-primary underline-offset-4 hover:underline"
        >
          이메일로 로그인
        </Link>
        해 주세요.
      </p>
    </form>
  );
}

export default SocialLinkPage;
