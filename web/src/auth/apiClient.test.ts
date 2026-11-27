import { act, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { createElement, useState } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchHouses, houseSchema } from '../api/houses';
import { HOUSE_VERDE_ID, db, seedSession } from '../mocks/db';
import { server } from '../mocks/server';
import { renderWithProviders } from '../test/renderWithProviders';
import {
  ApiError,
  CLIENT_ID,
  invalidateSessionEpoch,
  loginWithPassword,
  onSessionExpired,
  refreshAccessToken,
  request,
  revokeSession,
  SessionSupersededError,
} from './apiClient';
import { useSession } from './SessionContext';
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore';

function countRequests(predicate: (req: Request) => boolean) {
  let count = 0;
  const listener = ({ request: req }: { request: Request }) => {
    if (predicate(req)) count += 1;
  };
  server.events.on('request:start', listener);
  return {
    get count() {
      return count;
    },
    stop: () => server.events.removeListener('request:start', listener),
  };
}

const isPath = (method: string, path: string) => (req: Request) =>
  req.method === method && new URL(req.url).pathname === path;

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function tokenJson(accessToken: string) {
  return HttpResponse.json({
    access_token: accessToken,
    token_type: 'bearer',
    expires_in: 900,
    scope: 'read write',
    user_id: db.users[0].id,
  });
}

function meJson() {
  const { password: _password, ...me } = db.users[0];
  void _password;
  return HttpResponse.json(me);
}

let pendingRefreshMe: Promise<unknown> | null = null;

function SessionProbe() {
  const session = useSession();
  const [error, setError] = useState<string | null>(null);
  return createElement(
    'div',
    null,
    createElement('p', { 'data-testid': 'status' }, session.status),
    createElement('p', { 'data-testid': 'user' }, session.user?.name ?? ''),
    createElement(
      'button',
      {
        type: 'button',
        onClick: () => {
          session.login('ana@nossoteto.dev', 'senha-forte-123').catch((err) =>
            setError(String(err)),
          );
        },
      },
      'login',
    ),
    createElement(
      'button',
      { type: 'button', onClick: () => void session.logout() },
      'logout',
    ),
    createElement(
      'button',
      {
        type: 'button',
        onClick: () => {
          pendingRefreshMe = session.refreshMe();
        },
      },
      'refreshMe',
    ),
    createElement('p', { 'data-testid': 'error' }, error ?? ''),
  );
}

afterEach(() => {
  vi.restoreAllMocks();
  pendingRefreshMe = null;
});

describe('tokenStore', () => {
  it('keeps the access token only in memory', () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    setAccessToken('secret-token');
    expect(getAccessToken()).toBe('secret-token');
    clearAccessToken();
    expect(getAccessToken()).toBeNull();
    expect(setItem).not.toHaveBeenCalled();
  });
});

describe('OAuth contract (public client)', () => {
  it('logs in with form-urlencoded body, client_id, no Authorization header and credentials include', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    const token = await loginWithPassword('ana@nossoteto.dev', 'senha-forte-123');

    expect(token.access_token).toBeTruthy();
    expect(token.user_id).toBe(db.users[0].id);
    const [input, init] = fetchSpy.mock.calls[0];
    expect(String(input)).toBe('/oauth/token');
    expect(init?.method).toBe('POST');
    expect(init?.credentials).toBe('include');
    const headers = new Headers(init?.headers);
    expect(headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(headers.has('Authorization')).toBe(false);
    const body = new URLSearchParams(String(init?.body));
    expect(body.get('grant_type')).toBe('password');
    expect(body.get('client_id')).toBe(CLIENT_ID);
    expect(body.get('username')).toBe('ana@nossoteto.dev');
    expect(body.get('password')).toBe('senha-forte-123');
  });

  it('rejects bad credentials with ApiError invalid_grant translated to pt-BR', async () => {
    const error = await loginWithPassword('ana@nossoteto.dev', 'errada').catch(
      (err: unknown) => err,
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 400, code: 'invalid_grant' });
    expect((error as ApiError).message).toMatch(/e-mail ou senha/i);
  });

  it('refreshes with grant_type=refresh_token, client_id in the body and the cookie (credentials include)', async () => {
    seedSession();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    const token = await refreshAccessToken();

    expect(token).toBeTruthy();
    expect(getAccessToken()).toBe(token);
    const [, init] = fetchSpy.mock.calls[0];
    expect(init?.credentials).toBe('include');
    expect(new Headers(init?.headers).has('Authorization')).toBe(false);
    const body = new URLSearchParams(String(init?.body));
    expect(body.get('grant_type')).toBe('refresh_token');
    expect(body.get('client_id')).toBe(CLIENT_ID);
  });

  it('revokes with DELETE, client_id in the body and no Authorization header', async () => {
    seedSession();
    await refreshAccessToken();
    const fetchSpy = vi.spyOn(globalThis, 'fetch');

    await revokeSession();

    const [input, init] = fetchSpy.mock.calls[0];
    expect(String(input)).toBe('/oauth/token/revoke');
    expect(init?.method).toBe('DELETE');
    expect(init?.credentials).toBe('include');
    const headers = new Headers(init?.headers);
    expect(headers.has('Authorization')).toBe(false);
    expect(headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(new URLSearchParams(String(init?.body)).get('client_id')).toBe(CLIENT_ID);
    expect(db.session.refreshValid).toBe(false);
  });
});

