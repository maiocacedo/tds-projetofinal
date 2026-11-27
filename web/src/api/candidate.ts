import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { request } from '../auth/apiClient';
import { useSession } from '../auth/SessionContext';
import {
  houseSchema,
  useHouseDetailQuery,
  useHousesQuery,
  votingRuleSchema,
  type House,
  type HouseFilters,
} from './houses';
import type { UserProfile } from './me';
import { queryKeys } from './queryKeys';

export { useHouseDetailQuery, useHousesQuery, type House, type HouseFilters };

// LocalDateTime sem offset (ex.: "2026-11-27T14:30:00"): nunca usar .datetime() puro.
export const myMatchRequestSchema = z.object({
  id: z.string(),
  houseId: z.string(),
  houseName: z.string(),
  /** Aberto como string porque B6 acrescenta AWAITING_MOVE e EXPIRED. */
  status: z.string(),
  createdAt: z.string(),
  votesCast: z.number().int(),
  votersTotal: z.number().int(),
  moveDeadline: z.string().nullable().default(null),
});

export type MyMatchRequest = z.infer<typeof myMatchRequestSchema>;

export const matchRequestResponseSchema = z.object({
  id: z.string(),
  userId: z.string().optional(),
  userName: z.string().optional(),
  houseId: z.string(),
  status: z.string(),
  compatibilityScore: z.number().nullable().default(null),
  createdAt: z.string(),
});

export type MatchRequestResponse = z.infer<typeof matchRequestResponseSchema>;

export const cancelOtherRequestsResponseSchema = z.object({
  cancelledCount: z.number().int().default(0),
});

export type CancelOtherRequestsResponse = z.infer<
  typeof cancelOtherRequestsResponseSchema
>;

export const createHouseInputSchema = z.object({
  name: z.string().trim().min(2, 'Informe o nome da casa (pelo menos 2 caracteres).'),
  address: z.string().trim().optional(),
  description: z.string().trim().optional(),
  basicRules: z.string().trim().optional(),
  rentAmount: z
    .number()
    .min(0, 'O aluguel não pode ser negativo.')
    .nullable()
    .optional(),
  vacancies: z
    .number()
    .int('Use um número inteiro de vagas.')
    .min(0, 'As vagas não podem ser negativas.'),
  allowsPets: z.boolean(),
  allowsSmokers: z.boolean(),
  expectedOrganizationLevel: z.number().int().min(1).max(5),
  visitorTolerance: z.number().int().min(1).max(5),
  hasFurniture: z.boolean(),
  votingRule: votingRuleSchema,
  isAcceptingMatches: z.boolean(),
});

export type CreateHouseInput = z.infer<typeof createHouseInputSchema>;

// ---------------------------------------------------------------------------
// Compatibilidade — paridade 100% exata com CompatibilityCalculator.java (e B4b)
// ---------------------------------------------------------------------------

export interface CompatibilityProfileInput {
  budget?: number | null;
  hasPets?: boolean | null;
  isSmoker?: boolean | null;
  organizationLevel?: number | null;
  visitorTolerance?: number | null;
}

export interface CompatibilityHouseInput {
  rentAmount?: number | null;
  allowsPets?: boolean | null;
  allowsSmokers?: boolean | null;
  expectedOrganizationLevel?: number | null;
  visitorTolerance?: number | null;
}

export type CriterionKey = 'budget' | 'pets' | 'smoker' | 'organization' | 'visitors';
export type CriterionStatus = 'match' | 'partial' | 'mismatch';

export interface CompatibilityCriterion {
  key: CriterionKey;
  label: string;
  points: number;
  maxPoints: 20;
  status: CriterionStatus;
  summary: string;
}

export interface CompatibilityExplanation {
  score: number | null;
  criteria: CompatibilityCriterion[];
}

export function isProfileComplete(
  profile: CompatibilityProfileInput | null | undefined,
): boolean {
  if (!profile) return false;
  return (
    profile.budget != null &&
    profile.hasPets != null &&
    profile.isSmoker != null &&
    profile.organizationLevel != null &&
    profile.visitorTolerance != null
  );
}

function formatCurrency(value: number): string {
  return value.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: value % 1 === 0 ? 0 : 2,
  });
}

