import { http, HttpResponse, type HttpHandler } from 'msw';
import type { ChoreFrequency, ChoreStatus, ChoreSwapStatus, ChoreType } from '../../api/chores';
import { PERMISSIONS } from '../../api/me';
import { db, findMockUser, HOUSE_JABUTICABA_ID, MOCK_MORADOR_ID, type MockUser } from '../db';

// Dono único de `/api/houses/:h/chores*`. Espelha o ChoreService da B9: roda
// filtrada pelos moradores atuais (lidos de `db` na hora da requisição), troca
// temporária só no ciclo, reabrir → atrasada, +20 no fechamento.
// Estado module-local: `resetChoresMocks()` volta à semente.

const CARLA_ID = 'user-carla';
const ON_TIME_POINTS = 20;

const REQUIRED: Record<ChoreFrequency, number> = {
  ONCE: 1,
  TWICE: 2,
  THRICE: 3,
  DAILY: 7,
  BIWEEKLY: 1,
};

export interface MockChore {
  id: string;
  houseId: string;
  description: string;
  choreType: ChoreType;
  status: ChoreStatus;
  frequency: ChoreFrequency;
  completionsThisCycle: number;
  delayedThisCycle: boolean;
  linkedToGroceries: boolean;
  currentAssigneeId: string | null;
  temporaryAssigneeId: string | null;
  rotationOrder: string[];
  cycleNumber: number;
  weeksInCycle: number;
  reopenedById: string | null;
  reopenedAt: string | null;
  createdAt: string;
}

export interface MockChoreSwap {
  id: string;
  houseId: string;
  requesterChoreId: string;
  targetChoreId: string;
  requesterId: string;
  targetUserId: string;
  status: ChoreSwapStatus;
  cycleNumber: number;
  createdAt: string;
}

interface ChoresState {
  chores: MockChore[];
  swaps: MockChoreSwap[];
  /** Último corpo recebido em `POST /chores` (para os testes conferirem a roda). */
  lastCreateBody: unknown;
  seq: number;
}

function chore(partial: Partial<MockChore> & Pick<MockChore, 'id' | 'description'>): MockChore {
  return {
    houseId: HOUSE_JABUTICABA_ID,
    choreType: 'FIXED',
    status: 'PENDING',
    frequency: 'ONCE',
    completionsThisCycle: 0,
    delayedThisCycle: false,
    linkedToGroceries: false,
    currentAssigneeId: MOCK_MORADOR_ID,
    temporaryAssigneeId: null,
    rotationOrder: [MOCK_MORADOR_ID, CARLA_ID],
    cycleNumber: 1,
    weeksInCycle: 0,
    reopenedById: null,
    reopenedAt: null,
    createdAt: '2026-11-23T09:00:00',
    ...partial,
  };
}

function seed(): ChoresState {
  return {
    chores: [
      chore({
        id: 'chore-louca',
        description: 'Lavar a louça',
        choreType: 'ROTATIONAL',
      }),
      chore({
        id: 'chore-lixo',
        description: 'Tirar o lixo',
        frequency: 'THRICE',
        completionsThisCycle: 2,
      }),
      chore({
        id: 'chore-plantas',
        description: 'Regar as plantas',
        status: 'COMPLETED',
        completionsThisCycle: 1,
      }),
      chore({
        id: 'chore-banheiro',
        description: 'Limpar o banheiro',
        choreType: 'ROTATIONAL',
        status: 'COMPLETED',
        completionsThisCycle: 1,
        currentAssigneeId: CARLA_ID,
        rotationOrder: [CARLA_ID, MOCK_MORADOR_ID],
      }),
      chore({
        id: 'chore-sala',
        description: 'Varrer a sala',
        frequency: 'TWICE',
        currentAssigneeId: CARLA_ID,
      }),
      chore({
        id: 'chore-mercado',
        description: 'Fazer o mercado',
        choreType: 'ROTATIONAL',
        frequency: 'BIWEEKLY',
        linkedToGroceries: true,
        currentAssigneeId: CARLA_ID,
        rotationOrder: [CARLA_ID, MOCK_MORADOR_ID],
      }),
    ],
    swaps: [
      {
        id: 'swap-sala-louca',
        houseId: HOUSE_JABUTICABA_ID,
        requesterChoreId: 'chore-sala',
        targetChoreId: 'chore-louca',
        requesterId: CARLA_ID,
        targetUserId: MOCK_MORADOR_ID,
        status: 'PENDING',
        cycleNumber: 1,
        createdAt: '2026-11-24T18:30:00',
      },
    ],
    lastCreateBody: null,
    seq: 0,
  };
}

