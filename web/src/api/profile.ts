import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { request } from '../auth/apiClient';
import { userProfileSchema, type UserProfile } from './me';
import { queryKeys } from './queryKeys';

export interface RegisterPayload {
  name: string;
  email: string;
  password: string;
}

export const registerResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string(),
  // A API envia LocalDateTime sem offset; não usar z.string().datetime().
  createdAt: z.string(),
});

export type RegisterResponse = z.infer<typeof registerResponseSchema>;

export function registerUser(payload: RegisterPayload): Promise<RegisterResponse> {
  return request('/api/auth/register', {
    method: 'POST',
    json: payload,
    schema: registerResponseSchema,
  });
}

export interface PasswordResetPayload {
  email: string;
}

export const passwordResetResponseSchema = z.object({
  demoToken: z.string().optional(),
});

export type PasswordResetResponse = z.infer<typeof passwordResetResponseSchema>;

export function requestPasswordReset(
  payload: PasswordResetPayload,
): Promise<PasswordResetResponse> {
  return request('/api/auth/password-reset', {
    method: 'POST',
    json: payload,
    schema: passwordResetResponseSchema,
  });
}

export interface ConfirmPasswordResetPayload {
  token: string;
  newPassword: string;
}

export function confirmPasswordReset(
  payload: ConfirmPasswordResetPayload,
): Promise<void> {
  return request('/api/auth/password-reset/confirm', {
    method: 'POST',
    json: payload,
  });
}

export interface UpdateProfilePayload {
  budget: number;
  hasPets: boolean;
  isSmoker: boolean;
  organizationLevel: number;
  visitorTolerance: number;
  hasFurniture?: boolean | null;
  profession?: string | null;
  bio?: string | null;
  allergies?: string | null;
}

export function updateMyProfile(
  payload: UpdateProfilePayload,
): Promise<UserProfile> {
  return request('/api/users/me/profile', {
    method: 'PUT',
    json: payload,
    schema: userProfileSchema,
  });
}

export const pointReasonSchema = z.enum([
  'CHORE_ON_TIME',
  'EXPENSE_ON_TIME',
  'GROCERY_BOUGHT',
]);

export type PointReason = z.infer<typeof pointReasonSchema>;

export const pointHistoryItemSchema = z.object({
  id: z.string(),
  reason: pointReasonSchema,
  amount: z.number(),
  createdAt: z.string(),
  description: z.string().nullable().default(null),
});

export type PointHistoryItem = z.infer<typeof pointHistoryItemSchema>;

export const userPointsSchema = z.object({
  total: z.number(),
  thisMonth: z.number(),
  history: z.array(pointHistoryItemSchema).default([]),
});

export type UserPoints = z.infer<typeof userPointsSchema>;

export function fetchMyPoints(): Promise<UserPoints> {
  return request('/api/users/me/points', { schema: userPointsSchema });
}

export function useMyPointsQuery(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.points,
    queryFn: fetchMyPoints,
    enabled: options.enabled,
  });
}

export const POINT_REASON_LABELS: Record<PointReason, string> = {
  CHORE_ON_TIME: 'Tarefa no prazo',
  EXPENSE_ON_TIME: 'Conta paga em dia',
  GROCERY_BOUGHT: 'Item comprado no mercado',
};

export function formatResidenceMonths(months: number | null | undefined): string {
  if (months == null) return 'Novo no Nosso Teto';
  if (months <= 0) return 'Menos de 1 mês';
  if (months === 1) return '1 mês';
  return `${months} meses`;
}
