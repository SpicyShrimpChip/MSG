import { useMemo, type CSSProperties } from 'react';
import { buildEdgeLens, cn, supportsGlassLens, type LensEdge } from '@/utils';

// 크롬·엣지에서만 굴절을 그린다 (다른 브라우저는 흐림만)
const LENS_SUPPORTED =
  typeof navigator !== 'undefined' &&
  typeof CSS !== 'undefined' &&
  supportsGlassLens(
    navigator.userAgent,
    CSS.supports('backdrop-filter', 'blur(1px)'),
  );

// 위아래 띠는 가로로 모양이 같아서, 넉넉한 폭으로 한 번 그려 두고 띠 길이만큼 잘라 쓴다 (폭이 바뀌어도 다시 안 그림)
const BAND_MAP_WIDTH = 4000;

interface GlassSurfaceProps {
  // 유리 틀의 모서리 둥글기(px). 알약이면 높이의 절반
  radius: number;
  // 유리 틀 높이(px)
  height: number;
  // 위아래로 휘는 띠 두께(px)
  band?: number;
  // 휘는 세기와 무지개 번짐 (utils/liquidGlass.ts)
  strength?: number;
  chromatic?: number;
  // 바깥 요소에 더할 클래스 (예: 좁은 화면에서 숨기기). 감싸는 상자를 따로 두면 모서리 둥글기(inherit)를 못 물려받아 네모가 된다
  className?: string;
}

const lensStyle = (style: CSSProperties, lens: string) =>
  ({ ...style, '--glass-lens': lens }) as CSSProperties;

// .glass-frame 안에 깔아 유리 바탕을 그린다. 굴절(SVG 필터)은 CPU로 그려져 넓게 걸면 스크롤이 느려지므로,
// 휘는 가장자리(양 끝 둥근 부분·위아래 띠)에만 걸고 가운데 넓은 곳은 흐림만 둔다.
// 조각 지도는 틀 폭과 상관없어서 크기가 바뀌거나 스크롤해도 끄고 켜지 않고 늘 같은 모습이다
function GlassSurface({
  radius,
  height,
  band = 10,
  strength = 40,
  chromatic = 2,
  className,
}: GlassSurfaceProps) {
  const lens = useMemo(() => {
    if (!LENS_SUPPORTED) return null;
    const make = (edge: LensEdge, width: number, tileHeight: number) =>
      buildEdgeLens({
        edge,
        width,
        height: tileHeight,
        band,
        strength,
        chromatic,
      });
    return {
      left: make('left', radius, height),
      right: make('right', radius, height),
      top: make('top', BAND_MAP_WIDTH, band),
      bottom: make('bottom', BAND_MAP_WIDTH, band),
    };
  }, [radius, height, band, strength, chromatic]);

  if (!lens) {
    return (
      <span aria-hidden="true" className={cn('glass-surface', className)}>
        <span className="glass-tile inset-0" />
      </span>
    );
  }

  return (
    <span aria-hidden="true" className={cn('glass-surface', className)}>
      <span
        data-lens=""
        className="glass-tile"
        style={lensStyle({ left: 0, top: 0, width: radius, height }, lens.left)}
      />
      <span
        data-lens=""
        className="glass-tile"
        style={lensStyle(
          { right: 0, top: 0, width: radius, height },
          lens.right,
        )}
      />
      <span
        data-lens=""
        className="glass-tile"
        style={lensStyle(
          { left: radius, right: radius, top: 0, height: band },
          lens.top,
        )}
      />
      <span
        data-lens=""
        className="glass-tile"
        style={lensStyle(
          { left: radius, right: radius, top: height - band, height: band },
          lens.bottom,
        )}
      />
      <span
        className="glass-tile"
        style={{
          left: radius,
          right: radius,
          top: band,
          height: height - band * 2,
        }}
      />
    </span>
  );
}

export default GlassSurface;
