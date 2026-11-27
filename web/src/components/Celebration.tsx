import { forwardRef, useEffect, useRef, type CSSProperties, type ReactNode } from 'react';
import { useReducedMotion } from '../motion/useReducedMotion';

export interface CelebrationProps {
  title: string;
  subtitle?: string;
  /** Ícone dentro do bloco tomate (padrão: a casinha do Nosso Teto). */
  icon?: ReactNode;
  /** Chamado quando a comemoração termina (com timeout, nunca por `animationend`). */
  onDone?: () => void;
  children?: ReactNode;
  /** Nível do título (padrão `h2`; use `h1` quando a comemoração for a tela). */
  titleAs?: 'h1' | 'h2';
  className?: string;
}

export const CELEBRATION_DURATION_MS = 2600;
const REDUCED_DURATION_MS = 300;

const CONFETTI_COLORS = [
  '#c9402a',
  'oklch(0.80 0.14 75)',
  'oklch(0.62 0.13 150)',
  'oklch(0.22 0.03 35)',
  'oklch(0.78 0.10 25)',
  'oklch(0.70 0.12 250)',
];

// Mesma distribuição do protótipo M-candidato: 34 peças saindo do ícone.
const CONFETTI: CSSProperties[] = Array.from({ length: 34 }, (_, i) => {
  const angle = (i / 34) * Math.PI * 2 + (i % 3) * 0.21;
  const dist = 90 + ((i * 37) % 70);
  const bx = Math.round(Math.cos(angle) * dist);
  const by = Math.round(Math.sin(angle) * dist * 0.8 - 40);
  const fx = Math.round(bx * 1.5 + ((i * 53) % 40) - 20);
  const fy = 420 + ((i * 71) % 220);
  const w = 6 + (i % 4) * 2;
  const h = i % 2 ? w : Math.round(w * 1.8);
  return {
    width: w,
    height: h,
    margin: `${-h / 2}px 0 0 ${-w / 2}px`,
    borderRadius: i % 5 === 0 ? 999 : 2,
    background: CONFETTI_COLORS[i % CONFETTI_COLORS.length],
    animationDelay: `${(0.32 + (i % 6) * 0.03).toFixed(2)}s`,
    '--bx': `${bx}px`,
    '--by': `${by}px`,
    '--fx': `${fx}px`,
    '--fy': `${fy}px`,
    '--rot': `${((i * 97) % 720) - 360}deg`,
  } as CSSProperties;
});

function HouseMark() {
  return (
    <svg
      width="48"
      height="48"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2.2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" />
    </svg>
  );
}

/**
 * Comemoração de conquista: ícone que pousa em geleia, raios girando atrás,
 * confete nas cores do app e textos subindo. Com `prefers-reduced-motion`
 * não há confete, só o estado calmo.
 */
export const Celebration = forwardRef<HTMLDivElement, CelebrationProps>(function Celebration(
  { title, subtitle, icon, onDone, children, titleAs = 'h2', className },
  ref,
) {
  const reducedMotion = useReducedMotion();
  const onDoneRef = useRef(onDone);
  useEffect(() => {
    onDoneRef.current = onDone;
  }, [onDone]);

  useEffect(() => {
    const timer = window.setTimeout(
      () => onDoneRef.current?.(),
      reducedMotion ? REDUCED_DURATION_MS : CELEBRATION_DURATION_MS,
    );
    return () => window.clearTimeout(timer);
  }, [reducedMotion]);

  const Title = titleAs;

  return (
    <div
      ref={ref}
      data-reduced-motion={reducedMotion ? 'true' : 'false'}
      className={[
        'relative isolate flex flex-col items-center gap-3 overflow-hidden px-6 py-10 text-center',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div aria-hidden="true" className="relative z-[2] size-[104px]">
        <span className="nt-rays" style={{ zIndex: -1 }} />
        <span className="nt-cel-house absolute inset-0 z-0 flex items-center justify-center rounded-[34px] bg-tomate text-fundo shadow-[0_24px_40px_-20px_oklch(0.45_0.16_33/0.6)]">
          {icon ?? <HouseMark />}
        </span>
        {!reducedMotion && (
          <span
            data-testid="celebration-confetti"
            className="absolute left-1/2 top-1/2 z-[1] size-0"
          >
            {CONFETTI.map((style, index) => (
              <span key={index} className="nt-confetti" style={style} />
            ))}
          </span>
        )}
      </div>
      {subtitle && (
        <p
          className="nt-rise relative z-[2] text-sm font-bold text-tomate-escuro"
          style={{ animationDelay: '0.55s' }}
        >
          {subtitle}
        </p>
      )}
      <Title
        className="nt-rise relative z-[2] text-[34px] font-extrabold leading-[1.05] tracking-[-0.03em] text-tinta"
        style={{ animationDelay: '0.65s' }}
      >
        {title}
      </Title>
      {children && (
        <div
          className="nt-rise relative z-[2] flex w-full flex-col items-center gap-3"
          style={{ animationDelay: '0.75s' }}
        >
          {children}
        </div>
      )}
    </div>
  );
});
