import { useEffect, useRef, useState } from 'react';
import { ErrorText } from '@/components';
import { TURNSTILE_SITE_KEY } from './turnstile';

// Cloudflare Turnstile 위젯. 사이트 키와 켜는 조건은 turnstile.ts

const SCRIPT_SRC =
  'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';

interface TurnstileApi {
  render: (
    container: HTMLElement,
    options: {
      sitekey: string;
      language?: string;
      theme?: 'auto' | 'light' | 'dark';
      callback: (token: string) => void;
      'expired-callback': () => void;
      'error-callback': () => void;
    },
  ) => string;
  reset: (widgetId: string) => void;
  remove: (widgetId: string) => void;
}

declare global {
  interface Window {
    turnstile?: TurnstileApi;
  }
}

let loading: Promise<TurnstileApi> | null = null;

// 스크립트는 처음 한 번만 불러온다 (회원가입 화면을 다시 열어도 다시 받지 않는다)
function loadTurnstile(): Promise<TurnstileApi> {
  if (window.turnstile) return Promise.resolve(window.turnstile);
  if (!loading) {
    loading = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = SCRIPT_SRC;
      script.async = true;
      script.onload = () =>
        window.turnstile
          ? resolve(window.turnstile)
          : reject(new Error('turnstile'));
      script.onerror = () => {
        loading = null;
        reject(new Error('turnstile'));
      };
      document.head.appendChild(script);
    });
  }
  return loading;
}

interface TurnstileWidgetProps {
  // 확인이 끝나면 토큰, 만료·오류면 null
  onToken: (token: string | null) => void;
  // 값이 바뀌면 확인을 새로 한다 (토큰은 한 번만 쓸 수 있어 가입이 실패하면 새로 받아야 한다)
  resetKey: number;
}

export default function TurnstileWidget({
  onToken,
  resetKey,
}: TurnstileWidgetProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | null>(null);
  const onTokenRef = useRef(onToken);
  const [loadFailed, setLoadFailed] = useState(false);

  useEffect(() => {
    onTokenRef.current = onToken;
  }, [onToken]);

  useEffect(() => {
    let cancelled = false;
    loadTurnstile()
      .then((turnstile) => {
        if (cancelled || !containerRef.current) return;
        widgetIdRef.current = turnstile.render(containerRef.current, {
          sitekey: TURNSTILE_SITE_KEY,
          language: 'ko',
          theme: 'auto',
          callback: (token) => onTokenRef.current(token),
          'expired-callback': () => onTokenRef.current(null),
          'error-callback': () => onTokenRef.current(null),
        });
      })
      .catch(() => {
        if (!cancelled) setLoadFailed(true);
      });
    return () => {
      cancelled = true;
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
      }
      widgetIdRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (resetKey === 0 || !widgetIdRef.current || !window.turnstile) return;
    onTokenRef.current(null);
    window.turnstile.reset(widgetIdRef.current);
  }, [resetKey]);

  if (loadFailed) {
    return (
      <ErrorText message="자동 가입 방지 확인을 불러오지 못했어요. 인터넷 연결을 확인하고 새로고침해 주세요." />
    );
  }
  return <div ref={containerRef} />;
}
