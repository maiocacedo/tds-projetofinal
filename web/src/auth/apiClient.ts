import type { ZodType, ZodTypeDef } from 'zod';
import { translateApiError } from '../api/errors';
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore';

/** Client OAuth público (B1): `client_id` no corpo, sem `Authorization: Basic`. */
export const CLIENT_ID = 'nosso-teto-web';

const TOKEN_URL = '/oauth/token';
const REVOKE_URL = '/oauth/token/revoke';
const FORM_HEADERS = { 'Content-Type': 'application/x-www-form-urlencoded' };

export interface TokenResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  scope: string;
  user_id: string;
}

export class ApiError extends Error {
  readonly status: number;
  /** `body.error` da API (ex.: `invalid_grant`, `"Insufficient permission"`). */
  readonly code: string;
  readonly description: string | null;

  constructor(status: number, code: string, description: string | null = null) {
    super(translateApiError(code));
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.description = description;
  }
}

async function toApiError(response: Response): Promise<ApiError> {
  let code = `http_${response.status}`;
  let description: string | null = null;
  try {
    const body: unknown = await response.json();
    if (body && typeof body === 'object') {
      const { error, error_description } = body as Record<string, unknown>;
      if (typeof error === 'string') code = error;
      if (typeof error_description === 'string') description = error_description;
    }
  } catch {
    // corpo vazio ou não-JSON: mantém o código HTTP
  }
  return new ApiError(response.status, code, description);
}

/**
 * Login ou checagem que terminou depois de outro login/logout/expiração: a
 * resposta é descartada sem gravar token nem estado.
 */
export class SessionSupersededError extends Error {
  constructor() {
    super('Sessão substituída por outro login ou logout.');
    this.name = 'SessionSupersededError';
  }
}

// ---------------------------------------------------------------------------
// Sessão expirada: o SessionContext escuta para virar `anonymous`.
// ---------------------------------------------------------------------------

type Listener = () => void;
const sessionExpiredListeners = new Set<Listener>();

export function onSessionExpired(listener: Listener): () => void {
  sessionExpiredListeners.add(listener);
  return () => {
    sessionExpiredListeners.delete(listener);
  };
}

function expireSession(): void {
  // Requisições irmãs em voo da sessão expirada não disparam novo refresh.
  invalidateSessionEpoch();
  clearAccessToken();
  for (const listener of [...sessionExpiredListeners]) listener();
}

// ---------------------------------------------------------------------------
// Refresh single-flight.
//
// O backend (RefreshTokenService.rotate) revoga a família inteira se receber
// dois refresh concorrentes com o mesmo cookie. Por isso boot (montado 2× no
// StrictMode), 401 e refreshMe compartilham UMA promise em andamento.
// Fora de escopo: várias abas abertas fazendo refresh ao mesmo tempo.
// ---------------------------------------------------------------------------

let refreshInFlight: Promise<string | null> | null = null;

/**
 * Época da sessão: muda a cada login, logout e reset. Resposta de refresh que
 * chega de uma época anterior é descartada (não grava token nem expira a
 * sessão nova).
 */
let sessionEpoch = 0;

/**
 * Encerra a época atual: refresh em voo vira resposta velha e a próxima
 * chamada a `refreshAccessToken` abre uma promise nova.
 */
export function invalidateSessionEpoch(): void {
  sessionEpoch += 1;
  refreshInFlight = null;
}

/** Token do refresh, ou `null` em falha de rede, não-2xx ou 200 malformado. */
async function fetchRefreshedToken(): Promise<string | null> {
  try {
    const response = await fetch(TOKEN_URL, {
      method: 'POST',
      headers: FORM_HEADERS,
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: CLIENT_ID,
      }).toString(),
      credentials: 'include',
    });
    // 400 invalid_grant (sem cookie / revogado) ou 401 invalid_client: qualquer
    // não-2xx limpa a sessão, sem retry.
    if (!response.ok) return null;
    const body: unknown = await response.json();
    const token = (body as Partial<TokenResponse> | null)?.access_token;
    return typeof token === 'string' && token.length > 0 ? token : null;
  } catch {
    return null;
  }
}

async function doRefresh(epoch: number): Promise<string | null> {
  const token = await fetchRefreshedToken();
  if (epoch !== sessionEpoch) return null;
  if (!token) {
    expireSession();
    return null;
  }
  setAccessToken(token);
  return token;
}

