import { forwardRef, type HTMLAttributes } from 'react';

export interface LogoProps extends HTMLAttributes<HTMLSpanElement> {
  size?: 'sm' | 'md' | 'lg';
  showWordmark?: boolean;
  title?: string;
}

const MARK_SIZE: Record<NonNullable<LogoProps['size']>, { box: string; svg: number; text: string }> = {
  sm: { box: 'size-8 rounded-[10px]', svg: 18, text: 'text-lg' },
  md: { box: 'size-9 rounded-[12px]', svg: 20, text: 'text-xl' },
  lg: { box: 'size-12 rounded-[16px]', svg: 26, text: 'text-2xl' },
};

/**
 * Marca oficial Nosso Teto (casinha/telhado em tomate `#c9402a`).
 * Nunca renderiza `<h1>` para não colidir com headings de página.
 */
export const Logo = forwardRef<HTMLSpanElement, LogoProps>(function Logo(
  { size = 'md', showWordmark = true, title, className, ...rest },
  ref,
) {
  const dims = MARK_SIZE[size];
  const label = !showWordmark && title ? title : undefined;

  return (
    <span
      ref={ref}
      className={[
        'inline-flex items-center gap-2.5 font-extrabold tracking-tight text-tinta select-none',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      {...rest}
    >
      <span
        role={label ? 'img' : undefined}
        aria-label={label}
        aria-hidden={label ? undefined : true}
        className={[
          'inline-flex shrink-0 items-center justify-center bg-tomate text-fundo',
          dims.box,
        ].join(' ')}
      >
        <svg
          width={dims.svg}
          height={dims.svg}
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z" />
        </svg>
      </span>
      {showWordmark && <span className={dims.text}>Nosso Teto</span>}
    </span>
  );
});
