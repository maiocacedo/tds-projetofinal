import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { z } from 'zod';
import { request } from '../auth/apiClient';
import { queryKeys } from './queryKeys';

// Contrato das tarefas (B9). A API serializa com NON_NULL: campos nulos chegam
// omitidos, então todo campo opcional aceita `undefined` e vira `null`.

export const CHORE_FREQUENCIES = ['ONCE', 'TWICE', 'THRICE', 'DAILY', 'BIWEEKLY'] as const;
export const choreFrequencySchema = z.enum(CHORE_FREQUENCIES);
export type ChoreFrequency = z.infer<typeof choreFrequencySchema>;

export const CHORE_FREQUENCY_LABELS: Record<ChoreFrequency, string> = {
  ONCE: '1× por semana',
  TWICE: '2× por semana',
  THRICE: '3× por semana',
  DAILY: 'Todo dia (7×)',
  BIWEEKLY: 'A cada 2 semanas',
};

export const choreTypeSchema = z.enum(['ROTATIONAL', 'FIXED']);
export type ChoreType = z.infer<typeof choreTypeSchema>;

export const choreStatusSchema = z.enum(['PENDING', 'COMPLETED', 'DELAYED']);
export type ChoreStatus = z.infer<typeof choreStatusSchema>;

export const choreSwapStatusSchema = z.enum(['PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED']);
export type ChoreSwapStatus = z.infer<typeof choreSwapStatusSchema>;

const nullableString = z
  .string()
  .nullish()
  .transform((value) => value ?? null);

export const choreSwapSchema = z.object({
  id: z.string(),
  houseId: z.string(),
  requesterChoreId: z.string(),
  requesterChoreDescription: nullableString,
  targetChoreId: z.string(),
  targetChoreDescription: nullableString,
  requesterId: z.string(),
  requesterName: nullableString,
  targetUserId: z.string(),
  targetUserName: nullableString,
  status: choreSwapStatusSchema,
  cycleNumber: z.number().int(),
  /** `LocalDateTime` sem offset: não é ISO com fuso, então não usa `.datetime()`. */
  createdAt: z.string(),
});

export type ChoreSwapDto = z.infer<typeof choreSwapSchema>;

export const choreResponseSchema = z
  .object({
    id: z.string(),
    houseId: z.string(),
    description: z.string(),
    /** O DTO Java serializa `choreType`; `type` também é aceito. */
    choreType: choreTypeSchema.optional(),
    type: choreTypeSchema.optional(),
    status: choreStatusSchema,
    frequency: choreFrequencySchema.default('ONCE'),
    requiredCompletions: z.number().int().default(1),
    completionsThisCycle: z.number().int().default(0),
    delayedThisCycle: z.boolean().default(false),
    linkedToGroceries: z.boolean().default(false),
    currentAssigneeId: nullableString,
    currentAssigneeName: nullableString,
    effectiveAssigneeId: nullableString,
    effectiveAssigneeName: nullableString,
    temporaryAssigneeId: nullableString,
    temporaryAssigneeName: nullableString,
    nextAssigneeId: nullableString,
    nextAssigneeName: nullableString,
    rotationOrder: z
      .array(z.string())
      .nullish()
      .transform((value) => value ?? []),
    cycleNumber: z.number().int().default(1),
    reopenedById: nullableString,
    reopenedByName: nullableString,
    /** `LocalDateTime` sem offset. */
    reopenedAt: nullableString,
    pendingSwaps: z
      .array(choreSwapSchema)
      .nullish()
      .transform((value) => value ?? []),
    pendingSwap: choreSwapSchema.nullish().transform((value) => value ?? null),
  })
  .transform(({ choreType, type, ...chore }, ctx) => {
    const resolvedType = choreType ?? type;
    if (!resolvedType) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'chore type is required' });
      return z.NEVER;
    }
    return {
      ...chore,
      type: resolvedType,
      effectiveAssigneeId:
        chore.effectiveAssigneeId ?? chore.temporaryAssigneeId ?? chore.currentAssigneeId,
      effectiveAssigneeName:
        chore.effectiveAssigneeName ?? chore.temporaryAssigneeName ?? chore.currentAssigneeName,
    };
  });

