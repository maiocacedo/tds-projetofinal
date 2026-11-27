import { X } from 'lucide-react';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

export type ToastTone = 'neutro' | 'feito' | 'atrasado';

export interface ToastAction {
  label: string;
  onAction: () => void;
}

export interface ToastOptions {
  message: string;
  action?: ToastAction;
  /** Tempo até sumir sozinho (padrão 5000 ms). */
  durationMs?: number;
  tone?: ToastTone;
}

export interface ToastApi {
  /** Mostra um aviso e devolve o id dele. */
  show: (options: ToastOptions) => string;
  dismiss: (id: string) => void;
}

interface ToastEntry extends Required<Pick<ToastOptions, 'message' | 'tone' | 'durationMs'>> {
  id: string;
  action?: ToastAction;
}

export const DEFAULT_TOAST_DURATION_MS = 5000;

const ToastContext = createContext<ToastApi | null>(null);

const TONE_CLASSES: Record<ToastTone, string> = {
  neutro: 'bg-tinta text-fundo',
  feito: 'bg-feito-texto text-fundo',
  atrasado: 'bg-atrasado-texto text-fundo',
};

let toastSeq = 0;

export interface ToastProviderProps {
  children: ReactNode;
  className?: string;
}

/**
 * Avisos em pílula escura no rodapé. A região `aria-live="polite"` fica
 * sempre montada (sem `role`, para não colidir com outros `role="status"` da
 * tela); cada aviso exibido tem `role="status"`.
 */
export function ToastProvider({ children, className }: ToastProviderProps) {
  const [toasts, setToasts] = useState<ToastEntry[]>([]);
  const timersRef = useRef(new Map<string, number>());

  const dismiss = useCallback((id: string) => {
    const timer = timersRef.current.get(id);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      timersRef.current.delete(id);
    }
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback(
    ({ message, action, durationMs = DEFAULT_TOAST_DURATION_MS, tone = 'neutro' }: ToastOptions) => {
      toastSeq += 1;
      const id = `toast-${toastSeq}`;
      setToasts((current) => [...current, { id, message, action, durationMs, tone }]);
      if (Number.isFinite(durationMs) && durationMs > 0) {
        const timer = window.setTimeout(() => dismiss(id), durationMs);
        timersRef.current.set(id, timer);
      }
      return id;
    },
    [dismiss],
  );

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      for (const timer of timers.values()) window.clearTimeout(timer);
      timers.clear();
    };
  }, []);

  const api = useMemo<ToastApi>(() => ({ show, dismiss }), [show, dismiss]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div
        aria-live="polite"
        aria-relevant="additions text"
        className={[
          'pointer-events-none fixed inset-x-0 bottom-20 z-[60] flex flex-col items-center gap-2 px-4 md:bottom-6',
          className,
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role="status"
            className={[
              'nt-toast pointer-events-auto flex min-h-11 max-w-md items-center gap-3 rounded-full py-1 pl-5 pr-1 text-sm font-semibold shadow-lg',
              TONE_CLASSES[toast.tone],
            ].join(' ')}
          >
            <span className="py-1.5">{toast.message}</span>
            {toast.action && (
              <button
                type="button"
                onClick={() => {
                  toast.action?.onAction();
                  dismiss(toast.id);
                }}
                className="nt-press inline-flex min-h-11 items-center rounded-full bg-fundo/15 px-4 font-bold text-fundo hover:bg-fundo/25 cursor-pointer"
              >
                {toast.action.label}
              </button>
            )}
            <button
              type="button"
              aria-label="Fechar aviso"
              onClick={() => dismiss(toast.id)}
              className="nt-press inline-flex size-11 shrink-0 items-center justify-center rounded-full text-fundo/80 hover:bg-fundo/15 hover:text-fundo cursor-pointer"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

/** Acesso aos avisos. Precisa de `<ToastProvider>` acima na árvore. */
// eslint-disable-next-line react-refresh/only-export-components
export function useToast(): ToastApi {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast precisa estar dentro de <ToastProvider>.');
  }
  return context;
}
