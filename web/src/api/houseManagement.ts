import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { z } from 'zod';
import { ApiError, request } from '../auth/apiClient';
import { translateApiError } from './errors';
import { useSession } from '../auth/SessionContext';
import { houseMemberSchema, houseSchema, votingRuleSchema, type House } from './houses';
import { userMeSchema, type UserMe } from './me';
import { queryKeys } from './queryKeys';
import { userRefSchema } from './types';

// ---------------------------------------------------------------------------
// Datas
// ---------------------------------------------------------------------------

const HAS_OFFSET = /(Z|[+-]\d{2}:?\d{2})$/i;

/**
 * A API serializa `LocalDateTime` em UTC sem sufixo (`2026-11-29T12:00:00`).
 * `new Date()` leria isso como hora local; aqui o `Z` é anexado quando falta
 * offset, para o prazo de 72 h bater em qualquer fuso (ex.: America/Sao_Paulo).
 */
export function parseUtcLocalDateTime(iso: string): Date {
  const trimmed = iso.trim();
  return new Date(HAS_OFFSET.test(trimmed) ? trimmed : `${trimmed}Z`);
}

// ---------------------------------------------------------------------------
// Schemas
// ---------------------------------------------------------------------------

export const voteSchema = z.enum(['APPROVE', 'REJECT']);
export type Vote = z.infer<typeof voteSchema>;

export const matchStatusSchema = z.enum([
  'PENDING',
  'ACCEPTED',
  'REJECTED',
  'CANCELLED',
  'WITHDRAWN',
  'AWAITING_MOVE',
  'EXPIRED',
]);
export type MatchStatus = z.infer<typeof matchStatusSchema>;

/** Pontos chegam como número; aceita também `{ total }` do `/me`. */
const pointsSchema = z.union([
  z.number(),
  z.object({ total: z.number() }).transform((points) => points.total),
]);

/** Morador com os dados extras que a tela Casa mostra quando a API manda. */
export const managedMemberSchema = houseMemberSchema.extend({
  points: pointsSchema.nullish(),
  residenceMonths: z.number().int().nullish(),
});
export type ManagedMember = z.infer<typeof managedMemberSchema>;

export const managedHouseSchema = houseSchema.extend({
  members: z.array(managedMemberSchema).default([]),
});
export type ManagedHouse = z.infer<typeof managedHouseSchema>;

/** Resposta de `PUT /members/:m/admin` (`HouseMemberSummaryDto`). */
export const houseMemberSummarySchema = houseMemberSchema;

export const voterSummarySchema = userRefSchema;
export const voteDirectionSchema = voteSchema;

export const removalSchema = z.object({
  id: z.string(),
  targetUserId: z.string(),
  targetUserName: z.string(),
  proposedById: z.string(),
  proposedByName: z.string(),
  reason: z.string().nullable().optional(),
  status: z.string(),
  createdAt: z.string(),
  /** Quem já votou, sem a direção do voto. */
  voters: z.array(voterSummarySchema),
  myVote: voteDirectionSchema.nullable(),
  votesCast: z.number(),
  votersTotal: z.number(),
});
export type RemovalRequest = z.infer<typeof removalSchema>;
export type Removal = RemovalRequest;

export const matchCandidateSchema = z.preprocess(
  (raw) => {
    // A spec chama o campo de `compatibility`; a API real, de `compatibilityScore`.
    if (raw && typeof raw === 'object' && !('compatibilityScore' in raw) && 'compatibility' in raw) {
      return { ...raw, compatibilityScore: (raw as { compatibility: unknown }).compatibility };
    }
    return raw;
  },
  z.object({
    id: z.string(),
    name: z.string(),
    compatibilityScore: z.number().nullable().default(null),
    points: pointsSchema.nullable().default(null),
    /** `null` quando nunca morou numa casa do Nosso Teto. */
    residenceMonths: z.number().int().nullable().default(null),
  }),
);
export type MatchCandidate = z.infer<typeof matchCandidateSchema>;

