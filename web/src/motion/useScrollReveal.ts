import { useEffect, useState, type RefObject } from 'react';
import { useReducedMotion } from './useReducedMotion';

export interface ScrollRevealState {
  /** `true` quando o elemento já entrou na viewport ou quando o movimento está reduzido. */
  isVisible: boolean;
  /** Classe opcional de entrada suave (vazia em movimento reduzido). */
  className: string;
}

/**
 * Entrada suave ao rolar a página via `IntersectionObserver`.
 * O conteúdo permanece sempre visível por padrão (a animação apenas melhora a
 * chegada) e recebe fallback imediato com `prefers-reduced-motion: reduce`.
 */
export function useScrollReveal(
  ref: RefObject<Element | null>,
  options?: IntersectionObserverInit,
): ScrollRevealState {
  const reducedMotion = useReducedMotion();
  const [entered, setEntered] = useState<boolean>(
    () => reducedMotion || typeof IntersectionObserver === 'undefined',
  );

  const threshold = options?.threshold ?? 0.1;
  const rootMargin = options?.rootMargin ?? '0px';
  const root = options?.root ?? null;

  useEffect(() => {
    if (reducedMotion || typeof IntersectionObserver === 'undefined') {
      setEntered(true);
      return;
    }

    const element = ref.current;
    if (!element) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setEntered(true);
          observer.disconnect();
        }
      },
      { root, rootMargin, threshold },
    );

    observer.observe(element);
    return () => observer.disconnect();
  }, [ref, reducedMotion, root, rootMargin, threshold]);

  const isVisible = reducedMotion || entered;
  const className = !reducedMotion && entered ? 'nt-reveal-enter' : '';

  return { isVisible, className };
}
