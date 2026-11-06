import { useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Info } from 'lucide-react';
import { AsyncBoundary, Card, Notice, SubPage } from '@/components';
import { useCourses, useMe, useSavedMaskWords } from '@/hooks';
import {
  conversationKey,
  maskConversation,
  type ConversationToSend,
  type MaskOptions,
  type MergedImport,
} from '@/utils';
import CourseQuickForm from './components/import/CourseQuickForm';
import FileStep from './components/import/FileStep';
import ImportSteps from './components/import/ImportSteps';
import MaskStep from './components/import/MaskStep';
import SelectStep, {
  type ConversationChoices,
} from './components/import/SelectStep';
import SendStep from './components/import/SendStep';

// 키워드가 있는 과목이 없으면 파일을 고르기 전에 과목부터 등록하게 한다.
// 파일을 읽은 뒤에 알려 주면 과목을 등록하러 나갔다가 파일을 다시 올려야 했다
function CourseGate({ children }: { children: ReactNode }) {
  const { data: courses } = useCourses();
  if (courses.some((course) => course.keywords.length > 0)) return children;

  return (
    <Card
      as="section"
      aria-labelledby="import-course"
      className="flex flex-col gap-5 p-6 md:p-8"
    >
      <div className="flex flex-col gap-2">
        <h2 id="import-course" className="text-xl font-extrabold text-heading">
          먼저 과목과 키워드를 등록해 주세요
        </h2>
        <p className="text-body text-ink-soft">
          내 과목에 등록한 키워드가 들어 있는 대화만 골라서 가져와요. 공부와
          상관없는 대화는 보내지 않아요. 등록하면 바로 파일 고르기로 넘어가요.
        </p>
      </div>
      <CourseQuickForm courses={courses} />
    </Card>
  );
}

// 읽은 대화는 이 화면의 메모리에만 둔다. 새로고침하거나 화면을 나가면 사라진다 (서버·브라우저 저장소에 남기지 않음)
function ConversationImportPage() {
  const [step, setStep] = useState(0);
  const [imported, setImported] = useState<MergedImport | null>(null);
  const [choices, setChoices] = useState<ConversationChoices | null>(null);
  // 사용자가 고치기 전에는 내 정보의 이름과 이 브라우저에 저장한 단어를 미리 넣어 둔다
  const [editedOptions, setEditedOptions] = useState<MaskOptions | null>(null);
  // 새로 넣은 가릴 단어를 이 브라우저에 저장할지. 기본은 이번 가져오기에서만 쓴다
  const [rememberTerms, setRememberTerms] = useState(false);

  const { data: me } = useMe();
  const myName = me?.name;
  const { words: savedTerms, save: saveTerms } = useSavedMaskWords(me?.id);
  const maskOptions = useMemo<MaskOptions>(
    () => editedOptions ?? { names: myName ? [myName] : [], terms: savedTerms },
    [editedOptions, myName, savedTerms],
  );

  // 보낼 대화 (고른 것만, 과목 포함)
  const selected = useMemo(() => {
    if (!imported || !choices) return [];
    return imported.conversations.flatMap((conversation) => {
      const choice = choices[conversationKey(conversation)];
      return choice?.selected
        ? [{ courseId: choice.courseId, conversation }]
        : [];
    });
  }, [imported, choices]);

  const toSend = useMemo<ConversationToSend[]>(
    () =>
      step === 3
        ? selected.map(({ courseId, conversation }) => ({
            courseId,
            conversation: maskConversation(conversation, maskOptions)
              .conversation,
          }))
        : [],
    [step, selected, maskOptions],
  );

  // 다음 버튼이 화면 아래에 있어서, 단계를 바꾸면 단계 표시가 보이도록 맨 위로 올린다
  const goToStep = (next: number) => {
    setStep(next);
    window.scrollTo({ top: 0 });
  };

  const restart = () => {
    setImported(null);
    setChoices(null);
    goToStep(0);
  };

  return (
    <SubPage
      title="AI 대화 가져오기"
      description="ChatGPT, Claude와 공부한 대화를 가져와 개념별로 분석해요"
    >
      <ImportSteps current={step} />

      {/* 서버는 동의하지 않은 사용자의 대화를 받지 않는다(403). 보내기 전에 미리 알려 준다 */}
      {me && !me.aiAnalysisConsent && (
        <Notice icon={Info}>
          <p>
            AI 대화 분석에 동의해야 대화를 보낼 수 있어요. 파일 읽기와 개인정보
            가리기는 지금도 해 볼 수 있고, 동의는{' '}
            <Link to="/mypage" className="text-primary underline">
              마이페이지
            </Link>
            에서 할 수 있어요.
          </p>
        </Notice>
      )}

      {step === 0 && (
        <AsyncBoundary>
          <CourseGate>
            <FileStep
              imported={imported}
              onLoaded={(next) => {
                setImported(next);
                // 파일을 다시 고르면 이전 선택은 버린다
                setChoices(null);
              }}
              onNext={() => goToStep(1)}
            />
          </CourseGate>
        </AsyncBoundary>
      )}

      {step === 1 && imported && (
        <AsyncBoundary>
          <SelectStep
            conversations={imported.conversations}
            initialChoices={choices}
            onBack={() => goToStep(0)}
            onNext={(next) => {
              setChoices(next);
              goToStep(2);
            }}
          />
        </AsyncBoundary>
      )}

      {step === 2 && (
        <MaskStep
          conversations={selected.map(({ conversation }) => conversation)}
          options={maskOptions}
          onChangeOptions={setEditedOptions}
          savedTerms={me ? savedTerms : null}
          remember={rememberTerms}
          onChangeRemember={setRememberTerms}
          onBack={() => goToStep(1)}
          onNext={() => {
            // 3단계에서 뺀 저장 단어는 이번에만 빼고, 저장 목록에서 지우는 것은 마이페이지에서 한다
            if (rememberTerms)
              saveTerms([...savedTerms, ...(maskOptions.terms ?? [])]);
            goToStep(3);
          }}
        />
      )}

      {step === 3 && (
        <AsyncBoundary>
          <SendStep
            items={toSend}
            onBack={() => goToStep(2)}
            onRestart={restart}
          />
        </AsyncBoundary>
      )}
    </SubPage>
  );
}

export default ConversationImportPage;
