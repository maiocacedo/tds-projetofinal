import { http, HttpResponse, type HttpHandler } from 'msw';
import type { House } from '../../api/houses';
import type { MatchStatus, Vote } from '../../api/houseManagement';
import { PERMISSIONS } from '../../api/me';
import { db, findMockUser, HOUSE_JABUTICABA_ID, type MockUser } from '../db';

// Gestão da casa (F6). Remoções e solicitações vivem aqui, no módulo; casas e
// usuários são lidos de `db` na hora da requisição. `resetHouseManagementMocks()`
// volta ao estado inicial (chamado no beforeEach de `casa.test.tsx`).

const MOVE_WINDOW_MS = 72 * 60 * 60 * 1000;

export interface MockRemoval {
  id: string;
  houseId: string;
  targetUserId: string;
  targetUserName: string;
  proposedById: string;
  proposedByName: string;
  reason: string | null;
  status: 'OPEN' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  createdAt: string;
  votes: Record<string, Vote>;
}

export interface MockMatchCandidate {
  id: string;
  name: string;
  compatibilityScore: number | null;
  points: number;
  residenceMonths: number | null;
}

export interface MockMatchRequest {
  id: string;
  houseId: string;
  status: MatchStatus;
  createdAt: string;
  moveDeadline: string | null;
  candidate: MockMatchCandidate;
  votes: Record<string, Vote>;
}

export interface HouseManagementMockState {
  removals: MockRemoval[];
  matches: MockMatchRequest[];
  seq: number;
}

/** `LocalDateTime` em UTC sem offset, como a API serializa. */
export function toLocalDateTimeUtc(date: Date): string {
  return date.toISOString().slice(0, 19);
}

function seedState(): HouseManagementMockState {
  return {
    removals: [],
    matches: [
      {
        id: 'match-marina',
        houseId: HOUSE_JABUTICABA_ID,
        status: 'PENDING',
        createdAt: '2026-11-20T15:00:00',
        moveDeadline: null,
        candidate: {
          id: 'user-marina',
          name: 'Marina Costa',
          compatibilityScore: 85,
          points: 420,
          residenceMonths: 8,
        },
        votes: { 'user-carla': 'APPROVE' },
      },
      {
        id: 'match-rafael',
        houseId: HOUSE_JABUTICABA_ID,
        status: 'PENDING',
        createdAt: '2026-11-22T10:30:00',
        moveDeadline: null,
        candidate: {
          id: 'user-rafael',
          name: 'Rafael Fontes',
          compatibilityScore: 70,
          points: 0,
          residenceMonths: null,
        },
        votes: {},
      },
    ],
    seq: 0,
  };
}

export const houseManagementMock: HouseManagementMockState = seedState();

export function resetHouseManagementMocks(): void {
  const fresh = seedState();
  houseManagementMock.removals = fresh.removals;
  houseManagementMock.matches = fresh.matches;
  houseManagementMock.seq = fresh.seq;
}

