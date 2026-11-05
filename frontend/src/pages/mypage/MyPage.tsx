import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { Lock, UserRound } from 'lucide-react';
import {
  ME_QUERY_KEY,
  deleteAccount,
  getMe,
  updateAiConsent,
  updateAiQuizConsent,
  updateLearningProfile,
} from '@/api';
import {
  AsyncBoundary,
  Button,
  Card,
  ConfirmDialog,
  EmptyState,
  FormRow,
  LearningProfileFields,
  PasswordInput,
  SubPage,
  ErrorText,
  type LearningProfileValue,
} from '@/components';
import { LEARNING_PURPOSES, jobFieldLabel } from '@/constants';
import { useSavedMaskWords } from '@/hooks';
import { useAuthStore } from '@/store';
import type { User } from '@/types';
import {
  AI_CONSENT_NOTICE,
  AI_QUIZ_CONSENT_NOTICE,
  NoticeList,
} from '@/pages/auth/components/ConsentNotice';
import WordList from '@/pages/analysis/components/import/WordList';

// 내 정보: 흰 카드 안에 항목 이름과 값을 한 줄씩
function AccountInfo({ me }: { me: User }) {
  const rows = [
    { label: '이메일', value: me.email },
    { label: '이름', value: me.name },
  ];
  return (
    <dl className="flex flex-col divide-y divide-line rounded-2xl border border-line px-6">
      {rows.map((row) => (
        <div
          key={row.label}
          className="flex flex-wrap justify-between gap-x-6 gap-y-1 py-4"
        >
          <dt className="text-body text-ink-soft">{row.label}</dt>
          <dd className="text-body font-bold text-heading">{row.value}</dd>
        </div>
      ))}
    </dl>
  );
}

// AI 퀴즈 처리 동의: 대화 분석 동의와 따로. 철회해도 만든 퀴즈·결과는 남고 이후 AI 생성·채점만 멈춘다
function AiQuizConsentSection({ me }: { me: User }) {
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const mutation = useMutation({
    mutationFn: updateAiQuizConsent,
    onSuccess: (user) => queryClient.setQueryData(ME_QUERY_KEY, user),
  });
  return (
    <Card
      as="section"
      aria-labelledby="mypage-ai-quiz-consent"
      className="flex flex-col gap-4 p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2
          id="mypage-ai-quiz-consent"
          className="text-lg font-extrabold text-heading"
        >
          AI 퀴즈 처리 동의{' '}
          <span className="text-body font-medium text-ink-muted">(선택)</span>
        </h2>
        <span className="text-body font-bold text-heading">
          {me.aiQuizConsent ? '동의함' : '동의하지 않음'}
        </span>
      </div>
      <NoticeList items={AI_QUIZ_CONSENT_NOTICE} />
      <p className="text-body leading-relaxed text-ink-soft">
        {me.aiQuizConsent
          ? '철회하면 AI로 퀴즈를 만들거나 주관식·서술형을 AI로 채점하지 않아요. 이미 만든 퀴즈와 채점 결과는 남아요.'
          : '동의하지 않으면 AI 퀴즈 만들기와 주관식·서술형 AI 채점을 쓸 수 없어요. 객관식 채점은 그대로 돼요.'}
      </p>
      <ErrorText error={mutation.error} />
      <div>
        {me.aiQuizConsent ? (
          <Button
            variant="secondary"
            disabled={mutation.isPending}
            onClick={() => setConfirming(true)}
          >
            동의 철회하기
          </Button>
        ) : (
          <Button
            disabled={mutation.isPending}
            onClick={() => mutation.mutate(true)}
          >
            AI 퀴즈 처리에 동의하기
          </Button>
        )}
      </div>
      <ConfirmDialog
        open={confirming}
        title="AI 퀴즈 처리 동의를 철회할까요?"
        description="이후 AI 퀴즈 만들기와 주관식·서술형 AI 채점이 멈춰요. 이미 만든 퀴즈와 결과는 남아요."
        confirmLabel="철회하기"
        onConfirm={() => {
          setConfirming(false);
          mutation.mutate(false);
        }}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}

// 학습 목적·관심 직무: 다음에 만드는 퀴즈의 기본값이 된다 (이미 만든 퀴즈는 그대로)
function LearningProfileSection({ me }: { me: User }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState<LearningProfileValue>({
    learningPurpose: me.learningPurpose,
    jobField: me.jobField,
    jobFieldOther: me.jobFieldOther ?? '',
  });
  const mutation = useMutation({
    mutationFn: updateLearningProfile,
    onSuccess: (user) => {
      queryClient.setQueryData(ME_QUERY_KEY, user);
      setEditing(false);
    },
  });
  const purposeLabel = LEARNING_PURPOSES.find(
    (item) => item.value === me.learningPurpose,
  )?.label;

  return (
    <Card
      as="section"
      aria-labelledby="mypage-learning"
      className="flex flex-col gap-4 p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2
          id="mypage-learning"
          className="text-lg font-extrabold text-heading"
        >
          학습 목적·관심 직무
        </h2>
        {!editing && (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            바꾸기
          </Button>
        )}
      </div>
      {editing ? (
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate({
              learningPurpose: value.learningPurpose,
              jobField: value.jobField,
              jobFieldOther:
                value.jobField === 'other' ? value.jobFieldOther.trim() : null,
            });
          }}
        >
          <LearningProfileFields
            idPrefix="mypage"
            value={value}
            onChange={setValue}
          />
          <ErrorText error={mutation.error} />
          <div className="flex gap-2">
            <Button type="submit" disabled={mutation.isPending}>
              저장하기
            </Button>
            <Button variant="secondary" onClick={() => setEditing(false)}>
              취소
            </Button>
          </div>
        </form>
      ) : (
        <dl className="grid gap-1 text-body sm:grid-cols-[auto_1fr] sm:gap-x-6">
          <dt className="text-ink-soft">학습 목적</dt>
          <dd className="font-bold text-heading">{purposeLabel}</dd>
          <dt className="text-ink-soft">관심 직무</dt>
          <dd className="font-bold text-heading">
            {jobFieldLabel(me.jobField, me.jobFieldOther) ?? '고르지 않음'}
          </dd>
        </dl>
      )}
      <p className="text-caption text-ink-muted">
        다음에 만드는 퀴즈의 기본값이 돼요. 퀴즈를 만들 때마다 다른 목적을
        골라도 되고, 이미 만든 퀴즈는 바뀌지 않아요.
      </p>
    </Card>
  );
}

