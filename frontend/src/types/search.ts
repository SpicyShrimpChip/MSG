// 통합 검색 결과 (GET /api/search?q=). 백엔드 SearchResponse와 같은 모양.
// 내 과목(이름·키워드)·내 자료(제목·파일 이름)·자료 핵심 개념(이름)·공지사항(제목)에서 찾는다. 자료 본문은 찾지 않는다
export interface SearchResult {
  query: string;
  courses: SearchCourseHit[];
  materials: SearchMaterialHit[];
  concepts: SearchConceptHit[];
  notices: SearchNoticeHit[];
}

export interface SearchCourseHit {
  id: number;
  name: string;
  semester: string;
  // 과목 이름이 검색어와 맞는지 (false면 키워드만 맞음)
  nameMatched: boolean;
  matchedKeywords: string[];
  materialCount: number;
}

export interface SearchMaterialHit {
  id: number;
  title: string;
  fileName: string;
  courseId: number | null;
  courseName: string | null;
  // title: 제목·파일 이름이 맞음 / course: 검색어와 맞는 과목에 올린 자료
  matchedBy: 'title' | 'course';
}

export interface SearchConceptHit {
  materialId: number;
  materialTitle: string;
  conceptId: string;
  name: string;
  aiDescription: string | null;
}

export interface SearchNoticeHit {
  id: number;
  title: string;
  createdAt: string;
}
