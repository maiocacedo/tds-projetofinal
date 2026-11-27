import { useCallback, useEffect, useLayoutEffect, useRef, useState, type CSSProperties, type RefObject } from 'react';
import { useReducedMotion } from './useReducedMotion';

export interface JellyIndicatorState {
  ready: boolean;
  style: CSSProperties;
  animationName: string;
}

function findActiveItem(
  container: HTMLElement,
  activeIndexOrKey: number | string | null | undefined,
): HTMLElement | null {
  if (activeIndexOrKey === null || activeIndexOrKey === undefined) return null;

  if (typeof activeIndexOrKey === 'string') {
    const byKey = Array.from(container.querySelectorAll<HTMLElement>('[data-jelly-key]')).find(
      (el) => el.dataset.jellyKey === activeIndexOrKey,
    );
    if (byKey) return byKey;
  }

  if (typeof activeIndexOrKey === 'number' && activeIndexOrKey >= 0) {
    const tagged = container.querySelectorAll<HTMLElement>('[data-jelly-item]');
    if (tagged.length > activeIndexOrKey) {
      return tagged[activeIndexOrKey] ?? null;
    }
    const interactive = container.querySelectorAll<HTMLElement>(
      '[role="radio"], [role="tab"], a, button',
    );
    if (interactive.length > activeIndexOrKey) {
      return interactive[activeIndexOrKey] ?? null;
    }
  }

  return (
    container.querySelector<HTMLElement>('[aria-checked="true"]') ??
    container.querySelector<HTMLElement>('[aria-current="page"]') ??
    null
  );
}

/**
 * Calcula posição e largura do indicador deslizante em geleia a partir do item
 * ativo dentro de `containerRef`. Lida com segurança quando `getBoundingClientRect`
 * retorna 0 (como em jsdom), mantendo o layout visível sem lançar erro.
 */
export function useJellyIndicator(
  containerRef: RefObject<HTMLElement | null>,
  activeIndexOrKey: number | string | null | undefined,
): JellyIndicatorState {
  const reducedMotion = useReducedMotion();
  const [rect, setRect] = useState<{ left: number; width: number; ready: boolean }>({
    left: 0,
    width: 0,
    ready: false,
  });
  const [cycle, setCycle] = useState(0);
  const prevKeyRef = useRef<number | string | null | undefined>(activeIndexOrKey);

  const measure = useCallback(() => {
    const container = containerRef.current;
    if (!container) {
      setRect((prev) => (prev.ready ? { left: 0, width: 0, ready: false } : prev));
      return;
    }

    const activeEl = findActiveItem(container, activeIndexOrKey);
    if (!activeEl) {
      setRect((prev) => (prev.ready ? { left: 0, width: 0, ready: false } : prev));
      return;
    }

    const containerRect = container.getBoundingClientRect();
    const itemRect = activeEl.getBoundingClientRect();

    if (!containerRect.width || !itemRect.width) {
      setRect((prev) => (prev.ready ? { left: 0, width: 0, ready: false } : prev));
      return;
    }

    const nextLeft = itemRect.left - containerRect.left;
    const nextWidth = itemRect.width;

    setRect((prev) =>
      prev.ready && Math.abs(prev.left - nextLeft) < 0.5 && Math.abs(prev.width - nextWidth) < 0.5
        ? prev
        : { left: nextLeft, width: nextWidth, ready: true },
    );
  }, [containerRef, activeIndexOrKey]);

  useLayoutEffect(() => {
    if (prevKeyRef.current !== activeIndexOrKey) {
      prevKeyRef.current = activeIndexOrKey;
      setCycle((c) => c + 1);
    }
    measure();
  }, [activeIndexOrKey, measure]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    let observer: ResizeObserver | null = null;
    if (typeof ResizeObserver !== 'undefined') {
      observer = new ResizeObserver(() => measure());
      observer.observe(container);
    }
    window.addEventListener('resize', measure);

    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, [containerRef, measure]);

  const animationName =
    reducedMotion || cycle === 0 ? 'none' : cycle % 2 === 1 ? 'nt-geleia-a' : 'nt-geleia-b';

  const style: CSSProperties = rect.ready
    ? {
        left: `${rect.left}px`,
        width: `${rect.width}px`,
        opacity: 1,
        animationName,
      }
    : {
        opacity: 0,
      };

  return {
    ready: rect.ready,
    style,
    animationName,
  };
}
