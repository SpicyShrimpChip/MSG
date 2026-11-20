/* tslint:disable */
/* eslint-disable */

/**
 * CRC-32 (zip과 같은 방식)
 */
export function crc32(data: Uint8Array | number[]): number;

/**
 * zip 항목 하나의 압축을 풀고 크기·CRC를 확인한다. 실패하면 이유 코드를 담은 오류를 던진다.
 */
export function inflate_entry(compressed: Uint8Array | number[], method: number, uncompressed_size: number, crc: number): Uint8Array;

/**
 * 글 여러 개를 같은 단어로 가린다. 결과는 `[{ text, matches: [{ kind, original, start, end }] }]` JSON.
 * 가릴 단어 정규식은 한 번만 만든다(대화 하나의 제목·메시지를 한꺼번에 넘긴다).
 */
export function mask_texts(texts: string[], names: string[], terms: string[]): string;

export type InitInput = RequestInfo | URL | Response | BufferSource | WebAssembly.Module;

export interface InitOutput {
    readonly memory: WebAssembly.Memory;
    readonly crc32: (a: number, b: number) => number;
    readonly inflate_entry: (a: number, b: number, c: number, d: number, e: number) => [number, number, number, number];
    readonly mask_texts: (a: number, b: number, c: number, d: number, e: number, f: number) => [number, number];
    readonly __wbindgen_malloc: (a: number, b: number) => number;
    readonly __wbindgen_realloc: (a: number, b: number, c: number, d: number) => number;
    readonly __wbindgen_free: (a: number, b: number, c: number) => void;
    readonly __wbindgen_externrefs: WebAssembly.Table;
    readonly __externref_table_dealloc: (a: number) => void;
    readonly __externref_table_alloc: () => number;
    readonly __externref_drop_slice: (a: number, b: number) => void;
    readonly __wbindgen_start: () => void;
}

export type SyncInitInput = BufferSource | WebAssembly.Module;

/**
 * Instantiates the given `module`, which can either be bytes or
 * a precompiled `WebAssembly.Module`.
 *
 * @param {{ module: SyncInitInput }} module - Passing `SyncInitInput` directly is deprecated.
 *
 * @returns {InitOutput}
 */
export function initSync(module: { module: SyncInitInput } | SyncInitInput): InitOutput;

/**
 * If `module_or_path` is {RequestInfo} or {URL}, makes a request and
 * for everything else, calls `WebAssembly.instantiate` directly.
 *
 * @param {{ module_or_path: InitInput | Promise<InitInput> }} module_or_path - Passing `InitInput` directly is deprecated.
 *
 * @returns {Promise<InitOutput>}
 */
export default function __wbg_init (module_or_path?: { module_or_path: InitInput | Promise<InitInput> } | InitInput | Promise<InitInput>): Promise<InitOutput>;
