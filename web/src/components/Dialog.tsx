import * as DialogPrimitive from '@radix-ui/react-dialog';
import { forwardRef, type ReactNode } from 'react';
import { Button } from './Button';

export interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description?: ReactNode;
  /** Rótulo do botão de confirmação (padrão "Confirmar"). */
  confirmLabel?: string;
  /** Rótulo do botão de cancelar (padrão "Cancelar"). */
  cancelLabel?: string;
  /**
   * Ação de confirmação. Se resolver, o diálogo fecha; se rejeitar, continua
   * aberto (quem chama mostra o erro). Sem `onConfirm`, confirmar só fecha.
   */
  onConfirm?: () => void | Promise<void>;
  tone?: 'primary' | 'danger';
  loading?: boolean;
  children?: ReactNode;
  className?: string;
}

/**
 * Diálogo central de confirmação sobre `@radix-ui/react-dialog`: foco preso,
 * fecha com `Esc`, clique fora ou "Cancelar".
 */
export const Dialog = forwardRef<HTMLDivElement, DialogProps>(function Dialog(
  {
    open,
    onOpenChange,
    title,
    description,
    confirmLabel = 'Confirmar',
    cancelLabel = 'Cancelar',
    onConfirm,
    tone = 'primary',
    loading = false,
    children,
    className,
  },
  ref,
) {
  const handleConfirm = async () => {
    if (loading) return;
    try {
      await onConfirm?.();
    } catch {
      // Mantém aberto: o erro é exibido por quem chamou.
      return;
    }
    onOpenChange(false);
  };

  return (
    <DialogPrimitive.Root
      open={open}
      onOpenChange={(next) => {
        if (loading && !next) return;
        onOpenChange(next);
      }}
    >
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="nt-overlay fixed inset-0 z-50 bg-tinta/45" />
        <DialogPrimitive.Content
          ref={ref}
          {...(description ? {} : { 'aria-describedby': undefined })}
          className={[
            'nt-dialog fixed left-1/2 top-1/2 z-50 flex w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 flex-col gap-4 rounded-[28px] bg-fundo p-6 text-tinta shadow-2xl focus:outline-none',
            className,
          ]
            .filter(Boolean)
            .join(' ')}
        >
          <div className="flex flex-col gap-2">
            <DialogPrimitive.Title className="text-xl font-extrabold leading-tight">
              {title}
            </DialogPrimitive.Title>
            {description && (
              <DialogPrimitive.Description className="text-[15px] text-texto-suave">
                {description}
              </DialogPrimitive.Description>
            )}
          </div>
          {children}
          <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
            <DialogPrimitive.Close asChild>
              <Button variant="secondary" disabled={loading}>
                {cancelLabel}
              </Button>
            </DialogPrimitive.Close>
            <Button
              variant={tone === 'danger' ? 'danger' : 'primary'}
              loading={loading}
              onClick={() => void handleConfirm()}
            >
              {confirmLabel}
            </Button>
          </div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  );
});