describe('request() with single-flight refresh', () => {
  it('sends the in-memory token as Bearer', async () => {
    seedSession();
    await refreshAccessToken();

    const me = await request<{ name: string }>('/api/users/me');

    expect(me.name).toBe(db.users[0].name);
  });

  it('(b) three simultaneous 401s trigger exactly one refresh and retry all three calls', async () => {
    seedSession();
    setAccessToken('stale-token');
    const meCalls = countRequests(isPath('GET', '/api/users/me'));

    const results = await Promise.all([
      request<{ id: string }>('/api/users/me'),
      request<{ id: string }>('/api/users/me'),
      request<{ id: string }>('/api/users/me'),
    ]);

    expect(results.map((r) => r.id)).toEqual([
      db.users[0].id,
      db.users[0].id,
      db.users[0].id,
    ]);
    expect(db.session.refreshCalls).toBe(1);
    expect(meCalls.count).toBe(6);
    meCalls.stop();
  });

  it('(c) refresh answering 400 invalid_grant clears the session without looping', async () => {
    setAccessToken('stale-token');
    db.session.refreshValid = false;
    const expired = vi.fn();
    onSessionExpired(expired);
    const tokenCalls = countRequests(isPath('POST', '/oauth/token'));

    const error = await request('/api/users/me').catch((err: unknown) => err);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect(tokenCalls.count).toBe(1);
    expect(db.session.refreshCalls).toBe(1);
    expect(getAccessToken()).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    tokenCalls.stop();
  });

  it('a second 401 after a successful refresh logs out (at most one retry)', async () => {
    seedSession();
    await refreshAccessToken();
    server.use(
      http.get('/api/houses', () =>
        HttpResponse.json({ error: 'Authentication required' }, { status: 401 }),
      ),
    );
    const expired = vi.fn();
    onSessionExpired(expired);
    const housesCalls = countRequests(isPath('GET', '/api/houses'));

    const error = await request('/api/houses').catch((err: unknown) => err);

    expect(error).toMatchObject({ status: 401 });
    expect(housesCalls.count).toBe(2);
    expect(db.session.refreshCalls).toBe(2);
    expect(getAccessToken()).toBeNull();
    expect(expired).toHaveBeenCalledTimes(1);
    housesCalls.stop();
  });

  it('never refreshes on a 401 from /oauth/token itself', async () => {
    server.use(
      http.post('/oauth/token', () =>
        HttpResponse.json({ error: 'invalid_client' }, { status: 401 }),
      ),
    );
    const tokenCalls = countRequests(isPath('POST', '/oauth/token'));

    const error = await loginWithPassword('ana@nossoteto.dev', 'x').catch(
      (err: unknown) => err,
    );

    expect(error).toMatchObject({ status: 401, code: 'invalid_client' });
    expect(tokenCalls.count).toBe(1);
    tokenCalls.stop();
  });

  it('(d) 403 never triggers refresh nor logout', async () => {
    seedSession();
    await refreshAccessToken();
    const token = getAccessToken();
    server.use(
      http.get('/api/houses', () =>
        HttpResponse.json({ error: 'Insufficient permission' }, { status: 403 }),
      ),
    );
    const expired = vi.fn();
    onSessionExpired(expired);

    const error = await request('/api/houses').catch((err: unknown) => err);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 403, code: 'Insufficient permission' });
    expect((error as ApiError).message).toMatch(/permissão/i);
    expect(db.session.refreshCalls).toBe(1);
    expect(getAccessToken()).toBe(token);
    expect(expired).not.toHaveBeenCalled();
  });
});