export const houseMatchSchema = z.object({
  id: z.string(),
  houseId: z.string().nullish(),
  status: matchStatusSchema,
  /** `LocalDateTime` UTC sem offset. */
  createdAt: z.string().nullish(),
  /** `LocalDateTime` UTC sem offset; só em `AWAITING_MOVE`. */
  moveDeadline: z.string().nullish(),
  candidate: matchCandidateSchema,
  voters: z.array(userRefSchema).default([]),
  myVote: voteSchema.nullable().default(null),
});
export type HouseMatch = z.infer<typeof houseMatchSchema>;

/** Item de `GET /api/matches/requests/mine` (só o que a tela de mudança lê). */
export const myMatchRequestSchema = z.object({
  id: z.string(),
  houseId: z.string(),
  houseName: z.string().nullish(),
  status: matchStatusSchema,
  createdAt: z.string().nullish(),
  votesCast: z.number().int().nullish(),
  votersTotal: z.number().int().nullish(),
  moveDeadline: z.string().nullish(),
});
export type MyMatchRequest = z.infer<typeof myMatchRequestSchema>;

export interface UpdateHousePayload {
  name: string;
  address?: string | null;
  description?: string | null;
  basicRules?: string | null;
  rentAmount?: number | null;
  allowsPets: boolean;
  allowsSmokers: boolean;
  expectedOrganizationLevel: number;
  visitorTolerance: number;
  hasFurniture: boolean;
  votingRule: z.infer<typeof votingRuleSchema>;
  isAcceptingMatches: boolean;
  vacancies: number;
}

// ---------------------------------------------------------------------------
// Chamadas
// ---------------------------------------------------------------------------

const enc = encodeURIComponent;

/** Chave própria sob `queryKeys.house(id)`: invalidar a casa também a atualiza. */
export const managedHouseKey = (houseId: string) =>
  [...queryKeys.house(houseId), 'management'] as const;

/** Sob `queryKeys.myMatchRequests`, mas com schema próprio da tela de mudança. */
export const myMoveRequestsKey = [...queryKeys.myMatchRequests, 'mudanca'] as const;

export function fetchManagedHouse(houseId: string): Promise<ManagedHouse> {
  return request(`/api/houses/${enc(houseId)}`, { schema: managedHouseSchema });
}

export function updateHouse(houseId: string, payload: UpdateHousePayload): Promise<House> {
  return request(`/api/houses/${enc(houseId)}`, {
    method: 'PUT',
    json: payload,
    schema: houseSchema,
  });
}

export function setMemberAdmin(houseId: string, memberId: string, isAdmin: boolean) {
  return request(`/api/houses/${enc(houseId)}/members/${enc(memberId)}/admin`, {
    method: 'PUT',
    json: { isAdmin },
    schema: houseMemberSummarySchema,
  });
}

export function leaveHouse(houseId: string, newAdminId?: string): Promise<void> {
  return request(`/api/houses/${enc(houseId)}/leave`, {
    method: 'POST',
    json: newAdminId ? { newAdminId } : {},
  });
}

export function fetchRemovals(houseId: string): Promise<Removal[]> {
  return request(`/api/houses/${enc(houseId)}/removals`, { schema: z.array(removalSchema) });
}

export function proposeRemoval(houseId: string, memberId: string, reason?: string) {
  const trimmed = reason?.trim();
  return request(`/api/houses/${enc(houseId)}/removals`, {
    method: 'POST',
    json: trimmed ? { memberId, reason: trimmed } : { memberId },
    schema: removalSchema,
  });
}

export function voteOnRemoval(houseId: string, removalId: string, vote: Vote) {
  return request(`/api/houses/${enc(houseId)}/removals/${enc(removalId)}/vote`, {
    method: 'POST',
    json: { vote },
  });
}

export function cancelRemoval(houseId: string, removalId: string): Promise<void> {
  return request(`/api/houses/${enc(houseId)}/removals/${enc(removalId)}`, {
    method: 'DELETE',
  });
}

export function fetchHouseMatches(houseId: string): Promise<HouseMatch[]> {
  return request(`/api/houses/${enc(houseId)}/matches`, { schema: z.array(houseMatchSchema) });
}

/** Rota do `MatchController`, fora de `/houses`. */
export function voteOnMatch(requestId: string, vote: Vote) {
  return request(`/api/matches/requests/${enc(requestId)}/vote`, {
    method: 'POST',
    json: { vote },
  });
}

