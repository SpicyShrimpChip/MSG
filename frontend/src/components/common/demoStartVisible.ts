import type { AuthStatus } from '@/store/useAuthStore';

// "로그인 없이 체험해 보기"를 보일지 정한다
// 이미 체험 중이거나 로그인한 상태면 숨긴다(내 데이터와 예시가 섞이지 않게).
// 세션을 되살리는 중(unknown)에도 숨긴다(눌렀다가 로그인과 체험이 함께 켜지지 않게)
export function demoStartVisible({
  available,
  active,
  status,
}: {
  available: boolean;
  active: boolean;
  status: AuthStatus;
}): boolean {
  return available && !active && status === 'anonymous';
}
