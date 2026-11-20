import { ApiError } from '@/api/request';

// 같은 요청을 두 번 보내도 한 번만 처리되게 하는 요청 키 (UUID v4 모양).
// crypto.randomUUID()는 HTTPS(또는 localhost)에서만 있어서, 없으면 getRandomValues로 같은 모양을 만든다
export function createRequestKey(): string {
  const cryptoApi = globalThis.crypto;
  if (typeof cryptoApi?.randomUUID === 'function')
    return cryptoApi.randomUUID();
  const bytes = new Uint8Array(16);
  cryptoApi.getRandomValues(bytes);
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // 버전 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // RFC 4122 변형
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'));
  return [
    hex.slice(0, 4).join(''),
    hex.slice(4, 6).join(''),
    hex.slice(6, 8).join(''),
    hex.slice(8, 10).join(''),
    hex.slice(10, 16).join(''),
  ].join('-');
}

// 서버가 답하기 전에 기다리기를 멈춘 실패(시간 초과·연결 끊김)인지.
// 이때는 서버가 요청을 계속 처리하고 있을 수 있어서, 다시 보낼 때 같은 키를 써야 결과가 하나만 생긴다
export function isNoResponseError(error: unknown): boolean {
  return error instanceof ApiError && error.status === undefined;
}