export type ChoreResponse = z.infer<typeof choreResponseSchema>;

export interface CreateChoreInput {
  description: string;
  choreType: ChoreType;
  assigneeId?: string;
  frequency: ChoreFrequency;
  rotationOrder?: string[];
  linkedToGroceries?: boolean;
}

// ---------------------------------------------------------------------------
// Requisições
// ---------------------------------------------------------------------------

const enc = encodeURIComponent;
const choresPath = (houseId: string) => `/api/houses/${enc(houseId)}/chores`;
const chorePath = (houseId: string, choreId: string) =>
  `${choresPath(houseId)}/${enc(choreId)}`;

export function fetchChores(houseId: string): Promise<ChoreResponse[]> {
  return request(choresPath(houseId), { schema: z.array(choreResponseSchema) });
}

export function fetchChoreSwaps(houseId: string): Promise<ChoreSwapDto[]> {
  return request(`${choresPath(houseId)}/swaps`, { schema: z.array(choreSwapSchema) });
}

export function createChore(houseId: string, input: CreateChoreInput): Promise<ChoreResponse> {
  return request(choresPath(houseId), {
    method: 'POST',
    json: input,
    schema: choreResponseSchema,
  });
}

export function completeChore(houseId: string, choreId: string): Promise<ChoreResponse> {
  return request(`${chorePath(houseId, choreId)}/complete`, {
    method: 'PUT',
    schema: choreResponseSchema,
  });
}

export function uncompleteChore(houseId: string, choreId: string): Promise<ChoreResponse> {
  return request(`${chorePath(houseId, choreId)}/uncomplete`, {
    method: 'PUT',
    schema: choreResponseSchema,
  });
}

export function reopenChore(houseId: string, choreId: string): Promise<ChoreResponse> {
  return request(`${chorePath(houseId, choreId)}/reopen`, {
    method: 'PUT',
    schema: choreResponseSchema,
  });
}

export function updateChoreRotation(
  houseId: string,
  choreId: string,
  order: string[],
): Promise<ChoreResponse> {
  return request(`${chorePath(houseId, choreId)}/rotation`, {
    method: 'PUT',
    json: { order },
    schema: choreResponseSchema,
  });
}

export function createChoreSwap(
  houseId: string,
  choreId: string,
  targetChoreId: string,
): Promise<ChoreSwapDto> {
  return request(`${chorePath(houseId, choreId)}/swaps`, {
    method: 'POST',
    json: { targetChoreId },
    schema: choreSwapSchema,
  });
}

export function answerChoreSwap(
  houseId: string,
  swapId: string,
  answer: 'accept' | 'reject',
): Promise<ChoreSwapDto> {
  return request(`${choresPath(houseId)}/swaps/${enc(swapId)}/${answer}`, {
    method: 'PUT',
    schema: choreSwapSchema,
  });
}

/** Admin fecha o ciclo antes do domingo (credita +20 às feitas no prazo). */
export function rotateChores(houseId: string): Promise<ChoreResponse[]> {
  return request(`${choresPath(houseId)}/rotate`, {
    method: 'POST',
    schema: z.array(choreResponseSchema),
  });
}

// ---------------------------------------------------------------------------
// Hooks
// ---------------------------------------------------------------------------

/** Chave das trocas: prefixada por `chores(h)`, então invalidar tarefas também as recarrega. */
export const choreSwapsKey = (houseId: string) => [...queryKeys.chores(houseId), 'swaps'] as const;

export function useChoresQuery(houseId: string | null | undefined) {
  return useQuery({
    queryKey: queryKeys.chores(houseId ?? ''),
    queryFn: () => fetchChores(houseId as string),
    enabled: Boolean(houseId),
  });
}

