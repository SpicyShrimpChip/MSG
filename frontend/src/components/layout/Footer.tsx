import { Link } from 'react-router-dom';
import { ExternalLink } from 'lucide-react';

// 소스 저장소(공개 레포). 체험 링크로 들어온 사람이 코드·PR 기록으로 바로 갈 수 있게 둔다
const REPOSITORY_URL = 'https://github.com/SpicyShrimpChip/lms';

function Footer() {
  return (
    <footer className="bg-footer print:hidden">
      <div className="mx-auto flex max-w-page flex-wrap justify-between gap-4 px-6 py-8 text-sm text-heading">
        <div className="flex flex-col gap-1.5">
          <span className="font-bold">MSG · My Study Guide</span>
          <span>© 2026 Team SSC</span>
        </div>
        <nav
          aria-label="하단 메뉴"
          className="flex flex-wrap items-start gap-5"
        >
          <Link to="/about" className="hover:underline">
            서비스 소개
          </Link>
          <Link to="/privacy" className="font-bold hover:underline">
            개인정보 처리방침
          </Link>
          <a
            href={REPOSITORY_URL}
            target="_blank"
            rel="noopener noreferrer"
            className="inline-flex items-center gap-1 hover:underline"
          >
            GitHub
            <ExternalLink size={13} aria-hidden="true" />
            <span className="sr-only">(새 탭에서 열림)</span>
          </a>
        </nav>
      </div>
    </footer>
  );
}

export default Footer;
