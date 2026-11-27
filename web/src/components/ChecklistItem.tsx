import { forwardRef, useEffect, useId, useRef, type ReactNode } from 'react';
import { useReducedMotion } from '../motion/useReducedMotion';

export interface ChecklistItemProps {
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  label: string;
  meta?: ReactNode;
  disabled?: boolean;
  settleDelayMs?: number;
  onSettled?: () => void;
  action?: ReactNode;
  className?: string;
}

export const ChecklistItem = forwardRef<HTMLDivElement, ChecklistItemProps>(
  function ChecklistItem(
    {
      checked,
      onCheckedChange,
      label,
      meta,
      disabled = false,
      settleDelayMs = 950,
      onSettled,
      action,
      className,
    },
    ref,
  ) {
    const id = useId();
    const reducedMotion = useReducedMotion();
    const prevCheckedRef = useRef(checked);
    const latestRef = useRef({ onSettled, reducedMotion, settleDelayMs });

    useEffect(() => {
      latestRef.current = { onSettled, reducedMotion, settleDelayMs };
    }, [onSettled, reducedMotion, settleDelayMs]);

    // Só a transição desmarcado → marcado agenda o "assentar"; desmarcar antes
    // do prazo (ou desmontar) cancela o timer. Nunca depende de `animationend`.
    useEffect(() => {
      const becameChecked = !prevCheckedRef.current && checked;
      prevCheckedRef.current = checked;
      if (!becameChecked) return;

      const { reducedMotion: reduced, settleDelayMs: delay } = latestRef.current;
      if (reduced || delay <= 0) {
        latestRef.current.onSettled?.();
        return;
      }

      const timerId = window.setTimeout(() => {
        latestRef.current.onSettled?.();
      }, delay);

      return () => {
        window.clearTimeout(timerId);
      };
    }, [checked]);

    return (
      <div
        ref={ref}
        data-checked={checked ? 'true' : 'false'}
        className={[
          'flex min-h-11 items-center gap-3 rounded-[18px] bg-superficie px-4 py-3 transition-colors',
          disabled ? 'opacity-60' : '',
          className,
        ]
          .filter(Boolean)
          .join(' ')}
      >
        <label
          htmlFor={id}
          className={[
            'flex min-h-11 flex-1 items-center gap-3',
            disabled ? 'cursor-not-allowed' : 'cursor-pointer',
          ].join(' ')}
        >
          <input
            id={id}
            type="checkbox"
            checked={checked}
            disabled={disabled}
            onChange={(event) => onCheckedChange(event.target.checked)}
            className="peer sr-only"
          />
          <span
            aria-hidden="true"
            className={[
              'nt-press flex size-[22px] shrink-0 items-center justify-center rounded-[6px] border-2 transition-colors',
              'peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-tomate',
              checked
                ? 'border-tomate bg-tomate text-fundo'
                : 'border-tinta/40 bg-fundo text-transparent',
            ].join(' ')}
          >
            <svg
              width="14"
              height="14"
              viewBox="0 0 16 16"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
            >
              <path
                d="M3.5 8.5L6.5 11.5L12.5 4.5"
                className={['nt-check-path', checked ? 'nt-check-path-done' : ''].join(' ')}
              />
            </svg>
          </span>
          <span className="flex min-w-0 flex-1 flex-col gap-0.5">
            <span
              className={[
                'nt-strike self-start text-[15px] font-semibold',
                checked ? 'nt-strike-done text-legenda' : 'text-tinta',
              ].join(' ')}
            >
              {label}
            </span>
            {meta && <span className="text-[13px] text-legenda">{meta}</span>}
          </span>
        </label>
        {action && <div className="shrink-0">{action}</div>}
      </div>
    );
  },
);
