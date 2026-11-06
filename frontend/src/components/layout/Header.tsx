import {
  useCallback,
  useEffect,
  useRef,
  useState,
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
import { cn } from '@/utils';
import Gnb from './Gnb';
import MobileMenu, { MOBILE_MENU_ID } from './MobileMenu';
import ThemeToggle from './ThemeToggle';
import { usePhone } from './usePhone';
import { useScrolled } from './useScrolled';
import { GlassSurface } from '../common';
import { FIELD_CLASS } from '../common/fieldClass';

// 넓은 화면 상단바 알약 높이(px): 맨 위 64(h-16), 스크롤하면 56(h-14). 유리 바탕(GlassSurface)에도 같은 값을 넘긴다
const CAPSULE_HEIGHT = { top: 64, scrolled: 56 };

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
            className="inline-flex h-10 items-center rounded-lg bg-primary px-4 font-bold text-on-primary hover:bg-primary-hover"
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
          data-menu-close=""
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
  const scrolled = useScrolled();
  const capsuleHeight = scrolled ? CAPSULE_HEIGHT.scrolled : CAPSULE_HEIGHT.top;
  // 스크롤하면 검색칸을 돋보기로 접고, 돋보기를 누르거나 입력 중일 때만 펼친다.
  // 휴대폰 폭은 늘 돋보기만 두고, 누르면 상단바 한 줄을 검색칸이 덮는다(닫으면 입력한 글자는 남긴다)
  const phone = usePhone();
  const [searchOpen, setSearchOpen] = useState(false);
  const searchInputRef = useRef<HTMLInputElement>(null);
  const searchCollapsed = phone
    ? !searchOpen
    : scrolled && !searchOpen && !keyword;
  const phoneSearchOpen = phone && searchOpen;

  // 휴대폰 검색칸은 상단바를 덮으므로, 칸 바깥을 누르면 확실히 닫는다 (포커스가 안 들어간 채 열린 경우도 닫히게)
  const searchFormRef = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (!phoneSearchOpen) return;
    const handlePointerDown = (e: PointerEvent) => {
      if (!searchFormRef.current?.contains(e.target as Node))
        setSearchOpen(false);
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [phoneSearchOpen]);

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
    // 좁은 화면: 흰 바탕 한 줄 + 햄버거 메뉴(MobileMenu). 넓은 화면처럼 위에 붙어 따라온다(11/6, 창을 좁힌 노트북에서 메뉴로 돌아가기 불편했다).
    // 넓은 화면: 처음부터 본문 폭(1152px)의 유리 알약이 위에 떠 있고, 스크롤하면 높이가 64 → 56px로 줄고 검색칸은 돋보기로 접힌다.
    // 바깥 자리(h-20 = 80px)는 그대로 둬서 아래 내용이 들썩이지 않게 한다. 배경은 투명이라 첫 화면 파란 띠는 이 자리까지 끌어올린다(Hero의 lg:-mt-20)
    <header className="sticky top-0 z-40 border-b border-line bg-surface-raised lg:h-20 lg:border-transparent lg:bg-transparent lg:pt-2 print:hidden">
      <div
        className={`glass-frame mx-auto flex flex-wrap items-center justify-between gap-x-6 gap-y-3 px-6 max-lg:max-w-page max-lg:py-3.5 max-lg:shadow-none max-lg:after:hidden max-sm:gap-x-3 max-sm:px-4 max-sm:py-2 lg:w-[calc(100%-3rem)] lg:max-w-[72rem] lg:flex-nowrap lg:rounded-full lg:pr-3 lg:pl-5 lg:transition-[height] lg:duration-300 lg:ease-out ${
          scrolled ? 'lg:h-14' : 'lg:h-16'
        }`}
      >
        {/* 유리 바탕 (모서리는 높이의 절반). 알약 바로 안에 둬야 둥근 모서리를 물려받는다. 넓은 화면에서만 */}
        <GlassSurface
          radius={capsuleHeight / 2}
          height={capsuleHeight}
          className="max-lg:hidden"
        />
        {/* 배치: [로고 + 메뉴] ……… [검색·계정·화면 모드]. 메뉴는 로고 옆(48px)에 붙이고 빈 공간은 가운데에 둔다
            (메뉴를 정가운데에 두면 넓은 오른쪽 묶음 때문에 메뉴가 검색칸에 붙어 오른쪽으로 쏠려 보였다, 11/6).
            min-w-max: 내용 폭 아래로는 줄지 않게 (없으면 "ai-lms"가 하이픈에서 끊긴다) */}
        <div className="flex items-center max-lg:mr-auto lg:min-w-max lg:shrink-0">
          <Link to="/" className="flex items-center gap-2.5 text-heading">
            <span className="flex size-8 items-center justify-center rounded-lg bg-primary text-sm font-bold text-on-primary">
              AI
            </span>
            {/* TODO(미결): 서비스 이름 확정 후 교체 */}
            <span className="text-xl font-bold whitespace-nowrap">ai-lms</span>
          </Link>
        </div>

        <Gnb />

        <div className="flex flex-wrap items-center gap-3 text-sm lg:ml-auto lg:min-w-max lg:shrink-0 lg:flex-nowrap">
          {/* 검색칸 자리는 펼친 폭(w-44)으로 고정하고 칸만 오른쪽으로 접는다.
              자리가 그대로라 검색칸이 접히고 펼쳐져도 메뉴는 움직이지 않는다 */}
          <div className="flex w-44 shrink-0 justify-end max-sm:w-10">
            {/* 맨 위에서는 펼쳐 두고, 스크롤하면 돋보기만 남긴다. 칸 폭(w-44 ↔ w-10) 하나만 알약 높이와 같은 시간으로 바뀌고,
                안의 입력칸은 남는 폭을 채운다. 돋보기 버튼은 두 모양에서 크기·자리가 같다(바뀌는 순간 튀지 않게) */}
            <form
              ref={searchFormRef}
              role="search"
              onSubmit={handleSearch}
              className={cn(
                'flex h-10 shrink-0 items-center gap-1.5 overflow-hidden transition-[width,padding,border-color,background-color] duration-300 ease-out',
                searchCollapsed
                  ? 'w-10 rounded-full border border-transparent px-1'
                  : ['w-44 pr-1 pl-3', FIELD_CLASS],
                phoneSearchOpen &&
                  'absolute inset-x-4 top-1/2 z-10 w-auto -translate-y-1/2 transition-none',
              )}
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
                onBlur={(e) => {
                  // 같은 칸의 돋보기(제출)를 누르는 중이면 접지 않는다 (접히면 제출 버튼이 열기 버튼으로 바뀐다)
                  if (e.currentTarget.form?.contains(e.relatedTarget)) return;
                  if (!keyword || phone) setSearchOpen(false);
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
                className="grid size-8 shrink-0 place-items-center rounded-full text-ink-soft hover:bg-primary-soft hover:text-heading"
              >
                <Search size={17} aria-hidden="true" />
              </button>
            </form>
          </div>

          {/* 휴대폰 폭에서는 계정·화면 모드를 메뉴 패널 맨 위로 옮긴다 */}
          <nav
            aria-label="계정"
            className="flex items-center gap-3 max-sm:hidden"
          >
            {accountItems.map((item) => (
              <span key={item.key}>{item.node}</span>
            ))}
          </nav>

          <span className="flex max-sm:hidden">
            <ThemeToggle />
          </span>
        </div>

        {/* 햄버거 버튼은 오른쪽 끝. 좁은 화면은 로고·검색·계정·버튼을 한 줄에 두고(위에 붙어 따라오므로 낮게),
            휴대폰 폭은 로고·돋보기·버튼만 남긴 한 줄(56px)이다 */}
        <button
          ref={menuButtonRef}
          type="button"
          aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
          aria-expanded={menuOpen}
          aria-controls={MOBILE_MENU_ID}
          onClick={() => setMenuOpen((open) => !open)}
          className="flex size-9 shrink-0 items-center justify-center rounded-md border border-input-border text-heading lg:hidden"
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
      <AnimatePresence initial={false}>
        {menuOpen && (
          <MobileMenu
            onClose={closeMenu}
            account={
              <>
                <nav aria-label="계정" className="flex items-center gap-3">
                  {accountItems.map((item) => (
                    <span key={item.key}>{item.node}</span>
                  ))}
                </nav>
                <ThemeToggle />
              </>
            }
          />
        )}
      </AnimatePresence>
    </header>
  );
}

export default Header;
