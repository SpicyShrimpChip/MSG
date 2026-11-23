import { useState } from 'react';
import {
  useMutation,
  type DefaultError,
  type UseMutationOptions,
  type UseMutationResult,
} from '@tanstack/react-query';
import {
  createSubmitLock,
  sendOnce,
  type SubmitLock,
} from '@/utils/submitLock';

// 서버 데이터를 만들거나 바꾸는 저장 버튼에 쓰는 useMutation (#210).
// mutate를 부르는 순간 잠금을 걸어, 화면이 다시 그려져 버튼이 막히기 전에 들어온 연타·더블클릭은 보내지 않는다.
// 잠금은 요청이 끝나면(성공·실패 모두, onSettled) 풀린다. 버튼의 disabled={isPending} 표시는 그대로 둔다.
// 잠금을 거치지 않는 mutateAsync는 내보내지 않는다.
// 한 화면의 두 버튼(저장·취소 등)이 서로 겹쳐 보내지 않게 하려면 같은 잠금(sharedLock)을 넘긴다

// 상태별 타입(isError면 error가 있음 등)이 그대로 남도록 유니언의 각 타입에서 뺀다
type DistributiveOmit<T, K extends PropertyKey> = T extends unknown
  ? Omit<T, K>
  : never;

export type SingleFlightMutationResult<
  TData = unknown,
  TError = DefaultError,
  TVariables = void,
  TOnMutateResult = unknown,
> = DistributiveOmit<
  UseMutationResult<TData, TError, TVariables, TOnMutateResult>,
  'mutateAsync'
>;

export function useSingleFlightMutation<
  TData = unknown,
  TError = DefaultError,
  TVariables = void,
  TOnMutateResult = unknown,
>(
  options: UseMutationOptions<TData, TError, TVariables, TOnMutateResult>,
  sharedLock?: SubmitLock,
): SingleFlightMutationResult<TData, TError, TVariables, TOnMutateResult> {
  // 화면이 다시 그려져도 같은 잠금을 쓴다
  const [ownLock] = useState(createSubmitLock);
  const lock = sharedLock ?? ownLock;
  const mutation = useMutation({
    ...options,
    // 옵션의 onSettled는 화면이 사라져도 불리므로 잠금이 남지 않는다
    onSettled: (...args) => {
      lock.release();
      return options.onSettled?.(...args);
    },
  });
  const guarded: Partial<typeof mutation> = {
    ...mutation,
    mutate: (...args) => {
      sendOnce(lock, mutation.mutate, ...args);
    },
  };
  delete guarded.mutateAsync;
  return guarded as SingleFlightMutationResult<
    TData,
    TError,
    TVariables,
    TOnMutateResult
  >;
}
