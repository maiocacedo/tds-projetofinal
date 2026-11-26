import { http, HttpResponse, type HttpHandler } from 'msw';
import { describe, expect, it } from 'vitest';

interface HandlerModule {
  handlers?: HttpHandler[];
}

/** `/api/houses/:houseId` and `/api/houses/:id` are the same route for MSW. */
function normalizeRoute(method: string, path: HttpHandler['info']['path']): string {
  const raw =
    typeof path === 'string'
      ? path
      : path instanceof RegExp
        ? `regexp:${path.source}`
        : `predicate:${String(path)}`;
  return `${method.toUpperCase()} ${raw.replace(/\/:[a-zA-Z0-9_]+/g, '/:param')}`;
}

function findDuplicateRoutes(
  modules: Record<string, HandlerModule>,
): string[] {
  const owners = new Map<string, string>();
  const duplicates: string[] = [];

  for (const file of Object.keys(modules).sort()) {
    for (const handler of modules[file]?.handlers ?? []) {
      const { method, path } = handler.info;
      const methods = Array.isArray(method) ? method : [method];
      for (const m of methods) {
        const key = normalizeRoute(String(m), path);
        const owner = owners.get(key);
        if (owner !== undefined) {
          duplicates.push(`${key} (${owner} e ${file})`);
        } else {
          owners.set(key, file);
        }
      }
    }
  }

  return duplicates;
}

const ok = () => HttpResponse.json({});

describe('MSW handler registry', () => {
  it('flags the same METHOD + path registered in two files', () => {
    expect(
      findDuplicateRoutes({
        './a.ts': { handlers: [http.get('/api/houses', ok)] },
        './b.ts': { handlers: [http.get('/api/houses', ok)] },
      }),
    ).toEqual(['GET /api/houses (./a.ts e ./b.ts)']);
  });

  it('flags duplicates inside the same file', () => {
    expect(
      findDuplicateRoutes({
        './a.ts': {
          handlers: [http.post('/api/x', ok), http.post('/api/x', ok)],
        },
      }),
    ).toHaveLength(1);
  });

  it('normalizes route params before comparing', () => {
    expect(
      findDuplicateRoutes({
        './a.ts': { handlers: [http.get('/api/houses/:id', ok)] },
        './b.ts': { handlers: [http.get('/api/houses/:houseId', ok)] },
      }),
    ).toHaveLength(1);
  });

  it('allows the same path with different methods', () => {
    expect(
      findDuplicateRoutes({
        './a.ts': { handlers: [http.get('/api/x', ok)] },
        './b.ts': { handlers: [http.post('/api/x', ok)] },
      }),
    ).toEqual([]);
  });

  const realModules = import.meta.glob<HandlerModule>(
    ['./*.ts', '!./index.ts', '!./*.test.ts'],
    { eager: true },
  );

  it('registers the core routes', () => {
    const keys = Object.values(realModules).flatMap((mod) =>
      (mod.handlers ?? []).map((h) =>
        normalizeRoute(String(h.info.method), h.info.path),
      ),
    );
    expect(keys).toEqual(
      expect.arrayContaining([
        'POST /oauth/token',
        'DELETE /oauth/token/revoke',
        'GET /api/users/me',
        'POST /api/users/me/welcome-ack',
        'GET /api/houses',
        'GET /api/houses/:param',
      ]),
    );
  });

  it('has no duplicated route across the real handler files', () => {
    expect(findDuplicateRoutes(realModules)).toEqual([]);
  });
});
