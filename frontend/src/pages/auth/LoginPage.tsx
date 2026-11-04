import { useState, type FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ME_QUERY_KEY, login } from '@/api';
import { Button, ErrorText, FormRow, TextInput } from '@/components';
import AuthCard from './components/AuthCard';
import MockAuthNotice from './components/MockAuthNotice';

interface LoginLocationState {
  // 로그인 가드가 넘겨 준 원래 주소
  from?: string;
  // 회원가입 직후 넘어온 경우 가입한 이메일
  signedUpEmail?: string;
}

function LoginPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const state = (useLocation().state ?? {}) as LoginLocationState;
  const [email, setEmail] = useState(state.signedUpEmail ?? '');
  const [password, setPassword] = useState('');

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: ({ user }) => {
      // 이전 사용자의 화면 데이터가 남지 않도록 비우고, 받은 내 정보를 넣어 둔다
      queryClient.clear();
      queryClient.setQueryData(ME_QUERY_KEY, user);
      navigate(state.from ?? '/', { replace: true });
    },
  });

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (mutation.isPending) return;
    mutation.mutate({ email: email.trim(), password });
  };

  return (
    <AuthCard title="로그인" description="계정으로 계속해요">
      <form onSubmit={handleSubmit} className="flex w-full flex-col gap-5">
        <MockAuthNotice from={state.from} />
        {state.signedUpEmail && (
          <p role="status" className="text-body text-ink-soft">
            가입이 완료됐어요. 로그인해 주세요.
          </p>
        )}
        <FormRow label="이메일" htmlFor="login-email">
          <TextInput
            id="login-email"
            type="email"
            autoComplete="email"
            required
            placeholder="name@example.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
        </FormRow>
        <FormRow label="비밀번호" htmlFor="login-password">
          <TextInput
            id="login-password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </FormRow>
        <ErrorText error={mutation.error} />
        <Button
          type="submit"
          size="lg"
          className="mt-2"
          disabled={mutation.isPending}
        >
          로그인
        </Button>
        <p className="text-body text-ink-soft">
          아직 계정이 없나요?{' '}
          <Link
            to="/signup"
            // 가입하고 로그인하면 원래 가려던 화면으로 이어지게 넘겨준다
            state={{ from: state.from }}
            className="font-medium text-primary underline-offset-4 hover:underline"
          >
            회원가입
          </Link>
        </p>
      </form>
    </AuthCard>
  );
}

export default LoginPage;