export function confirmMove(requestId: string, newAdminId?: string): Promise<UserMe> {
  return request(`/api/matches/requests/${enc(requestId)}/confirm-move`, {
    method: 'POST',
    json: newAdminId ? { newAdminId } : {},
    schema: userMeSchema,
  });
}

export function declineMove(requestId: string): Promise<void> {
  return request(`/api/matches/requests/${enc(requestId)}/decline-move`, { method: 'POST' });
}

export function retractReservation(requestId: string): Promise<void> {
  return request(`/api/matches/requests/${enc(requestId)}/retract`, { method: 'POST' });
}

/** Não existe `GET /api/matches/requests/:id`: filtra a lista do candidato. */
export async function fetchMyMatchRequest(requestId: string): Promise<MyMatchRequest | null> {
  const list = await request('/api/matches/requests/mine', {
    schema: z.array(myMatchRequestSchema),
  });
  return list.find((item) => item.id === requestId) ?? null;
}

// ---------------------------------------------------------------------------
// Regras derivadas
// ---------------------------------------------------------------------------

export function adminsOf(members: { isHouseAdmin: boolean | null }[]): number {
  return members.filter((member) => member.isHouseAdmin === true).length;
}

/**
 * Único admin com outros moradores: sair (ou mudar de casa) exige herdeiro,
 * senão a API responde `400 "new admin required"`.
 */
export function needsHeir(
  me: Pick<UserMe, 'id' | 'isHouseAdmin'> | null,
  members: { id: string; isHouseAdmin: boolean | null }[],
): boolean {
  if (!me?.isHouseAdmin) return false;
  const others = members.filter((member) => member.id !== me.id);
  return others.length > 0 && adminsOf(members) <= 1;
}

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

export function useManagedHouseQuery(houseId: string | null | undefined) {
  return useQuery({
    queryKey: managedHouseKey(houseId ?? ''),
    queryFn: () => fetchManagedHouse(houseId as string),
    enabled: Boolean(houseId),
  });
}

export function useRemovalsQuery(houseId: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.houseRemovals(houseId ?? ''),
    queryFn: () => fetchRemovals(houseId as string),
    enabled: Boolean(houseId),
  });
}

export function useHouseMatchesQuery(houseId: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.houseMatches(houseId ?? ''),
    queryFn: () => fetchHouseMatches(houseId as string),
    enabled: Boolean(houseId),
  });
}

export function useMyMoveRequestQuery(requestId: string | undefined) {
  return useQuery({
    queryKey: [...myMoveRequestsKey, requestId ?? ''],
    queryFn: () => fetchMyMatchRequest(requestId as string),
    enabled: Boolean(requestId),
  });
}

export function useUpdateHouseMutation(houseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpdateHousePayload) => updateHouse(houseId, payload),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.house(houseId) }),
        queryClient.invalidateQueries({ queryKey: queryKeys.houses }),
      ]);
    },
  });
}

export function useSetMemberAdminMutation(houseId: string) {
  const queryClient = useQueryClient();
  const { user, refreshMe } = useSession();
  return useMutation({
    mutationFn: ({ memberId, isAdmin }: { memberId: string; isAdmin: boolean }) =>
      setMemberAdmin(houseId, memberId, isAdmin),
    onSuccess: async (_data, { memberId }) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.house(houseId) });
      // O guard lê `isHouseAdmin` da sessão: mudar o próprio cargo exige refreshMe.
      if (memberId === user?.id) await refreshMe();
    },
  });
}

export function useLeaveHouseMutation(houseId: string) {
  const queryClient = useQueryClient();
  const { refreshMe } = useSession();
  return useMutation({
    mutationFn: (newAdminId?: string) => leaveHouse(houseId, newAdminId),
    onSuccess: async () => {
      await refreshMe();
      await queryClient.invalidateQueries({ queryKey: ['house', houseId] });
      await queryClient.invalidateQueries({ queryKey: queryKeys.houses });
    },
  });
}

function useRemovalsInvalidation(houseId: string) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.houseRemovals(houseId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.house(houseId) }),
    ]);
}