function statusFromPoints(points: number): CriterionStatus {
  if (points >= 20) return 'match';
  if (points > 0) return 'partial';
  return 'mismatch';
}

/**
 * Espelha exatamente as 5 regras e pesos (20 pts cada) de
 * `com.nossoteto.service.CompatibilityCalculator`:
 * - Ficha incompleta ou casa ausente -> `{ score: null, criteria: [] }` (nunca 0%).
 * - Orçamento (20): casa sem aluguel ou orçamento >= aluguel -> 20, senão 0.
 * - Pets (20): perfil tem pet E casa não aceita -> 0, senão 20.
 * - Fumante (20): perfil fuma E casa não aceita -> 0, senão 20.
 * - Organização (20): `max(0, 20 - |perfil - casa| * 5)` (ou 20 se casa null).
 * - Visitas (20): `max(0, 20 - |perfil - casa| * 5)` (ou 20 se casa null).
 */
export function explainCompatibility(
  profile: CompatibilityProfileInput | UserProfile | null | undefined,
  house: CompatibilityHouseInput | House | null | undefined,
): CompatibilityExplanation {
  if (!house || !isProfileComplete(profile)) {
    return { score: null, criteria: [] };
  }

  const p = profile as Required<{
    budget: number;
    hasPets: boolean;
    isSmoker: boolean;
    organizationLevel: number;
    visitorTolerance: number;
  }>;

  // 1. Orçamento (20 pts)
  const budgetOk = house.rentAmount == null || p.budget >= house.rentAmount;
  const budgetPoints = budgetOk ? 20 : 0;
  const budgetSummary =
    house.rentAmount == null
      ? 'A casa ainda não informou valor de aluguel.'
      : budgetOk
        ? `Aluguel de ${formatCurrency(house.rentAmount)} cabe no seu orçamento de ${formatCurrency(p.budget)}.`
        : `Aluguel de ${formatCurrency(house.rentAmount)} passa do seu orçamento de ${formatCurrency(p.budget)}.`;

  // 2. Pets (20 pts)
  const petsConflict = p.hasPets === true && house.allowsPets === false;
  const petsPoints = petsConflict ? 0 : 20;
  const petsSummary = petsConflict
    ? 'Você tem pet, mas a casa não aceita animais.'
    : p.hasPets
      ? 'Você tem pet e a casa aceita animais.'
      : house.allowsPets === false
        ? 'Você não tem pet e a casa não recebe animais.'
        : 'Você não tem pet; convivência tranquila com a regra da casa.';

  // 3. Fumante (20 pts)
  const smokerConflict = p.isSmoker === true && house.allowsSmokers === false;
  const smokerPoints = smokerConflict ? 0 : 20;
  const smokerSummary = smokerConflict
    ? 'Você fuma, mas a casa não permite fumantes.'
    : p.isSmoker
      ? 'Você fuma e a casa permite fumantes.'
      : house.allowsSmokers === false
        ? 'Você não fuma e a casa também não permite fumar.'
        : 'Você não fuma; regra compatível com a sua ficha.';

  // 4. Nível de organização (20 pts)
  let orgPoints = 20;
  let orgSummary = 'A casa não definiu exigência de organização.';
  if (house.expectedOrganizationLevel != null) {
    const diff = Math.abs(p.organizationLevel - house.expectedOrganizationLevel);
    orgPoints = Math.max(0, 20 - diff * 5);
    orgSummary =
      diff === 0
        ? `Vocês combinam no nível ${p.organizationLevel}/5 de organização.`
        : `Sua ficha é ${p.organizationLevel}/5 e a casa espera ${house.expectedOrganizationLevel}/5 (diferença de ${diff}).`;
  }

  // 5. Tolerância a visitas (20 pts)
  let visitorsPoints = 20;
  let visitorsSummary = 'A casa não definiu preferência para visitas.';
  if (house.visitorTolerance != null) {
    const diff = Math.abs(p.visitorTolerance - house.visitorTolerance);
    visitorsPoints = Math.max(0, 20 - diff * 5);
    visitorsSummary =
      diff === 0
        ? `Mesma abertura para visitas: nível ${p.visitorTolerance}/5.`
        : `Sua ficha é ${p.visitorTolerance}/5 e a casa é ${house.visitorTolerance}/5 (diferença de ${diff}).`;
  }

  const criteria: CompatibilityCriterion[] = [
    {
      key: 'budget',
      label: 'Orçamento',
      points: budgetPoints,
      maxPoints: 20,
      status: statusFromPoints(budgetPoints),
      summary: budgetSummary,
    },
    {
      key: 'pets',
      label: 'Pets',
      points: petsPoints,
      maxPoints: 20,
      status: statusFromPoints(petsPoints),
      summary: petsSummary,
    },
    {
      key: 'smoker',
      label: 'Cigarro',
      points: smokerPoints,
      maxPoints: 20,
      status: statusFromPoints(smokerPoints),
      summary: smokerSummary,
    },
    {
      key: 'organization',
      label: 'Organização',
      points: orgPoints,
      maxPoints: 20,
      status: statusFromPoints(orgPoints),
      summary: orgSummary,
    },
    {
      key: 'visitors',
      label: 'Visitas',
      points: visitorsPoints,
      maxPoints: 20,
      status: statusFromPoints(visitorsPoints),
      summary: visitorsSummary,
    },
  ];

  const score = criteria.reduce((sum, item) => sum + item.points, 0);
  return { score, criteria };
}

