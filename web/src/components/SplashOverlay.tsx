import { useEffect, useRef, useState } from 'react';
import { useSession } from '../auth/SessionContext';
import { useReducedMotion } from '../motion/useReducedMotion';

/** Flag de sessão: a splash aparece só na primeira abertura de cada aba. */
export const SPLASH_SEEN_KEY = 'nt:splash-shown';

/** Tempo mínimo da abertura (desenho do telhado + porta) antes do mergulho. */
export const SPLASH_INTRO_MS = 1600;
/** Duração do mergulho pela porta até o site. */
export const SPLASH_EXIT_MS = 860;
/** Com `prefers-reduced-motion`: só um instante parado, sem mergulho. */
export const SPLASH_REDUCED_MS = 300;
/** Teto de segurança: mesmo com a sessão travada, a splash nunca prende a tela. */
export const SPLASH_MAX_MS = 8000;

const HOUSE_PATH = 'M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z';
const TOMATE = '#c9402a';

type Phase = 'intro' | 'leaving' | 'gone';

function readSeen(): boolean {
  try {
    return window.sessionStorage.getItem(SPLASH_SEEN_KEY) !== null;
  } catch {
    // Sem sessionStorage (modo privado restrito): não arrisca repetir a splash.
    return true;
  }
}

function markSeen(): void {
  try {
    window.sessionStorage.setItem(SPLASH_SEEN_KEY, '1');
  } catch {
    // ignora
  }
}

export interface SplashOverlayProps {
  className?: string;
}

/**
 * Splash vermelha (E-splash-vermelha). Montada como irmã de `<AppRoutes />`:
 * fica por cima (`fixed inset-0 z-50`) enquanto as rotas já renderizam por
 * baixo, cobre o refresh silencioso da sessão e sai mergulhando pela porta
 * quando o tempo mínimo passou **e** a sessão deixou de carregar.
 */
