import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
  type FormEvent,
  type ReactNode,
} from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { Menu, Search, X } from 'lucide-react';
import { AnimatePresence } from 'motion/react';
import * as m from 'motion/react-m';
import { logout } from '@/api';
import { MOTION_DURATION } from '@/constants';
import { useMe } from '@/hooks';
import { useAuthStore } from '@/store';
import Gnb from './Gnb';
import MobileMenu, { MOBILE_MENU_ID } from './MobileMenu';
import ThemeToggle from './ThemeToggle';
import { SEARCH_WIDTH, useCapsuleMode, useCapsuleWidth } from './useCapsule';
import { GlassSurface } from '../common';
import { FIELD_CLASS } from '../common/fieldClass';

// 알약 높이(px). 알약 클래스 h-14와 같다
const CAPSULE_HEIGHT = 56;
// 알약 전환과 검색 칸 접기는 같은 시간·곡선으로 움직인다 (따로 움직이면 한 박자씩 어긋나 끊겨 보인다)
const CAPSULE_TRANSITION = 'duration-[640ms] ease-[cubic-bezier(0.16,1,0.3,1)]';

const ACCOUNT_LINK_CLASS = 'font-medium text-ink-soft hover:text-heading';

// 로그인하면 이름·마이페이지·로그아웃, 아니면 로그인·회원가입(버튼)
function useAccountItems(): { key: string; node: ReactNode }[] {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const authenticated = useAuthStore(
    (state) => state.status === 'authenticated',
  );
  const { data: me } = useMe();

  if (!authenticated) {
    return [
      {
        key: 'login',
        node: (
          <Link to="/login" className={ACCOUNT_LINK_CLASS}>
            로그인
          </Link>
        ),
      },
      {
        key: 'signup',
        node: (
          <Link
            to="/signup"
            className="rounded-lg bg-primary px-3.5 py-2 font-bold text-on-primary hover:bg-primary-hover"
          >
            회원가입
          </Link>
        ),
      },
    ];
  }

  const handleLogout = async () => {
    await logout();
    // 다음 사용자에게 내 데이터가 보이지 않도록 화면 데이터를 모두 비운다
    queryClient.clear();
    navigate('/');
  };

  return [
    ...(me
      ? [
          {
            key: 'name',
            // 이름은 최대 30자까지 가입할 수 있어서, 길면 줄여 보여 줘 상단바가 두 줄로 깨지지 않게 한다
            node: (
              <span
                title={`${me.name}님`}
                className="inline-block max-w-28 truncate align-bottom font-medium text-heading"
              >
                {me.name}님
              </span>
            ),
          },
        ]
      : []),
    // 관리자에게만 보인다. 눌러서 가는 화면도 서버가 다시 확인한다
    ...(me?.role === 'ADMIN'
      ? [
          {
            key: 'admin',
            node: (
              <Link to="/admin/notices" className={ACCOUNT_LINK_CLASS}>
                관리자
              </Link>
            ),
          },
        ]
      : []),
    {
      key: 'mypage',
      node: (
        <Link to="/mypage" className={ACCOUNT_LINK_CLASS}>
          마이페이지
        </Link>
      ),
    },
    {
      key: 'logout',
      node: (
        <button
          type="button"
          onClick={handleLogout}
          className={ACCOUNT_LINK_CLASS}
        >
          로그아웃
        </button>
      ),
    },
  ];
}

