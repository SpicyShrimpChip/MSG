import type { ReactNode } from 'react';
import {
  CheckScene,
  EvidenceScene,
  ImportScene,
  MaskScene,
  MasteryScene,
  OrderScene,
} from './StoryScenes';

export const STORY_STEPS: {
  title: string;
  // 왼쪽 큰 제목. 줄바꿈 자리는 \n
  headline: string;
  body: string;
  // 창 틀 주소줄에 보여 줄 실제 화면 경로
  path: string;
  Scene: () => ReactNode;
}[] = [
  {
    title: '대화 가져오기',
    headline: '흩어진 AI 대화를\n과목별로 모아요',
    body: 'ChatGPT·Claude 내보내기 파일을 올리면 내 브라우저에서 읽고, 과목 키워드에 맞는 학습 대화만 골라요.',
    path: '/analysis/import',
    Scene: ImportScene,
  },
  {
    title: '개인정보 가리기',
    headline: '보내기 전에\n개인정보부터 가려요',
    body: '이메일·전화번호 같은 정보는 브라우저에서 가린 뒤, 내가 선택한 대화만 서버로 보내요.',
    path: '/analysis/import',
    Scene: MaskScene,
  },
  {
    title: '개념별 강점·약점',
    headline: '개념마다 어디까지\n아는지 보여 줘요',
    body: '대화에서 다시 물어본 것과 퀴즈 결과를 정해진 규칙으로 계산해, 개념마다 강점·보통·보완 필요·근거 부족 중 하나로 보여 줘요.',
    path: '/analysis',
    Scene: MasteryScene,
  },
  {
    title: '판단 근거',
    headline: '왜 그렇게 판단했는지\n함께 보여 줘요',
    body: 'GPT·Claude·Gemini가 같은 기준으로 따로 판단한 결과와, 판단에 쓴 대화·퀴즈를 날짜와 함께 보여 줘요.',
    path: '/analysis',
    Scene: EvidenceScene,
  },
  {
    title: '맞춤 순서',
    headline: '보완할 개념부터\n공부 순서를 다시 짜요',
    body: '보완할 개념부터 핵심 개념을 보고 확인 퀴즈로 이어서 공부해요. 결과가 쌓이면 순서를 다시 계산해요.',
    path: '/curriculum',
    Scene: OrderScene,
  },
  {
    title: '나아졌는지 확인',
    headline: '공부한 뒤\n얼마나 나아졌는지 확인해요',
    body: '사전평가와 같은 개념·같은 난이도로 사후평가를 보고 비교해요. 결과는 다시 숙련도와 공부 순서에 반영돼요.',
    path: '/assessments',
    Scene: CheckScene,
  },
];
