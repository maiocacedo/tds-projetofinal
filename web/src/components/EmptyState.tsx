import { forwardRef, type ReactNode } from 'react';

export interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description?: string;
  action?: ReactNode;
  className?: string;
}

/** Estado vazio de lista/tela: ícone, frase curta e uma ação opcional. */
export const EmptyState = forwardRef<HTMLDivElement, EmptyStateProps>(function EmptyState(
  { icon, title, description, action, className },
  ref,
) {
  return (
    <div
      ref={ref}
      className={[
        'flex flex-col items-center gap-3 rounded-[28px] bg-superficie px-6 py-10 text-center',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      {icon && (
        <span
          aria-hidden="true"
          className="flex size-14 items-center justify-center rounded-full bg-fundo text-tomate [&_svg]:size-7"
        >
          {icon}
        </span>
      )}
      <p className="text-lg font-extrabold text-tinta">{title}</p>
      {description && <p className="max-w-[42ch] text-[15px] text-texto-suave">{description}</p>}
      {action && <div className="mt-1">{action}</div>}
    </div>
  );
});
