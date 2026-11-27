import { Link } from 'react-router-dom';
import { Lock } from 'lucide-react';

// 스크롤 소개(ScrollStory) 바로 아래에 붙는 안내 (1·2단계 대화 가져오기·개인정보 가리기의 보충 설명)
function PrivacyNotice() {
  return (
    <div className="flex flex-wrap items-center gap-4 rounded-2xl border border-line px-6 py-5">
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary-soft text-primary">
        <Lock size={20} aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-[1_1_420px]">
        <p className="mb-1 font-semibold text-heading">
          원본 대화 파일은 서버로 보내지 않아요
        </p>
        <p className="text-body leading-relaxed text-ink-soft">
          내 브라우저에서 학습 관련 대화만 고르고, 이메일과 전화번호 같은
          개인정보를 가린 뒤 내가 선택한 대화만 보내요.
        </p>
      </div>
      <Link
        to="/privacy"
        className="text-body font-medium text-primary underline-offset-4 hover:underline"
      >
        개인정보 처리방침
      </Link>
    </div>
  );
}

export default PrivacyNotice;
