import { http, HttpResponse, type HttpHandler } from 'msw';
import { PERMISSIONS, type UserProfile } from '../../api/me';
import type { PointHistoryItem } from '../../api/profile';
import {
  db,
  findMockUser,
  MOCK_MORADOR_ID,
  USER_PERMISSIONS,
  type MockUser,
} from '../db';

interface ResetTokenRecord {
  token: string;
  userId: string;
  expiresAt: number;
  used: boolean;
}

const MAX_EMAIL_LENGTH = 254;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/;
const TOKEN_TTL_MS = 15 * 60 * 1000;

const INITIAL_MORADOR_HISTORY: PointHistoryItem[] = [
  {
    id: 'pts-1',
    reason: 'CHORE_ON_TIME',
    amount: 20,
    createdAt: '2026-11-24T20:00:00',
    description: 'Lavar a área de serviço na semana',
  },
  {
    id: 'pts-2',
    reason: 'EXPENSE_ON_TIME',
    amount: 10,
    createdAt: '2026-11-18T14:30:00',
    description: 'Internet de novembro paga até o vencimento',
  },
  {
    id: 'pts-3',
    reason: 'GROCERY_BOUGHT',
    amount: 5,
    createdAt: '2026-11-12T18:15:00',
    description: 'Café 500 g',
  },
  {
    id: 'pts-4',
    reason: 'CHORE_ON_TIME',
    amount: 20,
    createdAt: '2026-10-27T21:00:00',
    description: 'Tirar o lixo reciclável',
  },
];

let userSeq = 0;
let tokenSeq = 0;
let demoPasswordResetEnabled = true;
const resetTokens = new Map<string, ResetTokenRecord>();
const pointsHistoryByUser = new Map<string, PointHistoryItem[]>([
  [MOCK_MORADOR_ID, structuredClone(INITIAL_MORADOR_HISTORY)],
]);

export function setDemoPasswordResetEnabled(enabled: boolean): void {
  demoPasswordResetEnabled = enabled;
}

export function seedPointsHistory(
  userId: string,
  history: PointHistoryItem[],
): void {
  pointsHistoryByUser.set(userId, structuredClone(history));
}

export function resetProfileMocks(): void {
  userSeq = 0;
  tokenSeq = 0;
  demoPasswordResetEnabled = true;
  resetTokens.clear();
  pointsHistoryByUser.clear();
  pointsHistoryByUser.set(
    MOCK_MORADOR_ID,
    structuredClone(INITIAL_MORADOR_HISTORY),
  );
}

const apiError = (error: string, status: number) =>
  HttpResponse.json({ error }, { status });

function authenticate(request: Request, permission: string): MockUser | Response {
  const auth = request.headers.get('Authorization');
  const user = findMockUser(db.session.userId);
  if (
    !db.session.accessToken ||
    auth !== `Bearer ${db.session.accessToken}` ||
    !user
  ) {
    return apiError('Authentication required', 401);
  }
  if (!user.permissions.includes(permission)) {
    return apiError('Insufficient permission', 403);
  }
  return user;
}

function nowLocalIso(): string {
  return new Date().toISOString().slice(0, 19);
}

function isProfileComplete(profile: UserProfile): boolean {
  return (
    profile.budget != null &&
    profile.hasPets != null &&
    profile.isSmoker != null &&
    profile.organizationLevel != null &&
    profile.visitorTolerance != null
  );
}