describe('SessionProvider', () => {
  it('(a) StrictMode double mount fires exactly one refresh on boot', async () => {
    seedSession();

    renderWithProviders(createElement(SessionProbe), { strict: true });

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    expect(screen.getByTestId('user')).toHaveTextContent(db.users[0].name);
    expect(db.session.refreshCalls).toBe(1);
  });

  it('boot without refresh cookie becomes anonymous silently', async () => {
    const consoleError = vi.spyOn(console, 'error');

    renderWithProviders(createElement(SessionProbe), { strict: true });

    expect(screen.getByTestId('status')).toHaveTextContent('loading');
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );
    expect(db.session.refreshCalls).toBe(1);
    expect(consoleError).not.toHaveBeenCalled();
  });

  it('(f) login keeps the token out of localStorage and sessionStorage', async () => {
    const setItem = vi.spyOn(Storage.prototype, 'setItem');
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );

    await user.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    const token = getAccessToken();
    expect(token).toBeTruthy();
    for (const [, value] of setItem.mock.calls) {
      expect(String(value)).not.toContain(token as string);
    }
    // Node >= 25 expõe um localStorage experimental que pode sombrear o do
    // jsdom (fica undefined sem --localstorage-file): varre o que existir.
    const storages = [globalThis.localStorage, globalThis.sessionStorage].filter(
      (storage): storage is Storage => storage != null,
    );
    for (const storage of storages) {
      for (let i = 0; i < storage.length; i += 1) {
        const key = storage.key(i) ?? '';
        expect(key).not.toContain(token as string);
        expect(storage.getItem(key) ?? '').not.toContain(token as string);
      }
    }
  });

  it('(e) logout clears token, user and query cache even when revoke answers 500', async () => {
    seedSession();
    server.use(
      http.delete('/oauth/token/revoke', () =>
        HttpResponse.json({ error: 'server_error' }, { status: 500 }),
      ),
    );
    const user = userEvent.setup();
    const { queryClient } = renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    queryClient.setQueryData(['cached'], { secret: true });

    await user.click(screen.getByRole('button', { name: 'logout' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );
    expect(getAccessToken()).toBeNull();
    expect(screen.getByTestId('user')).toHaveTextContent('');
    expect(queryClient.getQueryData(['cached'])).toBeUndefined();
  });

  it('a forced logout from the client (failed refresh) moves the session to anonymous', async () => {
    seedSession();
    const { queryClient } = renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    queryClient.setQueryData(['cached'], 1);
    db.session.refreshValid = false;
    db.session.accessToken = null;

    await act(async () => {
      await request('/api/users/me').catch(() => undefined);
    });

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );
    expect(queryClient.getQueryData(['cached'])).toBeUndefined();
  });
});

