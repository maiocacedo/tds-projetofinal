import { forwardRef, type HTMLAttributes, type ReactNode } from 'react';

export type StatusTone = 'atrasado' | 'vence-logo' | 'feito' | 'pontos' | 'neutro';

export interface StatusChipProps extends HTMLAttributes<HTMLSpanElement> {
  tone?: StatusTone;
  children: ReactNode;
}

const TONE_CLASSES: Record<StatusTone, string> = {
  atrasado: 'bg-atrasado-fundo text-atrasado-texto',
  'vence-logo': 'bg-vence-logo-fundo text-vence-logo-texto',
  feito: 'bg-feito-fundo text-feito-texto',
  pontos: 'bg-pontos-fundo text-pontos-texto border border-pontos-borda',
  neutro: 'bg-superficie text-texto-suave',
};

export const StatusChip = forwardRef<HTMLSpanElement, StatusChipProps>(function StatusChip(
  { tone = 'neutro', children, className, ...rest },
  ref,
) {
  return (
    <span
      ref={ref}
      className={[
        'inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-bold leading-5',
        TONE_CLASSES[tone],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...rest}
    >
      {children}
    </span>
  );
});