export const handlers: HttpHandler[] = [
  http.post('/api/auth/register', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as {
      name?: unknown;
      email?: unknown;
      password?: unknown;
    };

    const name = typeof body.name === 'string' ? body.name.trim() : '';
    if (!name) {
      return apiError('name is required', 400);
    }

    const email = typeof body.email === 'string' ? body.email.trim() : '';
    if (
      !email ||
      email.length > MAX_EMAIL_LENGTH ||
      !EMAIL_PATTERN.test(email)
    ) {
      return apiError('invalid email', 400);
    }

    const password = typeof body.password === 'string' ? body.password : '';
    if (password.length < 8) {
      return apiError('password must have at least 8 characters', 400);
    }

    const exists = db.users.some(
      (u) => u.email.toLowerCase() === email.toLowerCase(),
    );
    if (exists) {
      return apiError('Email already exists', 400);
    }

    userSeq += 1;
    const created: MockUser = {
      id: `user-reg-${userSeq}`,
      name,
      email,
      password,
      houseId: null,
      houseName: null,
      isHouseAdmin: null,
      profile: null,
      profileComplete: false,
      permissions: [...USER_PERMISSIONS],
      points: { total: 0, thisMonth: 0 },
      residenceMonths: null,
      pendingWelcome: false,
    };
    db.users.push(created);

    return HttpResponse.json(
      {
        id: created.id,
        name: created.name,
        email: created.email,
        createdAt: nowLocalIso(),
      },
      { status: 201 },
    );
  }),

  http.post('/api/auth/password-reset', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as {
      email?: unknown;
    };
    const email = typeof body.email === 'string' ? body.email.trim() : '';
    if (!email) {
      return HttpResponse.json({}, { status: 202 });
    }

    const user = db.users.find(
      (u) => u.email.toLowerCase() === email.toLowerCase(),
    );
    if (!user) {
      return HttpResponse.json({}, { status: 202 });
    }

    tokenSeq += 1;
    const token = `demo-reset-${tokenSeq}`;
    resetTokens.set(token, {
      token,
      userId: user.id,
      expiresAt: Date.now() + TOKEN_TTL_MS,
      used: false,
    });

    if (!demoPasswordResetEnabled) {
      return HttpResponse.json({}, { status: 202 });
    }
    return HttpResponse.json({ demoToken: token }, { status: 202 });
  }),

  http.post('/api/auth/password-reset/confirm', async ({ request }) => {
    const body = (await request.json().catch(() => ({}))) as {
      token?: unknown;
      newPassword?: unknown;
    };

    const newPassword =
      typeof body.newPassword === 'string' ? body.newPassword : '';
    if (newPassword.length < 8) {
      return apiError('password must have at least 8 characters', 400);
    }

    const token = typeof body.token === 'string' ? body.token.trim() : '';
    const record = token ? resetTokens.get(token) : undefined;
    if (!record || record.used || record.expiresAt <= Date.now()) {
      return apiError('invalid or expired token', 400);
    }

    const user = findMockUser(record.userId);
    if (!user) {
      return apiError('invalid or expired token', 400);
    }

    for (const entry of resetTokens.values()) {
      if (entry.userId === user.id) {
        entry.used = true;
      }
    }
    user.password = newPassword;

    if (db.session.userId === user.id) {
      db.session.refreshValid = false;
      db.session.refreshToken = null;
    }

    return new HttpResponse(null, { status: 204 });
  }),

  http.put('/api/users/me/profile', async ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_PROFILE);
    if (user instanceof Response) return user;

    const body = (await request.json().catch(() => ({}))) as UserProfile;

    for (const [key, value] of [
      ['organizationLevel', body.organizationLevel],
      ['visitorTolerance', body.visitorTolerance],
    ] as const) {
      if (value != null && (value < 1 || value > 5)) {
        return apiError(`${key} must be between 1 and 5`, 400);
      }
    }

    const prev = user.profile ?? {};
    const nextProfile: UserProfile = {
      budget: body.budget ?? null,
      hasPets: body.hasPets !== undefined ? body.hasPets : (prev.hasPets ?? null),
      isSmoker:
        body.isSmoker !== undefined ? body.isSmoker : (prev.isSmoker ?? null),
      profession: body.profession ?? null,
      bio: body.bio ?? null,
      allergies: body.allergies ?? null,
      organizationLevel: body.organizationLevel ?? null,
      visitorTolerance: body.visitorTolerance ?? null,
      hasFurniture:
        body.hasFurniture !== undefined
          ? body.hasFurniture
          : (prev.hasFurniture ?? null),
    };

    user.profile = nextProfile;
    user.profileComplete = isProfileComplete(nextProfile);

    return HttpResponse.json(nextProfile);
  }),

  http.get('/api/users/me/points', ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_PROFILE);
    if (user instanceof Response) return user;

    const history = pointsHistoryByUser.get(user.id) ?? [];
    return HttpResponse.json({
      total: user.points.total,
      thisMonth: user.points.thisMonth,
      history,
    });
  }),
];
