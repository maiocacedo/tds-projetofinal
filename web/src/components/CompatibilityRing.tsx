import { Sparkles } from 'lucide-react';
import { forwardRef, type CSSProperties } from 'react';

export interface CompatibilityRingProps {
  /** Percentual 0–100; `null` quando ainda não há dados (usuário novo). */
  score: number | null;
  size?: 'sm' | 'md' | 'lg';
  /** Mostra o percentual dentro do anel (padrão `true`). */
  showLabel?: boolean;
  className?: string;
}

const RING: Record<NonNullable<CompatibilityRingProps['size']>, { px: number; stroke: number; text: string }> = {
  sm: { px: 44, stroke: 4, text: 'text-xs' },
  md: { px: 60, stroke: 5, text: 'text-sm' },
  lg: { px: 84, stroke: 7, text: 'text-lg' },
};

function toneFor(score: number): string {
  if (score >= 70) return 'text-feito-texto';
  if (score >= 40) return 'text-pontos-texto';
  return 'text-texto-suave';
}

/**
 * Anel de compatibilidade. Sem score (`null`) mostra o selo "Novo no Nosso
 * Teto" em vez de inventar um percentual.
 */
export const CompatibilityRing = forwardRef<HTMLSpanElement, CompatibilityRingProps>(
  function CompatibilityRing({ score, size = 'md', showLabel = true, className }, ref) {
    if (score === null || !Number.isFinite(score)) {
      return (
        <span
          ref={ref}
          className={[
            'inline-flex items-center gap-1 rounded-full border border-pontos-borda bg-pontos-fundo px-2.5 py-0.5 text-xs font-bold leading-5 text-pontos-texto',
            className,
          ]
            .filter(Boolean)
            .join(' ')}
        >
          <Sparkles aria-hidden="true" className="size-3.5" />
          Novo no Nosso Teto
        </span>
      );
    }

    const value = Math.round(Math.min(100, Math.max(0, score)));
    const dims = RING[size];
    const radius = (dims.px - dims.stroke) / 2;
    const circumference = 2 * Math.PI * radius;
    const offset = circumference * (1 - value / 100);
    const arcStyle = {
      strokeDasharray: circumference,
      strokeDashoffset: offset,
      '--nt-ring-circumference': `${circumference}`,
      '--nt-ring-offset': `${offset}`,
    } as CSSProperties;

    return (
      <span
        ref={ref}
        role="img"
        aria-label={`Compatibilidade de ${value}%`}
        className={[
          'relative inline-flex shrink-0 items-center justify-center',
          toneFor(value),
          className,
        ]
          .filter(Boolean)
          .join(' ')}
        style={{ width: dims.px, height: dims.px }}
      >
        <svg
          aria-hidden="true"
          width={dims.px}
          height={dims.px}
          viewBox={`0 0 ${dims.px} ${dims.px}`}
          className="-rotate-90"
        >
          <circle
            cx={dims.px / 2}
            cy={dims.px / 2}
            r={radius}
            fill="none"
            stroke="var(--color-linha)"
            strokeWidth={dims.stroke}
          />
          <circle
            className="nt-ring-arc"
            cx={dims.px / 2}
            cy={dims.px / 2}
            r={radius}
            fill="none"
            stroke="currentColor"
            strokeWidth={dims.stroke}
            strokeLinecap="round"
            style={arcStyle}
          />
        </svg>
        {showLabel && (
          <span aria-hidden="true" className={['absolute font-extrabold', dims.text].join(' ')}>
            {value}%
          </span>
        )}
      </span>
    );
  },
);