let state: ChoresState = seed();

export function resetChoresMocks(): void {
  state = seed();
}

/** Leitura do estado do mock para asserções de teste. */
export function getChoresMockState(): Readonly<ChoresState> {
  return state;
}

// ---------------------------------------------------------------------------
// Auth e casa (core.ts não exporta `authenticate`)
// ---------------------------------------------------------------------------

const apiError = (error: string, status: number) => HttpResponse.json({ error }, { status });

function authenticate(request: Request): MockUser | Response {
  const auth = request.headers.get('Authorization');
  const user = findMockUser(db.session.userId);
  if (!db.session.accessToken || auth !== `Bearer ${db.session.accessToken}` || !user) {
    return apiError('Authentication required', 401);
  }
  if (!user.permissions.includes(PERMISSIONS.MANAGE_CHORES)) {
    return apiError('Insufficient permission', 403);
  }
  return user;
}

interface HouseContext {
  user: MockUser;
  houseId: string;
  memberIds: string[];
  names: Map<string, string>;
}

function houseContext(
  request: Request,
  houseIdParam: unknown,
  { admin = false }: { admin?: boolean } = {},
): HouseContext | Response {
  const user = authenticate(request);
  if (user instanceof Response) return user;
  const houseId = String(houseIdParam);
  const house = db.houses.find((h) => h.id === houseId);
  if (!house) return apiError('house not found', 404);
  const member = house.members.find((m) => m.id === user.id);
  if (!member) return apiError('User does not belong to this house', 403);
  if (admin && !member.isHouseAdmin) {
    return apiError('User is not an administrator of this house', 403);
  }
  return {
    user,
    houseId,
    memberIds: house.members.map((m) => m.id),
    names: new Map(house.members.map((m) => [m.id, m.name])),
  };
}

function findChore(ctx: HouseContext, choreId: unknown): MockChore | undefined {
  return state.chores.find((c) => c.id === String(choreId) && c.houseId === ctx.houseId);
}

// ---------------------------------------------------------------------------
// Regras da roda (espelho de ChoreService.effectiveOrder/effectiveAssignee)
// ---------------------------------------------------------------------------

function effectiveOrder(c: MockChore, memberIds: string[]): string[] {
  const order = new Set(c.rotationOrder.filter((id) => memberIds.includes(id)));
  for (const id of memberIds) order.add(id);
  return [...order];
}

function activeTemporary(c: MockChore, memberIds: string[]): string | null {
  return c.temporaryAssigneeId && memberIds.includes(c.temporaryAssigneeId)
    ? c.temporaryAssigneeId
    : null;
}

function effectiveAssignee(c: MockChore, memberIds: string[]): string | null {
  const temporary = activeTemporary(c, memberIds);
  if (temporary) return temporary;
  if (c.currentAssigneeId && memberIds.includes(c.currentAssigneeId)) return c.currentAssigneeId;
  const order = effectiveOrder(c, memberIds);
  return order[0] ?? c.currentAssigneeId;
}

function nextAssignee(c: MockChore, memberIds: string[]): string | null {
  const order = effectiveOrder(c, memberIds);
  if (c.choreType !== 'ROTATIONAL' || order.length === 0) return c.currentAssigneeId;
  const index = c.currentAssigneeId ? order.indexOf(c.currentAssigneeId) : -1;
  return index < 0 ? (order[0] ?? null) : (order[(index + 1) % order.length] ?? null);
}

function nameOf(ctx: HouseContext, id: string | null): string | undefined {
  return id ? ctx.names.get(id) : undefined;
}

function toSwapDto(ctx: HouseContext, swap: MockChoreSwap) {
  const describe = (id: string) => state.chores.find((c) => c.id === id)?.description;
  return {
    ...swap,
    requesterChoreDescription: describe(swap.requesterChoreId),
    targetChoreDescription: describe(swap.targetChoreId),
    requesterName: nameOf(ctx, swap.requesterId),
    targetUserName: nameOf(ctx, swap.targetUserId),
  };
}

