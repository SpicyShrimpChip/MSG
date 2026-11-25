// Cloudflare Turnstile(자동 가입 방지). 사이트 키(VITE_TURNSTILE_SITE_KEY)가 없으면 쓰지 않는다
// 서버 비밀 키(TURNSTILE_SECRET_KEY)와 같이 켠다. 개인정보 처리방침 7·8·14번에 적어 둔 대로 회원가입 화면에서만 불러온다
export const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY ?? '';
