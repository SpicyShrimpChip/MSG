interface LogoMarkProps {
  className?: string;
}

// MSG 로고 표시(11/6 B1 시안): 점 두 개를 지나 과녁에 닿는 길 = 학습 순서를 따라 목표에 닿는다.
// public/favicon.svg와 같은 모양이다. 한쪽을 고치면 다른 쪽도 같이 고친다.
// 색은 테마 변수(primary·on-primary)라 어두운 화면 모드에서도 따라 바뀐다.
function LogoMark({ className = 'size-8' }: LogoMarkProps) {
  return (
    <svg viewBox="0 0 40 40" aria-hidden="true" className={className}>
      <rect width="40" height="40" rx="10" className="fill-primary" />
      <path
        d="M9 29 C15 29 13 20 20 20 S25 11 31 11"
        fill="none"
        strokeWidth="2.6"
        strokeLinecap="round"
        className="stroke-on-primary"
      />
      <circle cx="9" cy="29" r="3.2" className="fill-on-primary" />
      <circle cx="20" cy="20" r="3.2" className="fill-on-primary" />
      <circle
        cx="31"
        cy="11"
        r="4.2"
        fill="none"
        strokeWidth="2.4"
        className="stroke-on-primary"
      />
      <circle cx="31" cy="11" r="1.6" className="fill-on-primary" />
    </svg>
  );
}

export default LogoMark;