export function SplashOverlay({ className }: SplashOverlayProps = {}) {
  const { status } = useSession();
  const reducedMotion = useReducedMotion();
  const [phase, setPhase] = useState<Phase>(() => (readSeen() ? 'gone' : 'intro'));
  const [minElapsed, setMinElapsed] = useState(false);
  const skipRef = useRef<HTMLButtonElement>(null);

  const active = phase !== 'gone';

  // Grava a flag assim que monta (antes de qualquer saída).
  useEffect(() => {
    if (active) markSeen();
  }, [active]);

  useEffect(() => {
    if (!active) return;
    const timer = window.setTimeout(
      () => setMinElapsed(true),
      reducedMotion ? SPLASH_REDUCED_MS : SPLASH_INTRO_MS,
    );
    return () => window.clearTimeout(timer);
  }, [active, reducedMotion]);

  useEffect(() => {
    if (!active) return;
    const timer = window.setTimeout(() => setPhase('gone'), SPLASH_MAX_MS);
    return () => window.clearTimeout(timer);
  }, [active]);

  useEffect(() => {
    if (phase !== 'intro' || !minElapsed || status === 'loading') return;
    setPhase(reducedMotion ? 'gone' : 'leaving');
  }, [phase, minElapsed, status, reducedMotion]);

  useEffect(() => {
    if (phase !== 'leaving') return;
    const timer = window.setTimeout(() => setPhase('gone'), SPLASH_EXIT_MS);
    return () => window.clearTimeout(timer);
  }, [phase]);

  useEffect(() => {
    if (phase === 'intro') skipRef.current?.focus({ preventScroll: true });
  }, [phase]);

  if (phase === 'gone') return null;

  return (
    <div
      data-testid="splash-overlay"
      aria-label="Abrindo o Nosso Teto"
      role="region"
      className={[
        'fixed inset-0 z-50 flex items-center justify-center overflow-hidden pb-10',
        phase === 'leaving' ? 'nt-splash-leaving pointer-events-none' : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div
        aria-hidden="true"
        className="nt-splash-zoom relative size-[min(504px,92vw)] flex-none"
      >
        {/* Parede tomate gigante com o vão da porta: ao mergulhar, o site aparece pela porta. */}
        <svg
          className="absolute inset-0 z-[1] block overflow-visible"
          width="100%"
          height="100%"
          viewBox="24 24 72 72"
          aria-hidden="true"
        >
          <defs>
            <mask id="nt-splash-door-hole" maskUnits="userSpaceOnUse" x="49" y="67" width="22" height="22">
              <rect x="49" y="67" width="22" height="22" fill="white" />
              <rect className="nt-splash-door" x="51" y="69" width="18" height="18" fill="black" />
            </mask>
            <clipPath id="nt-splash-door-clip">
              <rect x="51" y="69" width="18" height="18" />
            </clipPath>
            <linearGradient id="nt-splash-leaf-shade" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#3c0c05" stopOpacity="0.95" />
              <stop offset="70%" stopColor="#4a1108" stopOpacity="0.65" />
              <stop offset="100%" stopColor="#5c170b" stopOpacity="0.35" />
            </linearGradient>
            <linearGradient id="nt-splash-beam-outer" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="#ffffff" stopOpacity="0.28" />
              <stop offset="55%" stopColor="#fff4e4" stopOpacity="0.10" />
              <stop offset="100%" stopColor="#fff4e4" stopOpacity="0" />
            </linearGradient>
            <linearGradient id="nt-splash-beam-inner" x1="0%" y1="0%" x2="0%" y2="100%">
              <stop offset="0%" stopColor="#ffffff" stopOpacity="0.48" />
              <stop offset="50%" stopColor="#fffaf0" stopOpacity="0.16" />
              <stop offset="100%" stopColor="#fffaf0" stopOpacity="0" />
            </linearGradient>
            <radialGradient id="nt-splash-door-veil" cx="56%" cy="52%" r="72%">
              <stop offset="0%" stopColor="#ffffff" />
              <stop offset="68%" stopColor="#fffdf8" />
              <stop offset="100%" stopColor="#fff3de" />
            </radialGradient>
            <radialGradient id="nt-splash-door-halo" cx="50%" cy="32%" r="62%">
              <stop offset="0%" stopColor="#ffffff" stopOpacity="0.32" />
              <stop offset="55%" stopColor="#fff2de" stopOpacity="0.09" />
              <stop offset="100%" stopColor="#fff2de" stopOpacity="0" />
            </radialGradient>
          </defs>
          {/* Luz quente atrás da porta (cobre o que está por baixo até o mergulho). */}
          <rect
            className="nt-splash-door-veil"
            x="51"
            y="69"
            width="18"
            height="18"
            fill="url(#nt-splash-door-veil)"
          />
          <rect x="-360" y="-360" width="840" height="429.3" fill={TOMATE} />
          <rect x="-360" y="87" width="840" height="393" fill={TOMATE} />
          <rect x="-360" y="69" width="411.3" height="18.3" fill={TOMATE} />
          <rect x="68.7" y="69" width="411.3" height="18.3" fill={TOMATE} />
          <g mask="url(#nt-splash-door-hole)">
            <rect x="50.7" y="68.7" width="18.6" height="18.6" fill={TOMATE} />
          </g>
          <g clipPath="url(#nt-splash-door-clip)">
            <g transform="translate(51 78)">
              <g className="nt-splash-door-leaf">
                <g transform="translate(-51 -78)">
                  <rect x="51" y="69.6" width="18" height="17.4" fill={TOMATE} />
                  <rect x="51" y="69.6" width="18" height="17.4" fill="url(#nt-splash-leaf-shade)" opacity="0.46" />
                </g>
              </g>
            </g>
          </g>
          <g transform="translate(60 86)">
            <g className="nt-splash-beam">
              <g transform="translate(-60 -86)">
                <ellipse cx="60" cy="88.5" rx="22" ry="12" fill="url(#nt-splash-door-halo)" />
                <polygon points="50,86.5 70,86.5 96,124 24,124" fill="url(#nt-splash-beam-outer)" />
                <polygon points="52.5,86.5 67.5,86.5 80,118 40,118" fill="url(#nt-splash-beam-inner)" />
              </g>
            </g>
          </g>
        </svg>
        {/* Casa branca desenhada (sem quadrado), que assenta em geleia. */}
        <svg
          className="absolute inset-0 z-[2] block overflow-visible"
          width="100%"
          height="100%"
          viewBox="24 24 72 72"
          aria-hidden="true"
        >
          <g transform="translate(60 78)">
            <g className="nt-splash-house-parallax">
              <g transform="translate(-60 -78)">
                <g transform="translate(60 87)">
                  <g className="nt-splash-house-jello">
                    <g transform="translate(-60 -87)">
                      <g transform="translate(24 24) scale(3)">
                        <path
                          className="nt-splash-house"
                          pathLength={1}
                          d={HOUSE_PATH}
                          fill="none"
                          stroke="white"
                          strokeWidth="0.6"
                          strokeLinecap="round"
                          strokeLinejoin="round"
                        />
                      </g>
                    </g>
                  </g>
                </g>
              </g>
            </g>
          </g>
        </svg>
      </div>
      <span
        aria-hidden="true"
        className="nt-splash-wordmark pointer-events-none absolute inset-x-0 bottom-[12%] z-[3] text-center text-2xl font-extrabold tracking-[-0.02em] text-fundo"
      >
        Nosso Teto
      </span>
      <button
        ref={skipRef}
        type="button"
        onClick={() => setPhase('gone')}
        className="nt-splash-skip nt-press absolute bottom-4 right-4 z-[4] inline-flex min-h-11 items-center rounded-full border border-fundo/25 bg-[oklch(0.32_0.11_30)] px-5 text-sm font-semibold text-fundo shadow-[0_2px_12px_oklch(0.20_0.08_28/0.28)] hover:bg-[oklch(0.26_0.10_30)] cursor-pointer"
      >
        Pular
      </button>
    </div>
  );
}
