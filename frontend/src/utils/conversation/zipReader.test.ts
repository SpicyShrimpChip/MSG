import { deflateSync, strToU8 } from 'fflate';
import { describe, expect, it } from 'vitest';
import { crc32 } from './zipReader';
import { readExportZip } from './archive';

// 테스트 안에서 가짜 zip을 직접 만든다 (실제 내보내기 파일은 레포에 넣지 않는다)
// fflate의 zipSync는 ZIP64 확장 필드·잘린 파일을 못 만들어서 바이트를 직접 조립한다

interface FakeEntry {
  name: string;
  text: string;
  // true면 Claude 내보내기처럼 크기 칸을 0xFFFFFFFF로 두고 ZIP64 확장 필드에 실제 크기를 넣는다
  zip64?: boolean;
  stored?: boolean;
}

const u16 = (value: number) => [value & 0xff, (value >>> 8) & 0xff];
const u32 = (value: number) => [...u16(value & 0xffff), ...u16(value >>> 16)];
const u64 = (value: number) => [...u32(value), 0, 0, 0, 0];

function localPart(entry: FakeEntry) {
  const raw = strToU8(entry.text);
  const data = entry.stored ? raw : deflateSync(raw);
  const name = strToU8(entry.name);
  const extra = entry.zip64
    ? [...u16(1), ...u16(16), ...u64(raw.length), ...u64(data.length)]
    : [];
  const sizeField = (value: number) => (entry.zip64 ? 0xffffffff : value);
  const header = new Uint8Array([
    ...u32(0x04034b50),
    ...u16(entry.zip64 ? 45 : 20),
    ...u16(0),
    ...u16(entry.stored ? 0 : 8),
    ...u16(0),
    ...u16(0x21),
    ...u32(crc32(raw)),
    ...u32(sizeField(data.length)),
    ...u32(sizeField(raw.length)),
    ...u16(name.length),
    ...u16(extra.length),
    ...name,
    ...extra,
  ]);
  return { header, data, raw, name, crc: crc32(raw) };
}

// 목차까지 갖춘 zip. cutAt을 주면 그 바이트에서 파일이 끝난 것처럼 자른다
function buildZip(entries: FakeEntry[], cutAt?: number): Uint8Array {
  const chunks: Uint8Array[] = [];
  const central: number[][] = [];
  let offset = 0;
  for (const entry of entries) {
    const part = localPart(entry);
    central.push([
      ...u32(0x02014b50),
      ...u16(20),
      ...u16(20),
      ...u16(0),
      ...u16(entry.stored ? 0 : 8),
      ...u16(0),
      ...u16(0x21),
      ...u32(part.crc),
      ...u32(part.data.length),
      ...u32(part.raw.length),
      ...u16(part.name.length),
      ...u16(0),
      ...u16(0),
      ...u16(0),
      ...u16(0),
      ...u32(0),
      ...u32(offset),
      ...part.name,
    ]);
    chunks.push(part.header, part.data);
    offset += part.header.length + part.data.length;
  }
  const directory = new Uint8Array(central.flat());
  const end = new Uint8Array([
    ...u32(0x06054b50),
    ...u16(0),
    ...u16(0),
    ...u16(entries.length),
    ...u16(entries.length),
    ...u32(directory.length),
    ...u32(offset),
    ...u16(0),
  ]);
  const whole = new Uint8Array(offset + directory.length + end.length);
  let position = 0;
  for (const chunk of [...chunks, directory, end]) {
    whole.set(chunk, position);
    position += chunk.length;
  }
  return cutAt === undefined ? whole : whole.slice(0, cutAt);
}

// 목차가 시작하는 위치(= 마지막 파일 데이터가 끝나는 곳)
function dataEnd(entries: FakeEntry[]) {
  return entries.reduce((sum, entry) => {
    const part = localPart(entry);
    return sum + part.header.length + part.data.length;
  }, 0);
}

const CONVERSATION = '[{"uuid":"a"}]';

