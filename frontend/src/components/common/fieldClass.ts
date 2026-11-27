// 입력칸 공통 모양. input이 아닌 칸(선택 칸, 키워드 칩 칸, 검색 칸)도 이 값을 쓴다
// 포커스는 테두리 색과 안쪽 ring으로 2px 파랑을 만든다. 1px 색 변화만으로는 바탕 대비 3:1에 못 미친다.
// 공통 :focus-visible(2px 띄운 바깥 테두리) 대신 쓰는 이유: 다른 요소에 붙은 칸에서 띄운 테두리가 이웃과 겹친다.
// focus-within은 칸 자신이 포커스돼도 걸린다
export const FIELD_CLASS =
  'rounded-md border border-input-border bg-surface-raised text-body outline-hidden focus-within:border-primary focus-within:ring-1 focus-within:ring-primary';
