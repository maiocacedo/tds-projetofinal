import { http, HttpResponse, type HttpHandler } from 'msw';
import {
  explainCompatibility,
  type CreateHouseInput,
  type MyMatchRequest,
} from '../../api/candidate';
import type { House } from '../../api/houses';
import { PERMISSIONS } from '../../api/me';
import { db, findMockUser, type MockUser } from '../db';

export interface MockMatchRequest extends MyMatchRequest {
  userId: string;
}

const apiError = (error: string, status: number) =>
  HttpResponse.json({ error }, { status });

function authenticate(request: Request, permission?: string): MockUser | Response {
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

let matchRequests: MockMatchRequest[] = [];
let seq = 1;

function nowLocalDateTime(): string {
  return new Date().toISOString().slice(0, 19);
}

export function resetCandidateMocks(): void {
  matchRequests = [];
  seq = 1;
}

export function seedCandidateMatchRequests(items: MockMatchRequest[]): void {
  matchRequests = items.map((item) => ({ ...item }));
}

export function getCandidateMockRequests(): readonly MockMatchRequest[] {
  return matchRequests;
}

// Rotas exclusivas de F5 (NÃO redefinir rotas de core.ts!)
export const handlers: HttpHandler[] = [
  http.post('/api/houses', async ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_HOUSE);
    if (user instanceof Response) return user;

    const body = (await request.json()) as Partial<CreateHouseInput> | null;
    const name = body?.name?.trim() ?? '';
    if (!name) {
      return apiError('House name is required', 400);
    }
    if (user.houseId) {
      return apiError(
        'User already belongs to a house; leave it before creating a new one',
        400,
      );
    }

    const houseId = `house-nova-${seq++}`;
    const newHouse: House = {
      id: houseId,
      name,
      address: body?.address?.trim() || null,
      description: body?.description?.trim() || null,
      basicRules: body?.basicRules?.trim() || null,
      rentAmount: body?.rentAmount ?? null,
      allowsPets: body?.allowsPets ?? true,
      allowsSmokers: body?.allowsSmokers ?? false,
      expectedOrganizationLevel: body?.expectedOrganizationLevel ?? 3,
      visitorTolerance: body?.visitorTolerance ?? 3,
      hasFurniture: body?.hasFurniture ?? false,
      votingRule: body?.votingRule ?? 'UNANIMITY',
      isAcceptingMatches: body?.isAcceptingMatches ?? true,
      compatibilityScore: null,
      vacancies: body?.vacancies ?? 1,
      members: [
        {
          id: user.id,
          name: user.name,
          email: user.email,
          isHouseAdmin: true,
        },
      ],
    };

    db.houses.push(newHouse);
    user.houseId = newHouse.id;
    user.houseName = newHouse.name;
    user.isHouseAdmin = true;

    return HttpResponse.json(newHouse, { status: 201 });
  }),

  http.post('/api/matches/requests', async ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;

    const body = (await request.json()) as { houseId?: string } | null;
    const houseId = body?.houseId?.trim();
    if (!houseId) {
      return apiError('House ID is required', 400);
    }

    const house = db.houses.find((h) => h.id === houseId);
    if (!house) {
      return apiError('house not found', 404);
    }
    if (!house.isAcceptingMatches || house.vacancies <= 0) {
      return apiError('house is not accepting requests', 400);
    }
    if (user.houseId === house.id) {
      return apiError('User already belongs to this house', 400);
    }
    const alreadyPending = matchRequests.some(
      (r) => r.userId === user.id && r.houseId === house.id && r.status === 'PENDING',
    );
    if (alreadyPending) {
      return apiError('Match request already pending for this house', 400);
    }

    const createdAt = nowLocalDateTime();
    const id = `req-${seq++}`;
    const stored: MockMatchRequest = {
      id,
      userId: user.id,
      houseId: house.id,
      houseName: house.name,
      status: 'PENDING',
      createdAt,
      votesCast: 0,
      votersTotal: Math.max(1, house.members.length),
      moveDeadline: null,
    };
    matchRequests.unshift(stored);

    const score = user.profileComplete
      ? explainCompatibility(user.profile, house).score
      : null;

    return HttpResponse.json(
      {
        id,
        userId: user.id,
        userName: user.name,
        houseId: house.id,
        status: 'PENDING',
        compatibilityScore: score,
        createdAt,
      },
      { status: 201 },
    );
  }),

  http.get('/api/matches/requests/mine', ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;

    const list: MyMatchRequest[] = matchRequests
      .filter((r) => r.userId === user.id)
      .map(({ userId: _userId, ...item }) => {
        void _userId;
        return item;
      });
    return HttpResponse.json(list);
  }),

  http.delete('/api/matches/requests/:id', ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;

    const target = matchRequests.find((r) => r.id === String(params.id));
    if (!target) {
      return apiError('match request not found', 404);
    }
    if (target.userId !== user.id) {
      return apiError('only the owner can perform this action', 403);
    }
    if (target.status !== 'PENDING') {
      return apiError('request is not pending', 400);
    }

    target.status = 'WITHDRAWN';
    return new HttpResponse(null, { status: 204 });
  }),

  http.post('/api/matches/requests/cancel-others', ({ request }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;

    let cancelledCount = 0;
    for (const req of matchRequests) {
      if (req.userId === user.id && req.status === 'PENDING') {
        req.status = 'CANCELLED';
        cancelledCount += 1;
      }
    }
    user.pendingWelcome = false;

    return HttpResponse.json({ cancelledCount });
  }),
];