/** Usa raw `fetch` (nunca `request`) para não recursar num refresh falho. */
export function refreshAccessToken(): Promise<string | null> {
  if (!refreshInFlight) {
    const current: Promise<string | null> = doRefresh(sessionEpoch).finally(() => {
      if (refreshInFlight === current) refreshInFlight = null;
    });
    refreshInFlight = current;
  }
  return refreshInFlight;
}

/** Só para testes: zera promise em andamento, época e listeners. */
export function resetAuthClientState(): void {
  invalidateSessionEpoch();
  sessionExpiredListeners.clear();
}

// ---------------------------------------------------------------------------
// Login / logout (OAuth)
// ---------------------------------------------------------------------------

export async function loginWithPassword(
  username: string,
  password: string,
): Promise<TokenResponse> {
  // Nova sessão: um refresh de boot ainda em voo não pode sobrescrevê-la.
  invalidateSessionEpoch();
  const epoch = sessionEpoch;
  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: FORM_HEADERS,
    body: new URLSearchParams({
      grant_type: 'password',
      client_id: CLIENT_ID,
      username,
      password,
    }).toString(),
    credentials: 'include',
  });
  if (!response.ok) throw await toApiError(response);
  let body: unknown = null;
  try {
    body = await response.json();
  } catch {
    // corpo não-JSON: tratado como 200 malformado abaixo
  }
  const token = (body as Partial<TokenResponse> | null)?.access_token;
  if (typeof token !== 'string' || token.length === 0) {
    throw new ApiError(response.status, 'invalid_token_response');
  }
  // Logout ou outro login avançou a época enquanto este voava: descarta.
  if (epoch !== sessionEpoch) throw new SessionSupersededError();
  setAccessToken(token);
  return body as TokenResponse;
}

export async function revokeSession(): Promise<void> {
  const response = await fetch(REVOKE_URL, {
    method: 'DELETE',
    headers: FORM_HEADERS,
    body: new URLSearchParams({ client_id: CLIENT_ID }).toString(),
    credentials: 'include',
  });
  if (!response.ok) throw await toApiError(response);
}

// ---------------------------------------------------------------------------
// request<T>
// ---------------------------------------------------------------------------

export interface RequestOptions<T> extends Omit<RequestInit, 'body'> {
  /** Serializado como JSON. */
  json?: unknown;
  body?: BodyInit | null;
  /** Valida a resposta (Zod sem `.strict()`). */
  schema?: ZodType<T, ZodTypeDef, unknown>;
}

function send(path: string, options: RequestOptions<unknown>, token: string | null) {
  const { json, schema: _schema, headers: initHeaders, ...init } = options;
  void _schema;
  const headers = new Headers(initHeaders);
  if (!headers.has('Accept')) headers.set('Accept', 'application/json');
  let body = init.body;
  if (json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(json);
  }
  if (token) headers.set('Authorization', `Bearer ${token}`);
  return fetch(path, { ...init, body, headers, credentials: 'include' });
}

function isApiPath(path: string): boolean {
  return path.startsWith('/api/');
}

async function parseBody<T>(
  response: Response,
  schema: RequestOptions<T>['schema'],
): Promise<T> {
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  const data: unknown = text ? JSON.parse(text) : undefined;
  return schema ? schema.parse(data) : (data as T);
}

export async function request<T = unknown>(
  path: string,
  options: RequestOptions<T> = {},
): Promise<T> {
  const epoch = sessionEpoch;
  const sentToken = getAccessToken();
  let response = await send(path, options, sentToken);

  // Refresh automático só para /api/** (nunca /oauth/**), no máximo 1 retry.
  // 403 nunca entra aqui.
  if (response.status === 401 && isApiPath(path)) {
    // Sessão encerrada/trocada enquanto esta voava: sem refresh nem replay com
    // o token de outra sessão.
    if (epoch !== sessionEpoch) throw await toApiError(response);
    const current = getAccessToken();
    // Outra requisição já renovou o token enquanto esta voava: só repete.
    const nextToken =
      current && current !== sentToken ? current : await refreshAccessToken();
    if (!nextToken) throw await toApiError(response);
    if (epoch !== sessionEpoch) throw await toApiError(response);

    response = await send(path, options, nextToken);
    if (response.status === 401) {
      const error = await toApiError(response);
      // 401 de uma época anterior não derruba a sessão que veio depois.
      if (epoch === sessionEpoch) expireSession();
      throw error;
    }
  }

  if (!response.ok) throw await toApiError(response);
  return parseBody(response, options.schema);
}