function Header() {
  const navigate = useNavigate();
  const accountItems = useAccountItems();
  const [keyword, setKeyword] = useState('');
  const [menuOpen, setMenuOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const closeMenu = useCallback(() => setMenuOpen(false), []);
  const logoRef = useRef<HTMLDivElement>(null);
  const gnbRef = useRef<HTMLElement>(null);
  const rightRef = useRef<HTMLDivElement>(null);
  const capsule = useCapsuleMode();
  // 알약일 때 검색칸은 접어 두고, 돋보기를 누르거나 입력 중일 때만 펼친다
  const [searchOpen, setSearchOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const searchFormRef = useRef<HTMLFormElement>(null);
  const searchCollapsed = capsule && !searchOpen && !keyword;
  const capsuleWidth = useCapsuleWidth(
    logoRef,
    gnbRef,
    rightRef,
    searchFormRef,
    searchCollapsed ? SEARCH_WIDTH.collapsed : SEARCH_WIDTH.open,
  );

  // Esc로 닫고 포커스를 햄버거 버튼으로 돌려준다
  useEffect(() => {
    if (!menuOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      setMenuOpen(false);
      menuButtonRef.current?.focus();
    };
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [menuOpen]);

  const handleSearch = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const trimmed = keyword.trim();
    if (!trimmed) return;
    navigate(`/search?q=${encodeURIComponent(trimmed)}`);
  };

  return (
    // 로고·주 메뉴·검색·계정을 한 줄에 둔다. 좁은 화면에서는 주 메뉴 대신 햄버거 메뉴(MobileMenu).
    // 넓은 화면에서는 위에 붙어 따라오고, 맨 위를 벗어나면 가운데로 모인 반투명 알약이 된다.
    // 알약일 때도 바깥 높이(h-17)는 그대로 둬서 아래 내용이 들썩이지 않게 한다
    <header
      className={`relative z-40 border-b lg:sticky lg:top-0 lg:h-17 print:hidden ${
        capsule
          ? 'border-transparent bg-transparent'
          : 'border-line bg-surface-raised'
      }`}
    >
      <div
        style={{ '--capsule': `${capsuleWidth}px` } as CSSProperties}
        className={`mx-auto flex flex-wrap items-center justify-between gap-y-3 border transition-[max-width,height,margin,padding,border-radius,column-gap,background-color,border-color,box-shadow] ${CAPSULE_TRANSITION} max-lg:py-3.5 lg:flex-nowrap ${
          capsule
            ? 'glass-frame mt-1.5 h-14 max-w-(--capsule) gap-x-5 rounded-full border-transparent pr-3 pl-5'
            : 'max-w-page gap-x-6 border-transparent px-6 lg:h-17'
        }`}
      >
        {/* 알약일 때 유리 바탕 (높이 h-14 = 56px, 모서리는 그 절반) */}
        {capsule && (
          <GlassSurface radius={CAPSULE_HEIGHT / 2} height={CAPSULE_HEIGHT} />
        )}
        <div
          ref={logoRef}
          className="flex items-center justify-between max-lg:w-full lg:shrink-0"
        >
          <Link to="/" className="flex items-center gap-2.5 text-heading">
            <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-on-primary">
              AI
            </span>
            {/* TODO(미결): 서비스 이름 확정 후 교체 */}
            <span className="text-xl font-bold">ai-lms</span>
          </Link>
          <button
            ref={menuButtonRef}
            type="button"
            aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
            aria-expanded={menuOpen}
            aria-controls={MOBILE_MENU_ID}
            onClick={() => setMenuOpen((open) => !open)}
            className="flex size-9 items-center justify-center rounded-md border border-input-border text-heading lg:hidden"
          >
            {/* 아이콘이 바뀔 때 살짝 돌며 나타난다 */}
            <m.span
              key={menuOpen ? 'close' : 'open'}
              initial={{ rotate: -90, opacity: 0 }}
              animate={{ rotate: 0, opacity: 1 }}
              transition={{ duration: MOTION_DURATION.fast }}
              className="flex"
            >
              {menuOpen ? (
                <X size={18} aria-hidden="true" />
              ) : (
                <Menu size={18} aria-hidden="true" />
              )}
            </m.span>
          </button>
        </div>

        <Gnb ref={gnbRef} compact={capsule} />

        <div
          ref={rightRef}
          className="flex flex-wrap items-center gap-3 text-sm lg:shrink-0 lg:flex-nowrap"
        >
          {/* 알약일 때는 돋보기 버튼만 두고, 누르면 칸이 옆으로 펼쳐진다 (상자 안의 상자가 생기지 않게) */}
          {/* 폭(w-9 / w-42)은 useCapsule의 SEARCH_WIDTH와 같다. 칸 폭 하나만 바뀌고 안의 입력칸은 남는 폭을 채운다 */}
          <form
            ref={searchFormRef}
            role="search"
            onSubmit={handleSearch}
            className={`flex h-9 shrink-0 items-center gap-1.5 overflow-hidden transition-[width,padding,border-color,background-color] ${CAPSULE_TRANSITION} ${
              searchCollapsed
                ? 'w-9 rounded-full border border-transparent px-[3px]'
                : `w-42 pr-1 pl-2.5 ${FIELD_CLASS}`
            }`}
          >
            <label htmlFor="global-search" className="sr-only">
              검색
            </label>
            <input
              ref={searchInputRef}
              id="global-search"
              type="search"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onBlur={() => {
                if (!keyword) setSearchOpen(false);
              }}
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  setSearchOpen(false);
                  e.currentTarget.blur();
                }
              }}
              tabIndex={searchCollapsed ? -1 : undefined}
              placeholder="자료, 개념 검색"
              className="w-0 min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-ink-muted"
            />
            <button
              type={searchCollapsed ? 'button' : 'submit'}
              aria-label={searchCollapsed ? '검색 열기' : '검색하기'}
              onClick={
                searchCollapsed
                  ? () => {
                      setSearchOpen(true);
                      searchInputRef.current?.focus();
                    }
                  : undefined
              }
              // 접혀도 펼쳐져도 크기·모양이 같은 버튼 (바뀌는 순간 아이콘이 튀지 않게)
              className="grid size-7 shrink-0 place-items-center rounded-full text-ink-soft hover:bg-primary-soft hover:text-heading"
            >
              <Search size={17} aria-hidden="true" />
            </button>
          </form>

          <nav aria-label="계정" className="flex items-center gap-3">
            {accountItems.map((item) => (
              <span key={item.key}>{item.node}</span>
            ))}
          </nav>

          <ThemeToggle />
        </div>
      </div>
      <AnimatePresence initial={false}>
        {menuOpen && <MobileMenu onClose={closeMenu} />}
      </AnimatePresence>
    </header>
  );
}

export default Header;
