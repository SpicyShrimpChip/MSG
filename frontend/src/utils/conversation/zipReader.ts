import { inflateSync, strFromU8 } from 'fflate';

// zip을 통째로 메모리에 올리지 않고 Blob.slice로 필요한 부분만 읽는 작은 리더.
// ChatGPT 내보내기는 첨부 파일 때문에 3GB를 넘을 수 있어서 fflate의 unzip(전체를 메모리에)을 쓰지 않는다.
// 압축 풀기(deflate)만 fflate에 맡긴다. 서버로는 아무것도 보내지 않는다

const LOCAL_SIGNATURE = 0x04034b50;
const CENTRAL_SIGNATURE = 0x02014b50;
const EOCD_SIGNATURE = 0x06054b50;
const ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
const ZIP64_EOCD_SIGNATURE = 0x06064b50;

const LOCAL_HEADER_SIZE = 30;
const CENTRAL_HEADER_SIZE = 46;
const EOCD_SIZE = 22;
// 끝의 주석까지 포함해 EOCD를 찾는 범위 (주석은 최대 65535바이트)
const EOCD_SEARCH_BYTES = EOCD_SIZE + 0xffff;
const MAX_32 = 0xffffffff;
const MAX_16 = 0xffff;

const STORED = 0;
const DEFLATED = 8;
const HAS_DATA_DESCRIPTOR = 0x08;

export interface ZipEntry {
  name: string;
  method: number;
  compressedSize: number;
  uncompressedSize: number;
  crc: number;
  localOffset: number;
}

// 목차나 헤더가 깨져서 읽을 수 없을 때
export class ZipFormatError extends Error {}

// 압축 데이터가 깨졌을 때 (풀리지 않거나 크기·CRC가 다름)
export class ZipDataError extends Error {}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    table[n] = c >>> 0;
  }
  return table;
})();

