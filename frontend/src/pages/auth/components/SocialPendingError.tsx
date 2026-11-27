import { Link } from 'react-router-dom';
import { ApiError } from '@/api';
import { ErrorText } from '@/components';

// 가입 마무리·계정 연결 화면에서 카카오 정보(가입 마무리 쿠키, 10분)를 읽지 못했을 때
function SocialPendingError({ error }: { error: Error }) {
  const expired = error instanceof ApiError && error.status === 401;
  return (
    <div className="flex flex-col items-center gap-4 text-center">
      {/* 만료가 아니면 error.message, 만료면 message를 보여 준다 */}
      <ErrorText
        error={expired ? null : error}
        message="가입 정보가 만료됐어요. 카카오 로그인을 다시 해 주세요."
      />
      <Link
        to="/login"
        replace
        className="font-medium text-primary underline-offset-4 hover:underline"
      >
        로그인 화면으로 가기
      </Link>
    </div>
  );
}

export default SocialPendingError;