function involves(swap: MockChoreSwap, choreId: string): boolean {
  return swap.requesterChoreId === choreId || swap.targetChoreId === choreId;
}

function pendingSwapsOf(choreId: string): MockChoreSwap[] {
  return state.swaps.filter((s) => s.status === 'PENDING' && involves(s, choreId));
}

/** `ChoreResponse` como o Jackson da B9 (`choreType`, campos nulos omitidos). */
function toResponse(ctx: HouseContext, c: MockChore) {
  const effective = effectiveAssignee(c, ctx.memberIds);
  const temporary = activeTemporary(c, ctx.memberIds);
  const next = nextAssignee(c, ctx.memberIds);
  const body: Record<string, unknown> = {
    id: c.id,
    houseId: c.houseId,
    description: c.description,
    choreType: c.choreType,
    status: c.status,
    frequency: c.frequency,
    requiredCompletions: REQUIRED[c.frequency],
    completionsThisCycle: c.completionsThisCycle,
    delayedThisCycle: c.delayedThisCycle,
    linkedToGroceries: c.linkedToGroceries,
    currentAssigneeId: c.currentAssigneeId,
    currentAssigneeName: nameOf(ctx, c.currentAssigneeId),
    effectiveAssigneeId: effective,
    effectiveAssigneeName: nameOf(ctx, effective),
    temporaryAssigneeId: temporary,
    temporaryAssigneeName: nameOf(ctx, temporary),
    nextAssigneeId: next,
    nextAssigneeName: nameOf(ctx, next),
    rotationOrder: effectiveOrder(c, ctx.memberIds),
    cycleNumber: c.cycleNumber,
    reopenedById: c.reopenedById,
    reopenedByName: nameOf(ctx, c.reopenedById),
    reopenedAt: c.reopenedAt,
    pendingSwaps: pendingSwapsOf(c.id).map((s) => toSwapDto(ctx, s)),
  };
  for (const key of Object.keys(body)) {
    if (body[key] === null || body[key] === undefined) delete body[key];
  }
  return body;
}

function nowLocal(): string {
  return new Date().toISOString().slice(0, 19);
}

function nextId(prefix: string): string {
  state.seq += 1;
  return `${prefix}-${state.seq}`;
}

const FREQUENCIES = Object.keys(REQUIRED) as ChoreFrequency[];

function closeCycle(ctx: HouseContext): void {
  for (const c of state.chores.filter((x) => x.houseId === ctx.houseId)) {
    if (c.frequency === 'BIWEEKLY' && c.weeksInCycle === 0) {
      c.weeksInCycle = 1;
      continue;
    }
    if (c.status !== 'COMPLETED') {
      c.status = 'DELAYED';
      c.delayedThisCycle = true;
      continue;
    }
    const doneBy = effectiveAssignee(c, ctx.memberIds);
    const nextOwner = nextAssignee(c, ctx.memberIds);
    if (!c.delayedThisCycle && doneBy) {
      const user = findMockUser(doneBy);
      if (user) {
        user.points = {
          total: user.points.total + ON_TIME_POINTS,
          thisMonth: user.points.thisMonth + ON_TIME_POINTS,
        };
      }
    }
    c.cycleNumber += 1;
    c.completionsThisCycle = 0;
    c.delayedThisCycle = false;
    c.reopenedById = null;
    c.reopenedAt = null;
    c.temporaryAssigneeId = null;
    c.weeksInCycle = 0;
    c.status = 'PENDING';
    for (const swap of pendingSwapsOf(c.id)) swap.status = 'CANCELLED';
    if (c.choreType === 'ROTATIONAL' && nextOwner) c.currentAssigneeId = nextOwner;
  }
}

