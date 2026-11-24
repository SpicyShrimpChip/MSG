import type { DeleteAccountInput } from '@/types';

// 비밀번호 없는 계정(카카오로만 가입)이 탈퇴할 때 입력하는 확인 문구. 서버 WithdrawalService.CONFIRM_TEXT와 같다
export const DELETE_CONFIRM_TEXT = '탈퇴';

// 탈퇴 요청 본문. 비밀번호가 있는 계정은 비밀번호, 없는 계정은 확인 문구를 보낸다. 아직 보낼 수 없으면 null
export function deleteAccountRequest(
  hasPassword: boolean,
  password: string,
  confirmText: string,
): DeleteAccountInput | null {
  if (hasPassword) return password === '' ? null : { password };
  return confirmText.trim() === DELETE_CONFIRM_TEXT
    ? { confirmText: DELETE_CONFIRM_TEXT }
    : null;
}