describe('session epoch (stale responses never revive a finished session)', () => {
  it('a refresh in flight that resolves after logout does not store the token nor expire the session', async () => {
    seedSession();
    const gate = deferred();
    server.use(
      http.post('/oauth/token', async () => {
        await gate.promise;
        return tokenJson('late-token');
      }),
    );
    const expired = vi.fn();
    onSessionExpired(expired);

    const stale = refreshAccessToken();
    invalidateSessionEpoch();
    gate.resolve();

    expect(await stale).toBeNull();
    expect(getAccessToken()).toBeNull();
    expect(expired).not.toHaveBeenCalled();
  });

  it('a refresh started while logout revokes does not store a token after logout', async () => {
    seedSession();
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    const revokeGate = deferred();
    const refreshGate = deferred();
    server.use(
      http.delete('/oauth/token/revoke', async () => {
        await revokeGate.promise;
        return new HttpResponse(null, { status: 204 });
      }),
      http.post('/oauth/token', async () => {
        await refreshGate.promise;
        return tokenJson('late-token');
      }),
    );

    await user.click(screen.getByRole('button', { name: 'logout' }));
    const late = refreshAccessToken();
    revokeGate.resolve();
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );
    refreshGate.resolve();

    expect(await late).toBeNull();
    expect(getAccessToken()).toBeNull();
  });

  it('a stale refresh settling does not drop the single-flight promise of the new epoch', async () => {
    const gates: (() => void)[] = [];
    let issued = 0;
    server.use(
      http.post('/oauth/token', async () => {
        issued += 1;
        const token = `token-${issued}`;
        await new Promise<void>((done) => gates.push(done));
        return tokenJson(token);
      }),
    );

    const stale = refreshAccessToken();
    await waitFor(() => expect(gates).toHaveLength(1));
    invalidateSessionEpoch();
    const fresh = refreshAccessToken();
    expect(fresh).not.toBe(stale);
    await waitFor(() => expect(gates).toHaveLength(2));

    gates[0]();
    expect(await stale).toBeNull();
    // A promise da época atual continua sendo a compartilhada.
    expect(refreshAccessToken()).toBe(fresh);

    gates[1]();
    expect(await fresh).toBe('token-2');
    expect(getAccessToken()).toBe('token-2');
  });

  it.each([
    ['non-JSON body', () => new HttpResponse('not json', { status: 200 })],
    ['JSON without access_token', () => HttpResponse.json({ token_type: 'bearer' })],
    ['empty access_token', () => HttpResponse.json({ access_token: '' })],
  ])(
    'a malformed 200 refresh (%s) on boot ends anonymous instead of stuck loading',
    async (_label, reply) => {
      seedSession();
      server.use(http.post('/oauth/token', reply));

      renderWithProviders(createElement(SessionProbe));

      await waitFor(() =>
        expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
      );
      expect(getAccessToken()).toBeNull();
    },
  );

  it('login() whose /me fails clears the token store and rethrows', async () => {
    server.use(
      http.get('/api/users/me', () =>
        HttpResponse.json({ error: 'server_error' }, { status: 500 }),
      ),
    );
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );

    await user.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() => expect(screen.getByTestId('error').textContent).not.toBe(''));
    expect(getAccessToken()).toBeNull();
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
  });

  it('refreshMe() in flight when logout happens does not revive the session', async () => {
    seedSession();
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    const gate = deferred();
    server.use(
      http.get('/api/users/me', async () => {
        await gate.promise;
        return meJson();
      }),
    );

    await user.click(screen.getByRole('button', { name: 'refreshMe' }));
    await user.click(screen.getByRole('button', { name: 'logout' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );
    await act(async () => {
      gate.resolve();
      await pendingRefreshMe;
    });

    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('user')).toHaveTextContent('');
    expect(getAccessToken()).toBeNull();
  });

  it('a boot refresh that resolves after login does not override the logged-in session', async () => {
    seedSession();
    const gate = deferred();
    let refreshes = 0;
    server.use(
      http.post('/oauth/token', async ({ request: req }) => {
        const params = new URLSearchParams(await req.clone().text());
        if (params.get('grant_type') !== 'refresh_token') return undefined;
        refreshes += 1;
        await gate.promise;
        return HttpResponse.json({ error: 'invalid_grant' }, { status: 400 });
      }),
    );
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() => expect(refreshes).toBe(1));

    await user.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    await act(async () => {
      gate.resolve();
      await new Promise((done) => setTimeout(done, 20));
    });

    expect(screen.getByTestId('status')).toHaveTextContent('authenticated');
    expect(getAccessToken()).toBeTruthy();
  });
});