export function useProposeRemovalMutation(houseId: string) {
  const invalidate = useRemovalsInvalidation(houseId);
  return useMutation({
    mutationFn: ({ memberId, reason }: { memberId: string; reason?: string }) =>
      proposeRemoval(houseId, memberId, reason),
    onSuccess: invalidate,
  });
}

export function useVoteRemovalMutation(houseId: string) {
  const invalidate = useRemovalsInvalidation(houseId);
  return useMutation({
    mutationFn: ({ removalId, vote }: { removalId: string; vote: Vote }) =>
      voteOnRemoval(houseId, removalId, vote),
    onSuccess: invalidate,
  });
}

export function useCancelRemovalMutation(houseId: string) {
  const invalidate = useRemovalsInvalidation(houseId);
  return useMutation({
    mutationFn: (removalId: string) => cancelRemoval(houseId, removalId),
    onSuccess: invalidate,
  });
}

function useHouseMatchesInvalidation(houseId: string) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.houseMatches(houseId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.house(houseId) }),
    ]);
}

export function useVoteMatchMutation(houseId: string) {
  const invalidate = useHouseMatchesInvalidation(houseId);
  return useMutation({
    mutationFn: ({ requestId, vote }: { requestId: string; vote: Vote }) =>
      voteOnMatch(requestId, vote),
    onSuccess: invalidate,
  });
}

export function useRetractReservationMutation(houseId: string) {
  const invalidate = useHouseMatchesInvalidation(houseId);
  return useMutation({
    mutationFn: (requestId: string) => retractReservation(requestId),
    onSuccess: invalidate,
  });
}

export function useConfirmMoveMutation() {
  const queryClient = useQueryClient();
  const { refreshMe } = useSession();
  return useMutation({
    mutationFn: ({ requestId, newAdminId }: { requestId: string; newAdminId?: string }) =>
      confirmMove(requestId, newAdminId),
    onSuccess: async () => {
      await refreshMe();
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.myMatchRequests }),
        queryClient.invalidateQueries({ queryKey: ['house'] }),
        queryClient.invalidateQueries({ queryKey: queryKeys.houses }),
      ]);
    },
  });
}

export function useDeclineMoveMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: string) => declineMove(requestId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.myMatchRequests }),
  });
}

/** Relógio da tela para contagens regressivas (re-renderiza a cada `intervalMs`). */
export function useNow(intervalMs = 15_000): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs);
    return () => window.clearInterval(timer);
  }, [intervalMs]);
  return now;
}

// ---------------------------------------------------------------------------
// Formatação
// ---------------------------------------------------------------------------

/** "8 meses no Nosso Teto"; sem histórico (`null`): "Novo no Nosso Teto". */
export function formatResidence(months: number | null | undefined): string {
  if (months === null || months === undefined) return 'Novo no Nosso Teto';
  if (months === 1) return '1 mês no Nosso Teto';
  return `${months} meses no Nosso Teto`;
}

export function formatPoints(points: number | null | undefined): string {
  return `${(points ?? 0).toLocaleString('pt-BR')} pts`;
}

/** Tempo até o prazo: "47 h 05 min". Prazo vencido devolve `null`. */
export function formatRemaining(deadline: Date, now: number): string | null {
  const ms = deadline.getTime() - now;
  if (!Number.isFinite(ms) || ms <= 0) return null;
  const totalMinutes = Math.floor(ms / 60_000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return `${hours} h ${String(minutes).padStart(2, '0')} min`;
}

const deadlineFormatter = new Intl.DateTimeFormat('pt-BR', {
  timeZone: 'America/Sao_Paulo',
  weekday: 'long',
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
});

/** Prazo no horário de Brasília: "domingo, 29/11, 09:00". */
export function formatDeadline(deadline: Date): string {
  return deadlineFormatter.format(deadline);
}

/** Mensagem pt-BR de uma falha (ApiError já vem traduzido). */
export function describeError(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  return translateApiError('');
}

export function myVoteText(vote: Vote | null): string {
  if (vote === 'APPROVE') return 'Seu voto: aprovar. Você pode trocar enquanto a votação estiver aberta.';
  if (vote === 'REJECT') return 'Seu voto: rejeitar. Você pode trocar enquanto a votação estiver aberta.';
  return 'Você ainda não votou.';
}
