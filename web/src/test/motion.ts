/**
 * Polyfills de movimento e observadores para jsdom, com controle determinístico
 * de `prefers-reduced-motion` e `IntersectionObserver` em testes.
 */

type MediaListener = (event: MediaQueryListEvent) => void;

interface MockMediaQueryList extends MediaQueryList {
  _setMatches: (next: boolean) => void;
}

let prefersReducedMotion = false;
const trackedMqls = new Set<MockMediaQueryList>();

function evaluateQuery(query: string): boolean {
  const normalized = query.replace(/\s+/g, ' ').trim().toLowerCase();
  if (normalized.includes('prefers-reduced-motion: reduce')) {
    return prefersReducedMotion;
  }
  if (normalized.includes('prefers-reduced-motion: no-preference')) {
    return !prefersReducedMotion;
  }
  return false;
}

type StoredMediaListener = EventListenerOrEventListenerObject | MediaListener;

function invokeMediaListener(listener: StoredMediaListener, event: MediaQueryListEvent): void {
  if (typeof listener === 'function') {
    listener(event);
  } else {
    listener.handleEvent(event);
  }
}

function createMockMediaQueryList(query: string): MockMediaQueryList {
  let currentMatches = evaluateQuery(query);
  const listeners = new Set<StoredMediaListener>();
  let onChangeHandler: ((this: MediaQueryList, ev: MediaQueryListEvent) => unknown) | null = null;

  const mql: MockMediaQueryList = {
    get matches() {
      return currentMatches;
    },
    media: query,
    get onchange() {
      return onChangeHandler;
    },
    set onchange(handler) {
      onChangeHandler = handler;
    },
    addEventListener(type: string, listener: EventListenerOrEventListenerObject | null) {
      if (type !== 'change' || !listener) return;
      listeners.add(listener);
    },
    removeEventListener(type: string, listener: EventListenerOrEventListenerObject | null) {
      if (type !== 'change' || !listener) return;
      listeners.delete(listener);
    },
    addListener(listener: MediaListener | null) {
      if (listener) listeners.add(listener);
    },
    removeListener(listener: MediaListener | null) {
      if (listener) listeners.delete(listener);
    },
    dispatchEvent(event: Event): boolean {
      const mqlEvent = event as MediaQueryListEvent;
      if (onChangeHandler) onChangeHandler.call(mql, mqlEvent);
      for (const listener of [...listeners]) {
        invokeMediaListener(listener, mqlEvent);
      }
      return true;
    },
    _setMatches(next: boolean) {
      if (currentMatches === next) return;
      currentMatches = next;
      const event = {
        matches: next,
        media: query,
        type: 'change',
      } as MediaQueryListEvent;
      if (onChangeHandler) onChangeHandler.call(mql, event);
      for (const listener of [...listeners]) {
        invokeMediaListener(listener, event);
      }
    },
  };

  trackedMqls.add(mql);
  return mql;
}

export function setPrefersReducedMotion(value: boolean): void {
  prefersReducedMotion = value;
  for (const mql of [...trackedMqls]) {
    mql._setMatches(evaluateQuery(mql.media));
  }
}

export function getPrefersReducedMotion(): boolean {
  return prefersReducedMotion;
}

interface ObserverRecord {
  instance: MockIntersectionObserver;
  callback: IntersectionObserverCallback;
  targets: Set<Element>;
}

const intersectionObservers = new Set<ObserverRecord>();

class MockIntersectionObserver implements IntersectionObserver {
  readonly root: Element | Document | null;
  readonly rootMargin: string;
  readonly thresholds: ReadonlyArray<number>;
  private readonly record: ObserverRecord;

  constructor(callback: IntersectionObserverCallback, options: IntersectionObserverInit = {}) {
    this.root = options.root ?? null;
    this.rootMargin = options.rootMargin ?? '0px';
    const threshold = options.threshold ?? 0;
    this.thresholds = Array.isArray(threshold) ? threshold : [threshold];
    this.record = {
      instance: this,
      callback,
      targets: new Set<Element>(),
    };
    intersectionObservers.add(this.record);
  }

  observe(target: Element): void {
    this.record.targets.add(target);
  }

  unobserve(target: Element): void {
    this.record.targets.delete(target);
  }

  disconnect(): void {
    this.record.targets.clear();
    intersectionObservers.delete(this.record);
  }

  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
}

class MockResizeObserver implements ResizeObserver {
  observe(): void {
    // no-op em jsdom
  }

  unobserve(): void {
    // no-op em jsdom
  }

  disconnect(): void {
    // no-op em jsdom
  }
}

function createEmptyDomRect(): DOMRectReadOnly {
  return {
    x: 0,
    y: 0,
    width: 0,
    height: 0,
    top: 0,
    right: 0,
    bottom: 0,
    left: 0,
    toJSON() {
      return {};
    },
  };
}

/**
 * Dispara uma interseção sintética para todos os `IntersectionObserver`
 * observando `target` (ou todos os alvos se `target` for omitido).
 */
export function triggerIntersection(
  target: Element,
  isIntersecting = true,
  overrides: Partial<IntersectionObserverEntry> = {},
): void {
  const rect = typeof target.getBoundingClientRect === 'function'
    ? target.getBoundingClientRect()
    : createEmptyDomRect();

  const entry: IntersectionObserverEntry = {
    boundingClientRect: rect,
    intersectionRatio: isIntersecting ? 1 : 0,
    intersectionRect: isIntersecting ? rect : createEmptyDomRect(),
    isIntersecting,
    rootBounds: null,
    target,
    time: Date.now(),
    ...overrides,
  };

  for (const record of [...intersectionObservers]) {
    if (record.targets.has(target)) {
      record.callback([entry], record.instance);
    }
  }
}

export function resetMotionTestState(): void {
  prefersReducedMotion = false;
  for (const mql of [...trackedMqls]) {
    mql._setMatches(evaluateQuery(mql.media));
  }
  trackedMqls.clear();
  intersectionObservers.clear();
}

export function installMotionPolyfills(): void {
  if (typeof window !== 'undefined') {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      configurable: true,
      value: (query: string) => createMockMediaQueryList(query),
    });

    Object.defineProperty(window, 'scrollTo', {
      writable: true,
      configurable: true,
      value: () => undefined,
    });
  }

  if (typeof globalThis.ResizeObserver === 'undefined') {
    Object.defineProperty(globalThis, 'ResizeObserver', {
      writable: true,
      configurable: true,
      value: MockResizeObserver,
    });
  }

  Object.defineProperty(globalThis, 'IntersectionObserver', {
    writable: true,
    configurable: true,
    value: MockIntersectionObserver,
  });

  if (typeof Element !== 'undefined') {
    if (!Element.prototype.hasPointerCapture) {
      Element.prototype.hasPointerCapture = () => false;
    }
    if (!Element.prototype.setPointerCapture) {
      Element.prototype.setPointerCapture = () => undefined;
    }
    if (!Element.prototype.releasePointerCapture) {
      Element.prototype.releasePointerCapture = () => undefined;
    }
    if (!Element.prototype.scrollIntoView) {
      Element.prototype.scrollIntoView = () => undefined;
    }
  }
}