describe('session epoch on 401 and login (no replay across sessions)', () => {
  function unauthorized() {
    return HttpResponse.json({ error: 'Authentication required' }, { status: 401 });
  }

  it.each([
    ['revoke still in flight', 'pending'],
    ['revoke answering 500', 'error'],
  ] as const)(
    'a 401 in flight after logout (%s) neither refreshes, retries nor revives the token',
    async (_label, revokeMode) => {
      seedSession();
      const user = userEvent.setup();
      renderWithProviders(createElement(SessionProbe));
      await waitFor(() =>
        expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
      );
      const housesGate = deferred();
      const revokeGate = deferred();
      server.use(
        http.get('/api/houses', async () => {
          await housesGate.promise;
          return unauthorized();
        }),
        http.delete('/oauth/token/revoke', async () => {
          if (revokeMode === 'pending') await revokeGate.promise;
          return HttpResponse.json({ error: 'server_error' }, { status: 500 });
        }),
      );
      const housesCalls = countRequests(isPath('GET', '/api/houses'));
      const refreshesBefore = db.session.refreshCalls;

      const inflight = request('/api/houses').catch((err: unknown) => err);
      await waitFor(() => expect(housesCalls.count).toBe(1));
      await user.click(screen.getByRole('button', { name: 'logout' }));
      if (revokeMode === 'error') {
        await waitFor(() =>
          expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
        );
      }

      housesGate.resolve();
      const error = await inflight;

      expect(error).toMatchObject({ status: 401 });
      expect(housesCalls.count).toBe(1);
      expect(db.session.refreshCalls).toBe(refreshesBefore);

      revokeGate.resolve();
      await waitFor(() =>
        expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
      );
      expect(getAccessToken()).toBeNull();
      expect(db.session.refreshCalls).toBe(refreshesBefore);
      housesCalls.stop();
    },
  );

  it('a 401 in flight from session A never replays with the token of session B', async () => {
    seedSession();
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    const tokenA = getAccessToken();
    const gate = deferred();
    const sentAuth: (string | null)[] = [];
    server.use(
      http.get('/api/houses', async ({ request: req }) => {
        sentAuth.push(req.headers.get('Authorization'));
        await gate.promise;
        return unauthorized();
      }),
    );

    const inflight = request('/api/houses').catch((err: unknown) => err);
    await waitFor(() => expect(sentAuth).toHaveLength(1));
    await user.click(screen.getByRole('button', { name: 'logout' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );
    await user.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated'),
    );
    const tokenB = getAccessToken();
    expect(tokenB).toBeTruthy();
    expect(tokenB).not.toBe(tokenA);
    const refreshesBefore = db.session.refreshCalls;

    gate.resolve();
    const error = await inflight;

    expect(error).toMatchObject({ status: 401 });
    expect(sentAuth).toEqual([`Bearer ${tokenA}`]);
    expect(db.session.refreshCalls).toBe(refreshesBefore);
    expect(getAccessToken()).toBe(tokenB);
    expect(screen.getByTestId('status')).toHaveTextContent('authenticated');
  });

  it('a sibling 401 in flight after expireSession() does not trigger a new refresh', async () => {
    seedSession();
    await refreshAccessToken();
    const gate = deferred();
    server.use(
      http.get('/api/houses', async () => {
        await gate.promise;
        return unauthorized();
      }),
    );
    const housesCalls = countRequests(isPath('GET', '/api/houses'));
    const expired = vi.fn();
    onSessionExpired(expired);

    const sibling = request('/api/houses').catch((err: unknown) => err);
    await waitFor(() => expect(housesCalls.count).toBe(1));
    // Access e refresh invalidados no servidor: o refresh desta falha e expira.
    db.session.accessToken = null;
    db.session.refreshValid = false;
    const first = await request('/api/users/me').catch((err: unknown) => err);
    expect(first).toMatchObject({ status: 401 });
    expect(db.session.refreshCalls).toBe(2);
    expect(expired).toHaveBeenCalledTimes(1);

    gate.resolve();
    const error = await sibling;

    expect(error).toMatchObject({ status: 401 });
    expect(housesCalls.count).toBe(1);
    expect(db.session.refreshCalls).toBe(2);
    expect(expired).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
    housesCalls.stop();
  });

  it('loginWithPassword in flight superseded by logout does not store the token', async () => {
    const gate = deferred();
    server.use(
      http.post('/oauth/token', async () => {
        await gate.promise;
        return tokenJson('late-login-token');
      }),
    );

    const pending = loginWithPassword('ana@nossoteto.dev', 'senha-forte-123').catch(
      (err: unknown) => err,
    );
    invalidateSessionEpoch();
    gate.resolve();

    expect(await pending).toBeInstanceOf(SessionSupersededError);
    expect(getAccessToken()).toBeNull();
  });

  it('login() superseded by logout() neither stores the token nor calls /me', async () => {
    const gate = deferred();
    server.use(
      http.post('/oauth/token', async ({ request: req }) => {
        const params = new URLSearchParams(await req.clone().text());
        if (params.get('grant_type') !== 'password') return undefined;
        await gate.promise;
        return tokenJson('late-login-token');
      }),
    );
    const meCalls = countRequests(isPath('GET', '/api/users/me'));
    const user = userEvent.setup();
    renderWithProviders(createElement(SessionProbe));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous'),
    );

    await user.click(screen.getByRole('button', { name: 'login' }));
    await user.click(screen.getByRole('button', { name: 'logout' }));
    await act(async () => {
      gate.resolve();
    });

    await waitFor(() =>
      expect(screen.getByTestId('error')).toHaveTextContent(/SessionSupersededError/),
    );
    expect(getAccessToken()).toBeNull();
    expect(meCalls.count).toBe(0);
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('user')).toHaveTextContent('');
    meCalls.stop();
  });

  it.each([
    ['non-JSON body', () => new HttpResponse('not json', { status: 200 })],
    ['JSON without access_token', () => HttpResponse.json({ token_type: 'bearer' })],
    ['empty access_token', () => HttpResponse.json({ access_token: '' })],
    ['non-string access_token', () => HttpResponse.json({ access_token: 42 })],
  ])('a malformed 200 login (%s) rejects without storing a token', async (_label, reply) => {
    server.use(http.post('/oauth/token', reply));

    const error = await loginWithPassword('ana@nossoteto.dev', 'senha-forte-123').catch(
      (err: unknown) => err,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 200, code: 'invalid_token_response' });
    expect(getAccessToken()).toBeNull();
  });
});

