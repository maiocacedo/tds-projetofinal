import { Slot } from '@radix-ui/react-slot';
import { Loader2 } from 'lucide-react';
import { forwardRef, type ButtonHTMLAttributes } from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  asChild?: boolean;
}

const VARIANT_CLASSES: Record<ButtonVariant, string> = {
  primary: 'bg-tomate text-fundo hover:bg-tomate-escuro border border-transparent',
  secondary: 'border-2 border-tinta bg-transparent text-tinta hover:bg-superficie',
  ghost: 'border border-transparent bg-transparent text-tinta hover:bg-superficie',
  danger: 'bg-atrasado-texto text-fundo hover:opacity-95 border border-transparent',
};

const SIZE_CLASSES: Record<ButtonSize, string> = {
  sm: 'min-h-11 px-4 text-sm font-bold',
  md: 'min-h-11 px-5 text-[15px] font-bold',
  lg: 'min-h-[52px] px-7 text-base font-extrabold',
};

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    asChild = false,
    disabled,
    type,
    className,
    children,
    ...rest
  },
  ref,
) {
  const isDisabled = Boolean(disabled || loading);
  const classes = [
    'nt-press inline-flex items-center justify-center gap-2 rounded-full cursor-pointer select-none',
    'disabled:pointer-events-none disabled:opacity-55',
    VARIANT_CLASSES[variant],
    SIZE_CLASSES[size],
    className,
  ]
    .filter(Boolean)
    .join(' ');

  if (asChild) {
    return (
      <Slot
        ref={ref}
        aria-busy={loading || undefined}
        aria-disabled={isDisabled || undefined}
        className={classes}
        {...rest}
      >
        {children}
      </Slot>
    );
  }

  return (
    <button
      ref={ref}
      type={type ?? 'button'}
      disabled={isDisabled}
      aria-busy={loading || undefined}
      className={classes}
      {...rest}
    >
      {loading && <Loader2 aria-hidden="true" className="size-4 motion-safe:animate-spin shrink-0" />}
      {children}
    </button>
  );
});
