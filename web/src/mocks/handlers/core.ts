import { http, HttpResponse, type HttpHandler } from 'msw';
import { CLIENT_ID } from '../../auth/apiClient';
import { PERMISSIONS } from '../../api/me';
import { db, findMockUser, type MockUser } from '../db';

const REFRESH_COOKIE = 'refresh_token';

const oauthError = (error: string, status: number) =>
  HttpResponse.json({ error }, { status });

const apiError = (error: string, status: number) =>
  HttpResponse.json({ error }, { status });

function issueTokens(userId: string) {
  const session = db.session;
  session.tokenSeq += 1;
  session.userId = userId;
  session.refreshValid = true;
  session.accessToken = `mock-access-${session.tokenSeq}`;
  session.refreshToken = `mock-refresh-${session.tokenSeq}`;
  return HttpResponse.json(
    {
      access_token: session.accessToken,
      token_type: 'bearer',
      expires_in: 900,
      scope: 'read write',
      user_id: userId,
    },
    {
      headers: {
        'Set-Cookie': `${REFRESH_COOKIE}=${session.refreshToken}; Path=/oauth/token; SameSite=Strict`,
      },
    },
  );
}

function revokeFamily() {
  db.session.refreshValid = false;
  db.session.refreshToken = null;
  db.session.accessToken = null;
}

/** Usuário dono do Bearer atual, ou a resposta de erro 401/403. */
function authenticate(
  request: Request,
  permission?: string,
): MockUser | Response {
  const auth = request.headers.get('Authorization');
  const user = findMockUser(db.session.userId);
  if (!db.session.accessToken || auth !== `Bearer ${db.session.accessToken}` || !user) {
    return apiError('Authentication required', 401);
  }
  if (permission && !user.permissions.includes(permission)) {
    return apiError('Insufficient permission', 403);
  }
  return user;
}

function toMe(user: MockUser) {
  const { password: _password, ...me } = user;
  void _password;
  return me;
}

/** `'true'`/`'false'` viram booleano; ausente (ou outro valor) não filtra. */
function booleanParam(value: string | null): boolean | null {
  if (value === 'true') return true;
  if (value === 'false') return false;
  return null;
}

function houseFor(viewer: MockUser, houseId: string) {
  const house = db.houses.find((h) => h.id === houseId);
  if (!house) return undefined;
  const isMember = viewer.houseId === house.id;
  return {
    ...house,
    compatibilityScore: viewer.profileComplete ? house.compatibilityScore : null,
    // Privacidade: e-mail dos moradores só para quem mora na casa.
    members: house.members.map(({ email, ...member }) =>
      isMember ? { ...member, email } : member,
    ),
  };
}

export const handlers: HttpHandler[] = [
  http.post('/oauth/token', async ({ request, cookies }) => {
    // B1: client público, parâmetros só no corpo, nunca Authorization.
    if (request.headers.has('Authorization')) {
      return oauthError('invalid_client', 401);
    }
    const params = new URLSearchParams(await request.text());
    if (params.get('client_id') !== CLIENT_ID) {
      return oauthError('invalid_client', 401);
    }

    const grantType = params.get('grant_type');
    if (grantType === 'password') {
      const user = db.users.find(
        (u) => u.email === params.get('username') && u.password === params.get('password'),
      );
      if (!user) return oauthError('invalid_grant', 400);
      return issueTokens(user.id);
    }

    if (grantType === 'refresh_token') {
      db.session.refreshCalls += 1;
      if (!db.session.refreshValid || !db.session.userId) {
        return oauthError('invalid_grant', 400);
      }
      // Reuso de refresh já rotacionado (quando o cookie chega, no browser):
      // revoga a família inteira, como o RefreshTokenService.
      const presented = cookies[REFRESH_COOKIE];
      if (presented && db.session.refreshToken && presented !== db.session.refreshToken) {
        revokeFamily();
        return oauthError('invalid_grant', 400);
      }
      return issueTokens(db.session.userId);
    }

    return oauthError('unsupported_grant_type', 400);
  }),

  http.delete('/oauth/token/revoke', async ({ request }) => {
    if (request.headers.has('Authorization')) {
      return oauthError('invalid_client', 401);
    }
    const params = new URLSearchParams(await request.text());
    if (params.get('client_id') !== CLIENT_ID) {
      return oauthError('invalid_client', 401);
    }
    revokeFamily();
    return new HttpResponse(null, {
      status: 204,
      headers: { 'Set-Cookie': `${REFRESH_COOKIE}=; Max-Age=0; Path=/oauth/token` },
    });
  }),

  http.get('/api/users/me', ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_PROFILE);
    if (user instanceof Response) return user;
    return HttpResponse.json(toMe(user));
  }),

  http.post('/api/users/me/welcome-ack', ({ request }) => {
    const user = authenticate(request);
    if (user instanceof Response) return user;
    user.pendingWelcome = false;
    return new HttpResponse(null, { status: 204 });
  }),

  http.get('/api/houses', ({ request }) => {
    const user = authenticate(request, PERMISSIONS.SEARCH_HOUSES);
    if (user instanceof Response) return user;
    const url = new URL(request.url);
    const maxRentParam = url.searchParams.get('maxRent');
    const maxRent = maxRentParam ? Number(maxRentParam) : null;
    const allowsPets = booleanParam(url.searchParams.get('allowsPets'));
    const allowsSmokers = booleanParam(url.searchParams.get('allowsSmokers'));
    // Espelha HouseService.listOpenHouses: casa sem aluguel passa no maxRent e
    // os booleanos filtram por igualdade quando o parâmetro vem.
    const list = db.houses
      .filter((h) => h.isAcceptingMatches && h.vacancies > 0)
      .filter((h) => maxRent === null || h.rentAmount == null || h.rentAmount <= maxRent)
      .filter((h) => allowsPets === null || h.allowsPets === allowsPets)
      .filter((h) => allowsSmokers === null || h.allowsSmokers === allowsSmokers)
      .map((h) => houseFor(user, h.id));
    return HttpResponse.json(list);
  }),

  http.get('/api/houses/:id', ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.SEARCH_HOUSES);
    if (user instanceof Response) return user;
    const house = houseFor(user, String(params.id));
    if (!house) return apiError('house not found', 404);
    return HttpResponse.json(house);
  }),
];