describe('houses contract (MSW parity with HouseService)', () => {
  async function loggedIn() {
    seedSession();
    await refreshAccessToken();
  }

  it('allowsPets=false and allowsSmokers=false filter by boolean equality', async () => {
    await loggedIn();

    const noPets = await fetchHouses({ allowsPets: false });
    expect(noPets.length).toBeGreaterThan(0);
    expect(noPets.every((h) => h.allowsPets === false)).toBe(true);

    const noSmokers = await fetchHouses({ allowsSmokers: false });
    expect(noSmokers.length).toBeGreaterThan(0);
    expect(noSmokers.every((h) => h.allowsSmokers === false)).toBe(true);

    const pets = await fetchHouses({ allowsPets: true });
    expect(pets.length).toBeGreaterThan(0);
    expect(pets.every((h) => h.allowsPets === true)).toBe(true);
  });

  it('maxRent keeps houses without rentAmount (null), like the backend', async () => {
    await loggedIn();
    const verde = db.houses.find((h) => h.id === HOUSE_VERDE_ID)!;
    verde.rentAmount = null;

    const houses = await fetchHouses({ maxRent: 100 });

    expect(houses.map((h) => h.id)).toEqual([HOUSE_VERDE_ID]);
    expect(houses[0].rentAmount).toBeNull();
  });

  it('house schema accepts rentAmount and member isHouseAdmin omitted or null', () => {
    const { rentAmount: _rent, members: _members, ...rest } = db.houses[0];
    void _rent;
    void _members;

    const omitted = houseSchema.parse({
      ...rest,
      members: [{ id: 'm1', name: 'Membro' }],
    });
    expect(omitted.rentAmount).toBeNull();
    expect(omitted.members[0].isHouseAdmin).toBeNull();

    const explicit = houseSchema.parse({
      ...rest,
      rentAmount: null,
      members: [{ id: 'm1', name: 'Membro', isHouseAdmin: null }],
    });
    expect(explicit.rentAmount).toBeNull();
    expect(explicit.members[0].isHouseAdmin).toBeNull();
  });
});