export function crc32(data: Uint8Array): number {
  let crc = 0xffffffff;
  for (let i = 0; i < data.length; i++) {
    crc = CRC_TABLE[(crc ^ data[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

async function readBytes(
  blob: Blob,
  start: number,
  end: number,
): Promise<Uint8Array> {
  return new Uint8Array(await blob.slice(start, end).arrayBuffer());
}

const viewOf = (bytes: Uint8Array) =>
  new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);

// 8바이트 숫자. 2^53(약 9PB)을 넘는 값은 어차피 읽을 수 없는 파일이다
function readUint64(view: DataView, offset: number): number {
  const value = view.getBigUint64(offset, true);
  if (value > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new ZipFormatError('zip 값이 너무 커요');
  }
  return Number(value);
}

// ZIP64 확장 필드(id 0x0001)에서 32비트 칸에 못 담은 큰 값을 꺼낸다.
// 값은 헤더 칸이 0xFFFFFFFF인 것만, 정해진 순서(원본 크기 → 압축 크기 → 위치)대로 들어 있다
function readZip64Extra(
  extra: Uint8Array,
  wanted: { uncompressed: boolean; compressed: boolean; offset: boolean },
): { uncompressed?: number; compressed?: number; offset?: number } {
  const view = viewOf(extra);
  let position = 0;
  while (position + 4 <= extra.length) {
    const id = view.getUint16(position, true);
    const size = view.getUint16(position + 2, true);
    const body = position + 4;
    if (id === 0x0001) {
      const result: {
        uncompressed?: number;
        compressed?: number;
        offset?: number;
      } = {};
      let cursor = body;
      const end = Math.min(body + size, extra.length);
      const take = () => {
        if (cursor + 8 > end)
          throw new ZipFormatError('ZIP64 확장 필드가 짧아요');
        const value = readUint64(view, cursor);
        cursor += 8;
        return value;
      };
      if (wanted.uncompressed) result.uncompressed = take();
      if (wanted.compressed) result.compressed = take();
      if (wanted.offset) result.offset = take();
      return result;
    }
    position = body + size;
  }
  return {};
}

// 파일 끝의 EOCD(목차 위치 정보)를 찾는다. 없으면 null (잘린 zip이거나 zip이 아님)
async function readEndOfCentralDirectory(blob: Blob): Promise<{
  entryCount: number;
  directorySize: number;
  directoryOffset: number;
} | null> {
  if (blob.size < EOCD_SIZE) return null;
  const tailStart = Math.max(0, blob.size - EOCD_SEARCH_BYTES);
  const tail = await readBytes(blob, tailStart, blob.size);
  const view = viewOf(tail);

  let position = tail.length - EOCD_SIZE;
  while (position >= 0 && view.getUint32(position, true) !== EOCD_SIGNATURE) {
    position -= 1;
  }
  if (position < 0) return null;

  let entryCount = view.getUint16(position + 10, true);
  let directorySize = view.getUint32(position + 12, true);
  let directoryOffset = view.getUint32(position + 16, true);

  if (
    entryCount === MAX_16 ||
    directorySize === MAX_32 ||
    directoryOffset === MAX_32
  ) {
    // ZIP64: EOCD 바로 앞 20바이트가 ZIP64 EOCD 위치 정보(locator)
    const locatorStart = tailStart + position - 20;
    if (locatorStart < 0) throw new ZipFormatError('ZIP64 정보가 없어요');
    const locator = await readBytes(blob, locatorStart, locatorStart + 20);
    const locatorView = viewOf(locator);
    if (locatorView.getUint32(0, true) !== ZIP64_LOCATOR_SIGNATURE) {
      throw new ZipFormatError('ZIP64 정보가 없어요');
    }
    const zip64Start = readUint64(locatorView, 8);
    const zip64 = await readBytes(blob, zip64Start, zip64Start + 56);
    const zip64View = viewOf(zip64);
    if (
      zip64.length < 56 ||
      zip64View.getUint32(0, true) !== ZIP64_EOCD_SIGNATURE
    ) {
      throw new ZipFormatError('ZIP64 목차 정보가 깨졌어요');
    }
    entryCount = readUint64(zip64View, 32);
    directorySize = readUint64(zip64View, 40);
    directoryOffset = readUint64(zip64View, 48);
  }
  return { entryCount, directorySize, directoryOffset };
}

// 중앙 목차를 읽어 파일 목록을 돌려준다. 목차가 없으면 null (잘린 zip)
export async function readCentralDirectory(
  blob: Blob,
): Promise<ZipEntry[] | null> {
  const end = await readEndOfCentralDirectory(blob);
  if (!end) return null;
  if (end.directoryOffset + end.directorySize > blob.size) {
    throw new ZipFormatError('목차 위치가 파일 밖이에요');
  }

  const bytes = await readBytes(
    blob,
    end.directoryOffset,
    end.directoryOffset + end.directorySize,
  );
  const view = viewOf(bytes);
  const entries: ZipEntry[] = [];
  let position = 0;
  while (position + CENTRAL_HEADER_SIZE <= bytes.length) {
    if (view.getUint32(position, true) !== CENTRAL_SIGNATURE) break;
    const method = view.getUint16(position + 10, true);
    const crc = view.getUint32(position + 16, true);
    let compressedSize = view.getUint32(position + 20, true);
    let uncompressedSize = view.getUint32(position + 24, true);
    const nameLength = view.getUint16(position + 28, true);
    const extraLength = view.getUint16(position + 30, true);
    const commentLength = view.getUint16(position + 32, true);
    let localOffset = view.getUint32(position + 42, true);
    const nameStart = position + CENTRAL_HEADER_SIZE;
    const extraStart = nameStart + nameLength;
    const next = extraStart + extraLength + commentLength;
    if (next > bytes.length) throw new ZipFormatError('목차가 짧아요');

    const needsZip64 =
      uncompressedSize === MAX_32 ||
      compressedSize === MAX_32 ||
      localOffset === MAX_32;
    if (needsZip64) {
      const big = readZip64Extra(
        bytes.subarray(extraStart, extraStart + extraLength),
        {
          uncompressed: uncompressedSize === MAX_32,
          compressed: compressedSize === MAX_32,
          offset: localOffset === MAX_32,
        },
      );
      uncompressedSize = big.uncompressed ?? uncompressedSize;
      compressedSize = big.compressed ?? compressedSize;
      localOffset = big.offset ?? localOffset;
    }
    entries.push({
      name: strFromU8(bytes.subarray(nameStart, extraStart), true),
      method,
      compressedSize,
      uncompressedSize,
      crc,
      localOffset,
    });
    position = next;
  }
  return entries;
}

// 로컬 헤더를 읽어 압축 데이터가 시작하는 위치를 구한다
async function readDataStart(blob: Blob, localOffset: number): Promise<number> {
  const header = await readBytes(
    blob,
    localOffset,
    localOffset + LOCAL_HEADER_SIZE,
  );
  if (
    header.length < LOCAL_HEADER_SIZE ||
    viewOf(header).getUint32(0, true) !== LOCAL_SIGNATURE
  ) {
    throw new ZipFormatError('파일 헤더가 깨졌어요');
  }
  const view = viewOf(header);
  return (
    localOffset +
    LOCAL_HEADER_SIZE +
    view.getUint16(26, true) +
    view.getUint16(28, true)
  );
}

// 압축을 풀고 크기·CRC를 확인한다
function decompress(
  compressed: Uint8Array,
  entry: Pick<ZipEntry, 'method' | 'uncompressedSize' | 'crc'>,
): Uint8Array {
  let data: Uint8Array;
  if (entry.method === STORED) {
    data = compressed;
  } else if (entry.method === DEFLATED) {
    try {
      data = inflateSync(compressed);
    } catch {
      throw new ZipDataError('압축을 풀지 못했어요');
    }
  } else {
    throw new ZipDataError('지원하지 않는 압축 방식이에요');
  }
  if (data.length !== entry.uncompressedSize || crc32(data) !== entry.crc) {
    throw new ZipDataError('파일 내용이 손상됐어요');
  }
  return data;
}

// 중앙 목차에서 찾은 파일 하나를 꺼낸다
export async function extractEntry(
  blob: Blob,
  entry: ZipEntry,
): Promise<Uint8Array> {
  const start = await readDataStart(blob, entry.localOffset);
  const end = start + entry.compressedSize;
  if (end > blob.size) throw new ZipDataError('파일이 중간에 잘렸어요');
  return decompress(await readBytes(blob, start, end), entry);
}

export type LocalScanItem =
  // 헤더를 읽었다. 내용은 extract()로 필요할 때만 꺼낸다
  | { kind: 'entry'; entry: ZipEntry; extract: () => Promise<Uint8Array> }
  // 파일이 이 파일 헤더나 데이터 중간에서 끝났다
  | { kind: 'truncated'; partialName?: string }
  // 헤더에 크기가 없어(데이터 뒤에 적는 방식) 건너뛸 수 없다
  | { kind: 'unreadable' };

// 중앙 목차가 없는 zip(중간에 잘린 파일)을 앞에서부터 헤더만 따라가며 읽는다.
// 데이터는 건너뛰므로 3GB 파일도 헤더 수만큼만 읽는다
export async function* scanLocalEntries(
  blob: Blob,
): AsyncGenerator<LocalScanItem> {
  let position = 0;
  while (position < blob.size) {
    const header = await readBytes(
      blob,
      position,
      position + LOCAL_HEADER_SIZE,
    );
    if (header.length < LOCAL_HEADER_SIZE) {
      yield { kind: 'truncated' };
      return;
    }
    const view = viewOf(header);
    // 로컬 헤더가 아니면 여기서 끝난다 (중앙 목차가 시작됐거나 zip이 아님)
    if (view.getUint32(0, true) !== LOCAL_SIGNATURE) return;

    const flag = view.getUint16(6, true);
    const method = view.getUint16(8, true);
    const crc = view.getUint32(14, true);
    let compressedSize = view.getUint32(18, true);
    let uncompressedSize = view.getUint32(22, true);
    const nameLength = view.getUint16(26, true);
    const extraLength = view.getUint16(28, true);
    const variable = await readBytes(
      blob,
      position + LOCAL_HEADER_SIZE,
      position + LOCAL_HEADER_SIZE + nameLength + extraLength,
    );
    if (variable.length < nameLength + extraLength) {
      yield { kind: 'truncated' };
      return;
    }

    // 크기가 데이터 뒤에 적히는 방식이면 데이터 끝을 알 수 없다
    if (flag & HAS_DATA_DESCRIPTOR && compressedSize === 0) {
      yield { kind: 'unreadable' };
      return;
    }
    // 로컬 헤더의 ZIP64 확장 필드에는 원본·압축 크기가 둘 다 들어 있다
    if (compressedSize === MAX_32 || uncompressedSize === MAX_32) {
      const big = readZip64Extra(variable.subarray(nameLength), {
        uncompressed: true,
        compressed: true,
        offset: false,
      });
      uncompressedSize = big.uncompressed ?? uncompressedSize;
      compressedSize = big.compressed ?? compressedSize;
    }

    const dataStart = position + LOCAL_HEADER_SIZE + nameLength + extraLength;
    const dataEnd = dataStart + compressedSize;
    const entry: ZipEntry = {
      name: strFromU8(variable.subarray(0, nameLength), true),
      method,
      compressedSize,
      uncompressedSize,
      crc,
      localOffset: position,
    };
    if (dataEnd > blob.size) {
      // 이 파일 데이터가 끝까지 안 왔다. 헤더는 온전하니 zip은 맞고, 이 파일 내용만 못 꺼낸다
      yield { kind: 'truncated', partialName: entry.name };
      return;
    }
    yield {
      kind: 'entry',
      entry,
      extract: async () =>
        decompress(await readBytes(blob, dataStart, dataEnd), entry),
    };
    position = dataEnd;
  }
}