export function useChoreSwapsQuery(houseId: string | null | undefined) {
  return useQuery({
    queryKey: choreSwapsKey(houseId ?? ''),
    queryFn: () => fetchChoreSwaps(houseId as string),
    enabled: Boolean(houseId),
  });
}

function useInvalidateChores(houseId: string) {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: queryKeys.chores(houseId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.dashboard(houseId) }),
      queryClient.invalidateQueries({ queryKey: queryKeys.points }),
    ]);
}

export function useCreateChoreMutation(houseId: string) {
  const invalidate = useInvalidateChores(houseId);
  return useMutation({
    mutationFn: (input: CreateChoreInput) => createChore(houseId, input),
    onSuccess: invalidate,
  });
}

/** Aplica ±1 conclusão no cache na hora: o item "conclui no lugar" sem esperar a rede. */
function useOptimisticCompletion(
  houseId: string,
  delta: 1 | -1,
  mutationFn: (choreId: string) => Promise<ChoreResponse>,
) {
  const queryClient = useQueryClient();
  const invalidate = useInvalidateChores(houseId);
  const key = queryKeys.chores(houseId);
  return useMutation({
    mutationFn,
    onMutate: async (choreId: string) => {
      await queryClient.cancelQueries({ queryKey: key, exact: true });
      const previous = queryClient.getQueryData<ChoreResponse[]>(key);
      queryClient.setQueryData<ChoreResponse[]>(key, (list) =>
        list?.map((chore) => {
          if (chore.id !== choreId) return chore;
          const completions = Math.min(
            chore.requiredCompletions,
            Math.max(0, chore.completionsThisCycle + delta),
          );
          const status: ChoreStatus =
            completions >= chore.requiredCompletions
              ? 'COMPLETED'
              : chore.delayedThisCycle
                ? 'DELAYED'
                : 'PENDING';
          return { ...chore, completionsThisCycle: completions, status };
        }),
      );
      return { previous };
    },
    onError: (_error, _choreId, context) => {
      if (context?.previous) queryClient.setQueryData(key, context.previous);
    },
    onSettled: invalidate,
  });
}

export function useCompleteChoreMutation(houseId: string) {
  return useOptimisticCompletion(houseId, 1, (choreId) => completeChore(houseId, choreId));
}

export function useUncompleteChoreMutation(houseId: string) {
  return useOptimisticCompletion(houseId, -1, (choreId) => uncompleteChore(houseId, choreId));
}

export function useReopenChoreMutation(houseId: string) {
  const invalidate = useInvalidateChores(houseId);
  return useMutation({
    mutationFn: (choreId: string) => reopenChore(houseId, choreId),
    onSettled: invalidate,
  });
}

export function useUpdateRotationMutation(houseId: string) {
  const invalidate = useInvalidateChores(houseId);
  return useMutation({
    mutationFn: ({ choreId, order }: { choreId: string; order: string[] }) =>
      updateChoreRotation(houseId, choreId, order),
    onSuccess: invalidate,
  });
}

export function useCreateSwapMutation(houseId: string) {
  const invalidate = useInvalidateChores(houseId);
  return useMutation({
    mutationFn: ({ choreId, targetChoreId }: { choreId: string; targetChoreId: string }) =>
      createChoreSwap(houseId, choreId, targetChoreId),
    onSuccess: invalidate,
  });
}

export function useAnswerSwapMutation(houseId: string) {
  const invalidate = useInvalidateChores(houseId);
  return useMutation({
    mutationFn: ({ swapId, answer }: { swapId: string; answer: 'accept' | 'reject' }) =>
      answerChoreSwap(houseId, swapId, answer),
    onSettled: invalidate,
  });
}

export function useRotateChoresMutation(houseId: string) {
  const invalidate = useInvalidateChores(houseId);
  return useMutation({
    mutationFn: () => rotateChores(houseId),
    onSuccess: invalidate,
  });
}