describe('readExportZip (목차로 읽기)', () => {
  it('크기 칸이 채워진 일반 zip을 읽는다', async () => {
    const result = await readExportZip(
      buildZip([
        { name: 'users.json', text: '[]' },
        { name: 'conversations.json', text: CONVERSATION },
      ]),
    );
    expect(result).toEqual({
      ok: true,
      files: [{ name: 'conversations.json', text: CONVERSATION }],
    });
  });

  it('Claude처럼 크기가 ZIP64 확장 필드에 있는 파일도 읽는다', async () => {
    const result = await readExportZip(
      buildZip([
        { name: 'conversations.json', text: CONVERSATION, zip64: true },
      ]),
    );
    expect(result).toEqual({
      ok: true,
      files: [{ name: 'conversations.json', text: CONVERSATION }],
    });
  });

  it('압축하지 않고 저장만 한 파일도 읽는다', async () => {
    const result = await readExportZip(
      buildZip([
        { name: 'conversations.json', text: CONVERSATION, stored: true },
      ]),
    );
    expect(result.ok).toBe(true);
  });

  it('내용이 손상됐으면(CRC 불일치) 열지 못했다고 알려 준다', async () => {
    const zip = buildZip([
      { name: 'conversations.json', text: CONVERSATION, stored: true },
    ]);
    // 저장만 한 데이터의 첫 글자를 바꾼다 (로컬 헤더 30바이트 + 이름 뒤)
    zip[30 + 'conversations.json'.length] ^= 0xff;
    const result = await readExportZip(zip);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.title).toBe('압축 파일을 열지 못했어요');
  });

  it('푼 크기가 상한을 넘으면 열지 않는다', async () => {
    const entries = [{ name: 'conversations.json', text: CONVERSATION }];
    const zip = buildZip(entries);
    // 목차의 원본 크기를 400MB로 고친다 (목차 시작 + 24)
    new DataView(zip.buffer).setUint32(
      dataEnd(entries) + 24,
      400 * 1024 * 1024,
      true,
    );
    const result = await readExportZip(zip);
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.title).toBe('대화 기록이 너무 커요');
  });
});

describe('readExportZip (잘린 zip)', () => {
  const entries: FakeEntry[] = [
    { name: 'ads.json', text: '[]' },
    { name: 'conversations-000.json', text: '[1]' },
    { name: 'conversations-001.json', text: '[2]' },
    { name: 'file-abc.dat', text: 'x'.repeat(2000), stored: true },
  ];

  it('목차 없이 끝만 잘렸으면 앞의 대화 파일을 건지고 잘렸다고 표시한다', async () => {
    const result = await readExportZip(
      buildZip(entries, dataEnd(entries) - 500),
    );
    if (!result.ok) throw new Error(result.message);
    expect(result.truncated).toBe(true);
    expect(result.files.map((file) => file.name)).toEqual([
      'conversations-000.json',
      'conversations-001.json',
    ]);
  });

  it('대화 파일 중간에서 잘리면 그 파일은 버리고 앞 파일만 돌려준다', async () => {
    const cut = dataEnd(entries.slice(0, 2)) + 40; // 세 번째 파일 헤더 안
    const result = await readExportZip(buildZip(entries, cut));
    if (!result.ok) throw new Error(result.message);
    expect(result.truncated).toBe(true);
    expect(result.files.map((file) => file.name)).toEqual([
      'conversations-000.json',
    ]);
  });

  it('잘린 Claude식(ZIP64 확장) zip도 앞에서부터 읽는다', async () => {
    const claude: FakeEntry[] = [
      { name: 'conversations.json', text: CONVERSATION, zip64: true },
      { name: 'other.json', text: 'y'.repeat(2000), zip64: true, stored: true },
    ];
    const result = await readExportZip(buildZip(claude, dataEnd(claude) - 100));
    if (!result.ok) throw new Error(result.message);
    expect(result.truncated).toBe(true);
    expect(result.files).toEqual([
      { name: 'conversations.json', text: CONVERSATION },
    ]);
  });

  it('대화 파일을 찾기 전에 잘리면 잘렸다고 알려 준다', async () => {
    const early: FakeEntry[] = [
      { name: 'ads.json', text: '[]' },
      { name: 'big.dat', text: 'z'.repeat(3000), stored: true },
    ];
    const result = await readExportZip(buildZip(early, dataEnd(early) - 200));
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.title).toBe('압축 파일이 잘려 있어요');
  });

  it('대화 파일 하나뿐인 zip이 그 안에서 잘리면 잘렸다고 알려 준다', async () => {
    const only: FakeEntry[] = [
      { name: 'conversations.json', text: 'a'.repeat(3000), stored: true },
    ];
    const result = await readExportZip(buildZip(only, dataEnd(only) - 100));
    expect(result.ok).toBe(false);
    if (!result.ok) expect(result.title).toBe('압축 파일이 잘려 있어요');
  });

  it('잘렸어도 대화 파일이 없는 Claude zip이면 알맞은 안내를 준다', async () => {
    const result = await readExportZip(
      buildZip(
        [{ name: 'users.json', text: '[]' }],
        dataEnd([{ name: 'users.json', text: '[]' }]),
      ),
    );
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.title).toBe('대화 기록이 들어 있는 zip이 아니에요');
    }
  });
});
