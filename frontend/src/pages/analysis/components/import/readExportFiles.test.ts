import { describe, expect, it } from 'vitest';
import { readExportFiles } from './readExportFiles';

const claudeJson = JSON.stringify([
  {
    uuid: 'c1',
    name: '정보보호',
    created_at: '2026-10-01T00:00:00Z',
    chat_messages: [
      { sender: 'human', text: '해시가 뭐예요?' },
      { sender: 'assistant', text: '해시는 ...' },
    ],
  },
]);

const file = (name: string, text: string) => new File([text], name);

describe('readExportFiles', () => {
  it('대화 없는 알려진 파일은 오류 없이 건너뛰고 회색 안내만 남긴다', async () => {
    const result = await readExportFiles([
      file('conversations.json', claudeJson),
      file('conversation_asset_file_names.json', '{}'),
      file('user.json', '{}'),
    ]);
    expect(result.conversations).toHaveLength(1);
    expect(result.errors).toEqual([]);
    expect(result.notices).toHaveLength(1);
    expect(result.notices[0].message).toMatch(/건너뛰었어요/);
  });

  it('그 파일만 올렸으면 conversations 파일을 올리라고 안내한다', async () => {
    const result = await readExportFiles([file('user.json', '{}')]);
    expect(result.conversations).toHaveLength(0);
    expect(result.errors).toEqual([]);
    expect(result.notices[0].message).toMatch(/conversations-000/);
  });

  it('정말 모르는 파일은 오류로 알려 준다', async () => {
    const result = await readExportFiles([file('notes.json', '{"a":1}')]);
    expect(result.errors).toHaveLength(1);
    expect(result.notices).toEqual([]);
  });
});