/**
 * Resolve o score exibido para o usuário logado sem nunca inventar 0%
 * quando a ficha está incompleta ou a API devolveu `null`.
 */
export function resolveHouseCompatibilityScore(
  profile: UserProfile | null | undefined,
  profileComplete: boolean,
  house: House,
): number | null {
  if (!profileComplete || house.compatibilityScore === null) {
    return null;
  }
  const explained = explainCompatibility(profile, house);
  return explained.score ?? house.compatibilityScore;
}

// ---------------------------------------------------------------------------
// Chamadas HTTP e hooks TanStack Query
// ---------------------------------------------------------------------------

export function fetchMyMatchRequests(): Promise<MyMatchRequest[]> {
  return request('/api/matches/requests/mine', {
    schema: z.array(myMatchRequestSchema),
  });
}

export function createHouse(payload: CreateHouseInput): Promise<House> {
  return request('/api/houses', {
    method: 'POST',
    json: payload,
    schema: houseSchema,
  });
}

export function createMatchRequest(houseId: string): Promise<MatchRequestResponse> {
  return request('/api/matches/requests', {
    method: 'POST',
    json: { houseId },
    schema: matchRequestResponseSchema,
  });
}

export function withdrawMatchRequest(requestId: string): Promise<void> {
  return request(`/api/matches/requests/${encodeURIComponent(requestId)}`, {
    method: 'DELETE',
  });
}

export function cancelOtherMatchRequests(): Promise<CancelOtherRequestsResponse> {
  return request('/api/matches/requests/cancel-others', {
    method: 'POST',
    schema: cancelOtherRequestsResponseSchema,
  });
}

export function acknowledgeWelcome(): Promise<void> {
  return request('/api/users/me/welcome-ack', {
    method: 'POST',
  });
}

export function useMyMatchRequestsQuery(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.myMatchRequests,
    queryFn: fetchMyMatchRequests,
    enabled: options.enabled,
  });
}

export function useCreateHouseMutation() {
  const queryClient = useQueryClient();
  const { refreshMe } = useSession();

  return useMutation({
    mutationFn: createHouse,
    onSuccess: async () => {
      await refreshMe();
      await queryClient.invalidateQueries({ queryKey: queryKeys.houses });
    },
  });
}

export function useCreateMatchRequestMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createMatchRequest,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.myMatchRequests });
    },
  });
}

export function useWithdrawMatchRequestMutation() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: withdrawMatchRequest,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.myMatchRequests });
    },
  });
}

export function useCancelOtherMatchRequestsMutation() {
  const queryClient = useQueryClient();
  const { refreshMe } = useSession();

  return useMutation({
    mutationFn: cancelOtherMatchRequests,
    onSuccess: async () => {
      await refreshMe();
      await queryClient.invalidateQueries({ queryKey: queryKeys.myMatchRequests });
    },
  });
}

export function useAcknowledgeWelcomeMutation() {
  const { refreshMe } = useSession();

  return useMutation({
    mutationFn: acknowledgeWelcome,
    onSuccess: async () => {
      await refreshMe();
    },
  });
}