function nextId(prefix: string): string {
  houseManagementMock.seq += 1;
  return `${prefix}-${houseManagementMock.seq}`;
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const apiError = (error: string, status: number) => HttpResponse.json({ error }, { status });
const noContent = () => new HttpResponse(null, { status: 204 });

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

async function readJson(request: Request): Promise<Record<string, unknown>> {
  try {
    const body: unknown = await request.json();
    return body && typeof body === 'object' ? (body as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}

function findHouse(id: string): House | undefined {
  return db.houses.find((house) => house.id === id);
}

function isMember(house: House, userId: string): boolean {
  return house.members.some((member) => member.id === userId);
}

function isAdminOf(house: House, userId: string): boolean {
  return house.members.some((member) => member.id === userId && member.isHouseAdmin === true);
}

function memberName(house: House, userId: string): string {
  return (
    house.members.find((member) => member.id === userId)?.name ??
    findMockUser(userId)?.name ??
    'Morador'
  );
}

function syncUser(userId: string, patch: Partial<MockUser>): void {
  const user = findMockUser(userId);
  if (user) Object.assign(user, patch);
}

function toHouseView(house: House) {
  return { ...house };
}

/** Saída da casa com repasse do admin (mesma regra do `leave`). */
function removeFromHouse(
  house: House,
  userId: string,
  newAdminId: string | undefined,
): Response | null {
  const leaving = house.members.find((member) => member.id === userId);
  if (!leaving) return apiError('user is not a member of this house', 400);
  const others = house.members.filter((member) => member.id !== userId);
  const otherAdmins = others.filter((member) => member.isHouseAdmin === true);

  if (leaving.isHouseAdmin && others.length > 0 && otherAdmins.length === 0) {
    const heir = newAdminId ? others.find((member) => member.id === newAdminId) : undefined;
    if (!heir) return apiError('new admin required', 400);
    heir.isHouseAdmin = true;
    syncUser(heir.id, { isHouseAdmin: true });
  }

  house.members = others;
  syncUser(userId, { houseId: null, houseName: null, isHouseAdmin: null });
  return null;
}

function toRemovalView(removal: MockRemoval, viewerId: string) {
  const house = findHouse(removal.houseId);
  const voters = Object.keys(removal.votes).map((id) => ({
    id,
    name: house ? memberName(house, id) : (findMockUser(id)?.name ?? 'Morador'),
  }));
  const votersTotal = house
    ? house.members.filter((m) => m.id !== removal.targetUserId).length
    : 0;
  return {
    id: removal.id,
    targetUserId: removal.targetUserId,
    targetUserName: removal.targetUserName,
    proposedById: removal.proposedById,
    proposedByName: removal.proposedByName,
    reason: removal.reason,
    status: removal.status,
    createdAt: removal.createdAt,
    voters,
    myVote: removal.votes[viewerId] ?? null,
    votesCast: voters.length,
    votersTotal,
  };
}

function toMatchView(match: MockMatchRequest, viewerId: string) {
  const house = findHouse(match.houseId);
  return {
    id: match.id,
    houseId: match.houseId,
    status: match.status,
    createdAt: match.createdAt,
    moveDeadline: match.moveDeadline,
    candidate: { ...match.candidate },
    voters: Object.keys(match.votes).map((id) => ({
      id,
      name: house ? memberName(house, id) : 'Morador',
    })),
    myVote: match.votes[viewerId] ?? null,
  };
}

/** Aprovado pela regra da casa? `eligible` = quem pode votar. */
function outcome(
  rule: House['votingRule'],
  votes: Record<string, Vote>,
  eligible: string[],
): 'APPROVED' | 'REJECTED' | null {
  const approvals = eligible.filter((id) => votes[id] === 'APPROVE').length;
  const rejections = eligible.filter((id) => votes[id] === 'REJECT').length;
  const total = eligible.length;
  if (total === 0) return null;
  if (rule === 'UNANIMITY') {
    if (approvals === total) return 'APPROVED';
    if (rejections > 0) return 'REJECTED';
    return null;
  }
  if (approvals > total / 2) return 'APPROVED';
  if (rejections >= total / 2) return 'REJECTED';
  return null;
}

function resolveMatch(match: MockMatchRequest, house: House): void {
  const result = outcome(
    house.votingRule,
    match.votes,
    house.members.map((member) => member.id),
  );
  if (result === 'REJECTED') {
    match.status = 'REJECTED';
    return;
  }
  if (result !== 'APPROVED') return;
  house.vacancies = Math.max(0, house.vacancies - 1);
  const candidate = findMockUser(match.candidate.id);
  if (candidate?.houseId) {
    match.status = 'AWAITING_MOVE';
    match.moveDeadline = toLocalDateTimeUtc(new Date(Date.now() + MOVE_WINDOW_MS));
    return;
  }
  match.status = 'ACCEPTED';
  house.members.push({
    id: match.candidate.id,
    name: match.candidate.name,
    email: candidate?.email,
    isHouseAdmin: false,
  });
  if (candidate) {
    Object.assign(candidate, {
      houseId: house.id,
      houseName: house.name,
      isHouseAdmin: false,
      pendingWelcome: true,
    });
  }
}

function findMatch(requestId: string): MockMatchRequest | undefined {
  return houseManagementMock.matches.find((match) => match.id === requestId);
}

// ---------------------------------------------------------------------------
// Handlers
// ---------------------------------------------------------------------------

export const handlers: HttpHandler[] = [
  http.put('/api/houses/:id', async ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_HOUSE);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    if (!isAdminOf(house, user.id)) return apiError('Insufficient permission', 403);

    const body = await readJson(request);
    if (typeof body.vacancies === 'number' && (!Number.isInteger(body.vacancies) || body.vacancies < 0)) {
      return apiError('invalid_request', 400);
    }
    const editable: (keyof House)[] = [
      'name',
      'address',
      'description',
      'basicRules',
      'rentAmount',
      'allowsPets',
      'allowsSmokers',
      'expectedOrganizationLevel',
      'visitorTolerance',
      'hasFurniture',
      'votingRule',
      'isAcceptingMatches',
      'vacancies',
    ];
    const patch: Partial<House> = {};
    for (const key of editable) {
      if (key in body && body[key] !== undefined) {
        (patch as Record<string, unknown>)[key] = body[key];
      }
    }
    Object.assign(house, patch);
    if (typeof patch.name === 'string') {
      for (const member of house.members) syncUser(member.id, { houseName: patch.name });
    }
    return HttpResponse.json(toHouseView(house));
  }),

  http.put('/api/houses/:id/members/:memberId/admin', async ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_HOUSE);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    if (!isAdminOf(house, user.id)) return apiError('Insufficient permission', 403);
    const member = house.members.find((m) => m.id === String(params.memberId));
    if (!member) return apiError('user is not a member of this house', 404);

    const body = await readJson(request);
    if (typeof body.isAdmin !== 'boolean') return apiError('invalid_request', 400);
    const admins = house.members.filter((m) => m.isHouseAdmin === true);
    if (!body.isAdmin && member.isHouseAdmin && admins.length <= 1) {
      return apiError('house must keep at least one admin', 400);
    }
    member.isHouseAdmin = body.isAdmin;
    syncUser(member.id, { isHouseAdmin: body.isAdmin });
    return HttpResponse.json({
      id: member.id,
      name: member.name,
      email: member.email,
      isHouseAdmin: member.isHouseAdmin,
    });
  }),

  http.post('/api/houses/:id/leave', async ({ request, params }) => {
    const user = authenticate(request);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    const body = await readJson(request);
    const newAdminId = typeof body.newAdminId === 'string' ? body.newAdminId : undefined;
    const error = removeFromHouse(house, user.id, newAdminId);
    if (error) return error;
    return noContent();
  }),

  http.get('/api/houses/:id/removals', ({ request, params }) => {
    const user = authenticate(request);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    if (!isMember(house, user.id)) return apiError('Insufficient permission', 403);
    const open = houseManagementMock.removals
      .filter((removal) => removal.houseId === house.id && removal.status === 'OPEN')
      .map((removal) => toRemovalView(removal, user.id));
    return HttpResponse.json(open);
  }),

  http.post('/api/houses/:id/removals', async ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MANAGE_HOUSE);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    if (!isAdminOf(house, user.id)) return apiError('Insufficient permission', 403);

    const body = await readJson(request);
    const memberId = typeof body.memberId === 'string' ? body.memberId : '';
    if (!memberId || memberId === user.id || !isMember(house, memberId)) {
      return apiError('invalid_request', 400);
    }
    const alreadyOpen = houseManagementMock.removals.some(
      (removal) =>
        removal.houseId === house.id &&
        removal.targetUserId === memberId &&
        removal.status === 'OPEN',
    );
    if (alreadyOpen) return apiError('removal already open for this member', 400);

    const removal: MockRemoval = {
      id: nextId('removal'),
      houseId: house.id,
      targetUserId: memberId,
      targetUserName: memberName(house, memberId),
      proposedById: user.id,
      proposedByName: memberName(house, user.id),
      reason: typeof body.reason === 'string' && body.reason.trim() ? body.reason.trim() : null,
      status: 'OPEN',
      createdAt: toLocalDateTimeUtc(new Date()),
      votes: {},
    };
    houseManagementMock.removals.push(removal);
    return HttpResponse.json(toRemovalView(removal, user.id), { status: 201 });
  }),

  http.post('/api/houses/:id/removals/:removalId/vote', async ({ request, params }) => {
    const user = authenticate(request);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    if (!isMember(house, user.id)) return apiError('Insufficient permission', 403);
    const removal = houseManagementMock.removals.find(
      (r) => r.id === String(params.removalId) && r.houseId === house.id,
    );
    if (!removal || removal.status !== 'OPEN') return apiError('removal not found', 404);
    if (removal.targetUserId === user.id) {
      return apiError('target member cannot vote on own removal', 400);
    }

    const body = await readJson(request);
    if (body.vote !== 'APPROVE' && body.vote !== 'REJECT') return apiError('invalid_request', 400);
    removal.votes[user.id] = body.vote;

    const eligible = house.members.map((m) => m.id).filter((id) => id !== removal.targetUserId);
    const result = outcome(house.votingRule, removal.votes, eligible);
    if (result === 'APPROVED') {
      removal.status = 'APPROVED';
      const target = house.members.find((m) => m.id === removal.targetUserId);
      const heir = house.members.find((m) => m.id !== removal.targetUserId);
      removeFromHouse(house, removal.targetUserId, target?.isHouseAdmin ? heir?.id : undefined);
    } else if (result === 'REJECTED') {
      removal.status = 'REJECTED';
    }
    return HttpResponse.json(toRemovalView(removal, user.id));
  }),

  http.delete('/api/houses/:id/removals/:removalId', ({ request, params }) => {
    const user = authenticate(request);
    if (user instanceof Response) return user;
    const removal = houseManagementMock.removals.find(
      (r) => r.id === String(params.removalId) && r.houseId === String(params.id),
    );
    if (!removal || removal.status !== 'OPEN') return apiError('removal not found', 404);
    if (removal.proposedById !== user.id) return apiError('Insufficient permission', 403);
    removal.status = 'CANCELLED';
    return noContent();
  }),

  http.get('/api/houses/:id/matches', ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;
    const house = findHouse(String(params.id));
    if (!house) return apiError('house not found', 404);
    if (!isMember(house, user.id)) return apiError('Insufficient permission', 403);
    const list = houseManagementMock.matches
      .filter((match) => match.houseId === house.id)
      .map((match) => toMatchView(match, user.id));
    return HttpResponse.json(list);
  }),

  http.post('/api/matches/requests/:requestId/vote', async ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;
    const match = findMatch(String(params.requestId));
    if (!match) return apiError('match request not found', 404);
    const house = findHouse(match.houseId);
    if (!house || !isMember(house, user.id)) return apiError('Insufficient permission', 403);
    if (match.status !== 'PENDING') return apiError('request is not pending', 400);

    const body = await readJson(request);
    if (body.vote !== 'APPROVE' && body.vote !== 'REJECT') return apiError('invalid_request', 400);
    if (body.vote === 'APPROVE' && house.vacancies <= 0) {
      return apiError('house has no available vacancies', 400);
    }
    match.votes[user.id] = body.vote;
    resolveMatch(match, house);

    const values = Object.values(match.votes);
    return HttpResponse.json({
      requestId: match.id,
      status: match.status,
      approveVotes: values.filter((v) => v === 'APPROVE').length,
      rejectVotes: values.filter((v) => v === 'REJECT').length,
      totalHouseMembers: house.members.length,
      hasOtherPendingRequests: false,
      otherPendingRequestIds: [],
    });
  }),

  http.post('/api/matches/requests/:requestId/confirm-move', async ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;
    const match = findMatch(String(params.requestId));
    if (!match || match.candidate.id !== user.id) return apiError('match request not found', 404);
    if (match.status !== 'AWAITING_MOVE') return apiError('request is not awaiting move', 400);
    const target = findHouse(match.houseId);
    if (!target) return apiError('house not found', 404);

    const body = await readJson(request);
    const newAdminId = typeof body.newAdminId === 'string' ? body.newAdminId : undefined;
    const current = user.houseId ? findHouse(user.houseId) : undefined;
    if (current) {
      const error = removeFromHouse(current, user.id, newAdminId);
      if (error) return error;
    }

    target.members.push({ id: user.id, name: user.name, email: user.email, isHouseAdmin: false });
    Object.assign(user, { houseId: target.id, houseName: target.name, isHouseAdmin: false });
    match.status = 'ACCEPTED';
    const { password: _password, ...me } = user;
    void _password;
    return HttpResponse.json(me);
  }),

  http.post('/api/matches/requests/:requestId/decline-move', ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;
    const match = findMatch(String(params.requestId));
    if (!match || match.candidate.id !== user.id) return apiError('match request not found', 404);
    if (match.status !== 'AWAITING_MOVE') return apiError('request is not awaiting move', 400);
    match.status = 'WITHDRAWN';
    const target = findHouse(match.houseId);
    if (target) target.vacancies += 1;
    return noContent();
  }),

  http.post('/api/matches/requests/:requestId/retract', ({ request, params }) => {
    const user = authenticate(request, PERMISSIONS.MATCH);
    if (user instanceof Response) return user;
    const match = findMatch(String(params.requestId));
    if (!match) return apiError('match request not found', 404);
    const target = findHouse(match.houseId);
    if (!target || !isAdminOf(target, user.id)) return apiError('Insufficient permission', 403);
    if (match.status !== 'AWAITING_MOVE') return apiError('request is not awaiting move', 400);
    match.status = 'CANCELLED';
    target.vacancies += 1;
    return noContent();
  }),
];

