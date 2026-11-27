import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { forwardRef, type ReactNode } from 'react';

export interface PanelProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: string;
  children: ReactNode;
  footer?: ReactNode;
  className?: string;
}

/**
 * Painel lateral que entra da direita no desktop e folha que sobe de baixo
 * no celular, sobre `@radix-ui/react-dialog` (foco preso, `Esc` fecha).
 */
export const Panel = forwardRef<HTMLDivElement, PanelProps>(function Panel(
  { open, onOpenChange, title, description, children, footer, className },
  ref,
) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="nt-overlay fixed inset-0 z-50 bg-tinta/45" />
        <DialogPrimitive.Content
          ref={ref}
          {...(description ? {} : { 'aria-describedby': undefined })}
          className={[
            'nt-panel fixed inset-x-0 bottom-0 z-50 flex max-h-[90dvh] flex-col rounded-t-[28px] bg-fundo text-tinta shadow-2xl focus:outline-none',
            'md:inset-y-0 md:left-auto md:right-0 md:max-h-none md:w-[440px] md:rounded-l-[28px] md:rounded-tr-none',
            className,
          ]
            .filter(Boolean)
            .join(' ')}
        >
          <div aria-hidden="true" className="mx-auto mt-2.5 h-1.5 w-10 rounded-full bg-linha md:hidden" />
          <div className="flex items-start justify-between gap-3 px-6 pb-3 pt-4 md:pt-6">
            <div className="flex min-w-0 flex-col gap-1">
              <DialogPrimitive.Title className="text-xl font-extrabold leading-tight">
                {title}
              </DialogPrimitive.Title>
              {description && (
                <DialogPrimitive.Description className="text-[15px] text-texto-suave">
                  {description}
                </DialogPrimitive.Description>
              )}
            </div>
            <DialogPrimitive.Close
              aria-label="Fechar"
              className="nt-press -mr-2 inline-flex size-11 shrink-0 items-center justify-center rounded-full text-tinta hover:bg-superficie cursor-pointer"
            >
              <X aria-hidden="true" className="size-5" />
            </DialogPrimitive.Close>
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-6">{children}</div>
          {footer && <div className="border-t border-linha px-6 py-4">{footer}</div>}
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
});
