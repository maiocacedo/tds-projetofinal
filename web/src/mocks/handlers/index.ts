import type { HttpHandler } from 'msw';

interface HandlerModule {
  handlers?: HttpHandler[];
}

const modules = import.meta.glob<HandlerModule>(
  ['./*.ts', '!./index.ts', '!./*.test.ts'],
  { eager: true },
);

export const allHandlers: HttpHandler[] = Object.keys(modules)
  .sort()
  .flatMap((path) => modules[path]?.handlers ?? []);
