import { describe, expect, it } from 'vitest';
import { DELETE_CONFIRM_TEXT, deleteAccountRequest } from './accountDeletion';

describe('deleteAccountRequest', () => {
  it('비밀번호가 있는 계정은 비밀번호를 보낸다', () => {
    expect(deleteAccountRequest(true, 'Passw0rd!', '')).toEqual({
      password: 'Passw0rd!',
    });
  });

  it('비밀번호가 비면 보낼 수 없다', () => {
    expect(deleteAccountRequest(true, '', '탈퇴')).toBeNull();
  });

  it('비밀번호 없는 계정은 탈퇴 문구를 보낸다', () => {
    expect(DELETE_CONFIRM_TEXT).toBe('탈퇴');
    expect(deleteAccountRequest(false, '', ' 탈퇴 ')).toEqual({
      confirmText: '탈퇴',
    });
  });

  it('탈퇴 문구가 정확하지 않으면 보낼 수 없다', () => {
    expect(deleteAccountRequest(false, '', '탈퇴할래요')).toBeNull();
    expect(deleteAccountRequest(false, 'Passw0rd!', '')).toBeNull();
  });
});
