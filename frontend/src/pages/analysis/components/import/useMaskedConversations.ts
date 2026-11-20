import { useEffect, useState } from 'react';
import type {
  MaskOptions,
  MaskedConversationWithSpots,
  ParsedConversation,
} from '@/utils';
import { maskConversationsOffThread } from '@/utils/conversation/worker/client';

export interface MaskedItem extends MaskedConversationWithSpots {
  original: ParsedConversation;
}

interface MaskingState {
  // 마지막으로 끝난 가리기 결과. 단어를 바꾸는 동안에는 이전 결과를 그대로 보여 준다
  items: MaskedItem[] | null;
  // 지금 입력(대화·가릴 단어)에 맞는 결과인지. 아니면 다시 가리는 중이다
  current: boolean;
  failed: boolean;
}

// 고른 대화를 Web Worker에서 가린다. enabled가 false면 아무것도 하지 않는다
export function useMaskedConversations(
  conversations: ParsedConversation[],
  options: MaskOptions,
  enabled: boolean,
): MaskingState {
  const [result, setResult] = useState<{
    conversations: ParsedConversation[];
    options: MaskOptions;
    items: MaskedItem[] | null;
    failed: boolean;
  } | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    maskConversationsOffThread(conversations, options).then(
      (masked) => {
        if (cancelled) return;
        setResult({
          conversations,
          options,
          items: masked.map((item, index) => ({
            ...item,
            original: conversations[index],
          })),
          failed: false,
        });
      },
      () => {
        if (cancelled) return;
        setResult((previous) => ({
          conversations,
          options,
          items: previous?.items ?? null,
          failed: true,
        }));
      },
    );
    return () => {
      cancelled = true;
    };
  }, [conversations, options, enabled]);

  const current =
    result !== null &&
    result.conversations === conversations &&
    result.options === options;
  return {
    items: result?.items ?? null,
    current: current && !result.failed,
    failed: current && result.failed,
  };
}