// AI 대화 분석 동의: 철회하면 가져온 대화와 분석 결과가 지워지므로 확인 창을 거친다
function AiConsentSection({ me }: { me: User }) {
  const queryClient = useQueryClient();
  const [confirming, setConfirming] = useState(false);
  const { clear: clearMaskWords } = useSavedMaskWords(me.id);
  const mutation = useMutation({
    mutationFn: updateAiConsent,
    onSuccess: (user) => {
      queryClient.setQueryData(ME_QUERY_KEY, user);
      // 철회하면 이 브라우저에 저장한 가릴 단어도 함께 지운다
      if (!user.aiAnalysisConsent) clearMaskWords();
      // 철회하면 지워진 대화·분석 결과가 화면에 남지 않도록 내 정보 말고는 다시 받는다
      void queryClient.invalidateQueries({
        predicate: (query) => query.queryKey[0] !== ME_QUERY_KEY[0],
      });
    },
  });

  return (
    <Card
      as="section"
      aria-labelledby="mypage-ai-consent"
      className="flex flex-col gap-4 p-6"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2
          id="mypage-ai-consent"
          className="text-lg font-extrabold text-heading"
        >
          AI 대화 분석 동의{' '}
          <span className="text-body font-medium text-ink-muted">(선택)</span>
        </h2>
        <span className="text-body font-bold text-heading">
          {me.aiAnalysisConsent ? '동의함' : '동의하지 않음'}
        </span>
      </div>
      <NoticeList items={AI_CONSENT_NOTICE} />
      <p className="text-body leading-relaxed text-ink-soft">
        {me.aiAnalysisConsent
          ? '철회하면 가져온 AI 대화와 그 대화로 계산한 분석 결과가 함께 지워져요. 학습 자료와 퀴즈 기록은 그대로 남아요.'
          : '동의하지 않아도 학습 자료로 공부하는 기능은 그대로 쓸 수 있어요. 동의하면 AI 대화 가져오기를 쓸 수 있어요.'}
      </p>

      <ErrorText error={mutation.error} />

      <div>
        {me.aiAnalysisConsent ? (
          <Button
            variant="secondary"
            disabled={mutation.isPending}
            onClick={() => setConfirming(true)}
          >
            동의 철회하기
          </Button>
        ) : (
          <Button
            disabled={mutation.isPending}
            onClick={() => mutation.mutate(true)}
          >
            AI 대화 분석에 동의하기
          </Button>
        )}
      </div>

      <ConfirmDialog
        open={confirming}
        title="AI 대화 분석 동의를 철회할까요?"
        description="가져온 AI 대화와 그 대화로 계산한 분석 결과가 모두 지워져요. 되돌릴 수 없어요."
        confirmLabel="철회하고 지우기"
        onConfirm={() => {
          setConfirming(false);
          mutation.mutate(false);
        }}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}

// 가져오기 3단계 "더 가릴 단어"에 미리 채울 단어. 서버에 보내지 않고 이 브라우저에만 저장한다
function MaskWordsSection({ me }: { me: User }) {
  const { words, save, clear } = useSavedMaskWords(me.id);

  return (
    <Card
      as="section"
      aria-labelledby="mypage-mask-words"
      className="flex flex-col gap-4 p-6"
    >
      <h2
        id="mypage-mask-words"
        className="text-lg font-extrabold text-heading"
      >
        대화 가져올 때 가릴 단어
      </h2>
      <WordList
        id="mypage-mask-terms"
        label="가릴 단어"
        hint="학교, 회사, 주소처럼 자동으로 찾지 못한 단어를 넣어 두면 AI 대화를 가져올 때 미리 채워져요."
        placeholder="예: OO대학교"
        words={words}
        onChange={save}
      />
      <p className="flex items-start gap-2 text-body text-ink-soft">
        <Lock size={18} className="mt-0.5 shrink-0" aria-hidden="true" />이
        브라우저에만 저장돼요. 다른 기기에서는 다시 넣어야 해요. 공용
        컴퓨터에서는 넣지 마세요.
      </p>
      {words.length > 0 && (
        <div>
          <Button variant="secondary" onClick={clear}>
            이 브라우저에서 모두 지우기
          </Button>
        </div>
      )}
    </Card>
  );
}

// 탈퇴: 서버가 내 모든 데이터를 지운 뒤 브라우저의 화면 데이터와 저장한 가릴 단어도 비우고 첫 화면으로 간다
function DeleteAccountSection({ me }: { me: User }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [confirming, setConfirming] = useState(false);
  // 본인 확인: 지금 비밀번호를 다시 받는다 (서버가 확인)
  const [password, setPassword] = useState('');
  const { clear: clearMaskWords } = useSavedMaskWords(me.id);
  const mutation = useMutation({
    mutationFn: () => deleteAccount(password),
    onSuccess: () => {
      clearMaskWords();
      queryClient.clear();
      navigate('/');
    },
  });

  return (
    <Card
      as="section"
      aria-labelledby="mypage-delete"
      className="flex flex-col gap-4 p-6"
    >
      <h2 id="mypage-delete" className="text-lg font-extrabold text-heading">
        탈퇴하고 내 데이터 지우기
      </h2>
      <p className="text-body leading-relaxed text-ink-soft">
        계정과 함께 내 과목, 올린 자료와 원본 파일, 그 자료로 만든
        퀴즈·사전·사후평가와 채점 결과, 가져온 AI 대화, 분석 결과와 피드백, 이
        브라우저에 저장한 가릴 단어가 모두 지워지고 되돌릴 수 없어요. 내가 쓴
        공지사항이 있으면 글은 남고 작성자만 지워져요.
      </p>
      <FormRow label="지금 비밀번호" htmlFor="mypage-delete-password" required>
        <PasswordInput
          id="mypage-delete-password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </FormRow>
      <ErrorText error={mutation.error} />
      <div>
        <Button
          variant="secondary"
          disabled={password === '' || mutation.isPending}
          onClick={() => setConfirming(true)}
        >
          {mutation.isPending ? '지우는 중…' : '탈퇴하기'}
        </Button>
      </div>
      <ConfirmDialog
        open={confirming}
        title="정말 탈퇴할까요?"
        description="계정과 내 모든 학습 데이터가 지워지고 되돌릴 수 없어요."
        confirmLabel="탈퇴하고 모두 지우기"
        onConfirm={() => {
          setConfirming(false);
          mutation.mutate();
        }}
        onCancel={() => setConfirming(false)}
      />
    </Card>
  );
}

function MyAccount() {
  const { data: me } = useSuspenseQuery({
    queryKey: ME_QUERY_KEY,
    queryFn: getMe,
  });
  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <AccountInfo me={me} />
      <LearningProfileSection me={me} />
      <AiConsentSection me={me} />
      <AiQuizConsentSection me={me} />
      <MaskWordsSection me={me} />
      <DeleteAccountSection me={me} />
    </div>
  );
}

function MyPage() {
  const authenticated = useAuthStore(
    (state) => state.status === 'authenticated',
  );

  return (
    <SubPage
      title="마이페이지"
      description="내 정보와 동의 상태, 저장된 데이터를 관리해요"
    >
      {authenticated ? (
        <AsyncBoundary>
          <MyAccount />
        </AsyncBoundary>
      ) : (
        <EmptyState
          icon={<UserRound size={24} aria-hidden="true" />}
          title="로그인하면 볼 수 있어요"
          description="AI 대화 분석 동의를 철회하고 저장된 데이터를 관리할 수 있어요."
          action={{ label: '로그인하기', to: '/login' }}
        />
      )}
    </SubPage>
  );
}

export default MyPage;