function answerSwap(
  request: Request,
  params: Record<string, unknown>,
  answer: 'accept' | 'reject',
): Response {
  const ctx = houseContext(request, params.houseId);
  if (ctx instanceof Response) return ctx;
  const swap = state.swaps.find((s) => s.id === String(params.swapId) && s.houseId === ctx.houseId);
  if (!swap) return apiError('invalid chore swap', 400);
  if (swap.targetUserId !== ctx.user.id) {
    return apiError('only the target resident can answer the swap', 403);
  }
  if (swap.status !== 'PENDING') return apiError('invalid chore swap', 400);
  if (answer === 'reject') {
    swap.status = 'REJECTED';
    return HttpResponse.json(toSwapDto(ctx, swap));
  }
  const requesterChore = findChore(ctx, swap.requesterChoreId);
  const targetChore = findChore(ctx, swap.targetChoreId);
  if (!requesterChore || !targetChore) return apiError('invalid chore swap', 400);
  swap.status = 'ACCEPTED';
  requesterChore.temporaryAssigneeId = swap.targetUserId;
  targetChore.temporaryAssigneeId = swap.requesterId;
  return HttpResponse.json(toSwapDto(ctx, swap));
}

const BASE = '/api/houses/:houseId/chores';

export const handlers: HttpHandler[] = [
  http.get(BASE, ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    return HttpResponse.json(
      state.chores.filter((c) => c.houseId === ctx.houseId).map((c) => toResponse(ctx, c)),
    );
  }),

  http.get(`${BASE}/swaps`, ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    return HttpResponse.json(
      state.swaps
        .filter((s) => s.houseId === ctx.houseId && s.status === 'PENDING')
        .map((s) => toSwapDto(ctx, s)),
    );
  }),

  http.post(BASE, async ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    const body = (await request.json()) as Record<string, unknown>;
    state.lastCreateBody = body;

    const description = typeof body.description === 'string' ? body.description.trim() : '';
    if (!description) return apiError('Chore description is required', 400);
    const choreType = body.choreType === 'ROTATIONAL' ? 'ROTATIONAL' : 'FIXED';
    const frequency = body.frequency ?? 'ONCE';
    if (!FREQUENCIES.includes(frequency as ChoreFrequency)) {
      return apiError('invalid chore frequency', 400);
    }

    let rotationOrder = ctx.memberIds;
    if (Array.isArray(body.rotationOrder)) {
      const order = body.rotationOrder.map(String);
      const exact =
        order.length === ctx.memberIds.length &&
        new Set(order).size === order.length &&
        order.every((id) => ctx.memberIds.includes(id));
      if (!exact) return apiError('rotation order must match current house members', 400);
      rotationOrder = order;
    }

    const assigneeId = typeof body.assigneeId === 'string' ? body.assigneeId : null;
    if (assigneeId && !ctx.memberIds.includes(assigneeId)) {
      return apiError('Assignee must be a member of the house', 400);
    }
    const created = chore({
      id: nextId('chore-nova'),
      houseId: ctx.houseId,
      description,
      choreType,
      frequency: frequency as ChoreFrequency,
      linkedToGroceries: body.linkedToGroceries === true,
      rotationOrder,
      currentAssigneeId:
        choreType === 'ROTATIONAL' ? (rotationOrder[0] ?? null) : (assigneeId ?? ctx.user.id),
      createdAt: nowLocal(),
    });
    state.chores.push(created);
    return HttpResponse.json(toResponse(ctx, created), { status: 201 });
  }),

  http.put(`${BASE}/:choreId/complete`, ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    const c = findChore(ctx, params.choreId);
    if (!c) return apiError('Chore not found in this house', 404);
    if (effectiveAssignee(c, ctx.memberIds) !== ctx.user.id) {
      return apiError('only the assignee can complete', 403);
    }
    const required = REQUIRED[c.frequency];
    if (c.status === 'COMPLETED' || c.completionsThisCycle >= required) {
      return apiError('chore already completed for this cycle', 400);
    }
    c.completionsThisCycle += 1;
    if (c.completionsThisCycle >= required) c.status = 'COMPLETED';
    return HttpResponse.json(toResponse(ctx, c));
  }),

  http.put(`${BASE}/:choreId/uncomplete`, ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    const c = findChore(ctx, params.choreId);
    if (!c) return apiError('Chore not found in this house', 404);
    if (effectiveAssignee(c, ctx.memberIds) !== ctx.user.id) {
      return apiError('only the assignee can complete', 403);
    }
    if (c.completionsThisCycle <= 0) return apiError('chore has no completions to undo', 400);
    c.completionsThisCycle -= 1;
    if (c.completionsThisCycle < REQUIRED[c.frequency]) {
      c.status = c.delayedThisCycle ? 'DELAYED' : 'PENDING';
    }
    return HttpResponse.json(toResponse(ctx, c));
  }),

  http.put(`${BASE}/:choreId/reopen`, ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    const c = findChore(ctx, params.choreId);
    if (!c) return apiError('Chore not found in this house', 404);
    if (effectiveAssignee(c, ctx.memberIds) === ctx.user.id) {
      return apiError('assignee cannot reopen own chore', 403);
    }
    if (c.status !== 'COMPLETED') return apiError('chore is not completed', 400);
    c.status = 'DELAYED';
    c.delayedThisCycle = true;
    c.completionsThisCycle = Math.max(0, REQUIRED[c.frequency] - 1);
    c.reopenedById = ctx.user.id;
    c.reopenedAt = nowLocal();
    return HttpResponse.json(toResponse(ctx, c));
  }),

  http.put(`${BASE}/:choreId/rotation`, async ({ request, params }) => {
    const ctx = houseContext(request, params.houseId, { admin: true });
    if (ctx instanceof Response) return ctx;
    const c = findChore(ctx, params.choreId);
    if (!c) return apiError('Chore not found in this house', 404);
    const body = (await request.json()) as { order?: unknown };
    const order = Array.isArray(body.order) ? body.order.map(String) : null;
    const exact =
      order !== null &&
      order.length === ctx.memberIds.length &&
      new Set(order).size === order.length &&
      order.every((id) => ctx.memberIds.includes(id));
    if (!order || !exact) {
      return apiError('rotation order must match current house members', 400);
    }
    c.rotationOrder = order;
    return HttpResponse.json(toResponse(ctx, c));
  }),

  http.post(`${BASE}/:choreId/swaps`, async ({ request, params }) => {
    const ctx = houseContext(request, params.houseId);
    if (ctx instanceof Response) return ctx;
    const requesterChore = findChore(ctx, params.choreId);
    if (!requesterChore) return apiError('Chore not found in this house', 404);
    const body = (await request.json()) as { targetChoreId?: unknown };
    const targetChoreId = typeof body.targetChoreId === 'string' ? body.targetChoreId : '';
    const targetChore = findChore(ctx, targetChoreId);
    const targetUserId = targetChore ? effectiveAssignee(targetChore, ctx.memberIds) : null;
    if (
      !targetChore ||
      targetChore.id === requesterChore.id ||
      effectiveAssignee(requesterChore, ctx.memberIds) !== ctx.user.id ||
      !targetUserId ||
      targetUserId === ctx.user.id ||
      activeTemporary(requesterChore, ctx.memberIds) ||
      activeTemporary(targetChore, ctx.memberIds) ||
      pendingSwapsOf(requesterChore.id).length > 0 ||
      pendingSwapsOf(targetChore.id).length > 0
    ) {
      return apiError('invalid chore swap', 400);
    }
    const swap: MockChoreSwap = {
      id: nextId('swap'),
      houseId: ctx.houseId,
      requesterChoreId: requesterChore.id,
      targetChoreId: targetChore.id,
      requesterId: ctx.user.id,
      targetUserId,
      status: 'PENDING',
      cycleNumber: requesterChore.cycleNumber,
      createdAt: nowLocal(),
    };
    state.swaps.push(swap);
    return HttpResponse.json(toSwapDto(ctx, swap), { status: 201 });
  }),

  http.put(`${BASE}/swaps/:swapId/accept`, ({ request, params }) =>
    answerSwap(request, params, 'accept'),
  ),

  http.put(`${BASE}/swaps/:swapId/reject`, ({ request, params }) =>
    answerSwap(request, params, 'reject'),
  ),

  http.post(`${BASE}/rotate`, ({ request, params }) => {
    const ctx = houseContext(request, params.houseId, { admin: true });
    if (ctx instanceof Response) return ctx;
    closeCycle(ctx);
    return HttpResponse.json(
      state.chores.filter((c) => c.houseId === ctx.houseId).map((c) => toResponse(ctx, c)),
    );
  }),
];
