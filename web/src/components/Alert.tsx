import {
  AlertCircle,
  CheckCircle2,
  Clock,
  Info,
  Sparkles,
  type LucideIcon,
} from 'lucide-react';
import { forwardRef, type ReactNode } from 'react';
import type { StatusTone } from './StatusChip';

export interface AlertAction {
  label: string;
  onClick: () => void;
}

export interface AlertProps {
  tone?: StatusTone;
  title: string;
  description?: ReactNode;
  action?: AlertAction;
  className?: string;
}

const ALERT_STYLES: Record<
  StatusTone,
  { container: string; iconBg: string; title: string; desc: string; icon: LucideIcon }
> = {
  atrasado: {
    container: 'bg-atrasado-fundo',
    iconBg: 'bg-atrasado-texto text-fundo',
    title: 'text-atrasado-texto',
    desc: 'text-atrasado-texto/90',
    icon: AlertCircle,
  },
  'vence-logo': {
    container: 'bg-vence-logo-fundo',
    iconBg: 'bg-vence-logo-texto text-fundo',
    title: 'text-vence-logo-texto',
    desc: 'text-vence-logo-texto/90',
    icon: Clock,
  },
  feito: {
    container: 'bg-feito-fundo',
    iconBg: 'bg-feito-texto text-fundo',
    title: 'text-feito-texto',
    desc: 'text-feito-texto/90',
    icon: CheckCircle2,
  },
  pontos: {
    container: 'bg-pontos-fundo border border-pontos-borda',
    iconBg: 'bg-pontos-texto text-fundo',
    title: 'text-pontos-texto',
    desc: 'text-pontos-texto/90',
    icon: Sparkles,
  },
  neutro: {
    container: 'bg-superficie border border-linha',
    iconBg: 'bg-tinta text-fundo',
    title: 'text-tinta',
    desc: 'text-texto-suave',
    icon: Info,
  },
};

export const Alert = forwardRef<HTMLDivElement, AlertProps>(function Alert(
  { tone = 'neutro', title, description, action, className },
  ref,
) {
  const style = ALERT_STYLES[tone];
  const Icon = style.icon;

  return (
    <div
      ref={ref}
      role={tone === 'atrasado' ? 'alert' : 'status'}
      className={[
        'flex items-center gap-3 rounded-[18px] p-3.5',
        style.container,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span
        aria-hidden="true"
        className={[
          'flex size-10 shrink-0 items-center justify-center rounded-full',
          style.iconBg,
        ].join(' ')}
      >
        <Icon className="size-5" />
      </span>
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className={['text-[15px] font-bold leading-snug', style.title].join(' ')}>
          {title}
        </span>
        {description && (
          <div className={['text-[13px] leading-snug', style.desc].join(' ')}>
            {description}
          </div>
        )}
      </div>
      {action && (
        <button
          type="button"
          onClick={action.onClick}
          className="nt-press inline-flex min-h-11 shrink-0 items-center justify-center rounded-full bg-tinta px-4 text-sm font-bold text-fundo hover:opacity-90 cursor-pointer"
        >
          {action.label}
        </button>
      )}
    </div>
  );
});
