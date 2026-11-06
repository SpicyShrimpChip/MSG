import { Link } from 'react-router-dom';

function Footer() {
  return (
    <footer className="bg-footer print:hidden">
      <div className="mx-auto flex max-w-page flex-wrap justify-between gap-4 px-6 py-8 text-sm text-heading">
        <div className="flex flex-col gap-1.5">
          <span className="font-bold">MSG · My Study Guide</span>
          <span>© 2026 Team SSC</span>
        </div>
        <nav aria-label="하단 메뉴" className="flex gap-5">
          <Link to="/about" className="hover:underline">
            서비스 소개
          </Link>
          <Link to="/privacy" className="font-bold hover:underline">
            개인정보 처리방침
          </Link>
        </nav>
      </div>
    </footer>
  );
}

export default Footer;
