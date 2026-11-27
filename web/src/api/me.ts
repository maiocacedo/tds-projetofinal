import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { request } from '../auth/apiClient';
import { queryKeys } from './queryKeys';

export const PERMISSIONS = {
  MANAGE_PROFILE: 'ROLE_MANAGE_PROFILE',
  SEARCH_HOUSES: 'ROLE_SEARCH_HOUSES',
  MANAGE_HOUSE: 'ROLE_MANAGE_HOUSE',
  MATCH: 'ROLE_MATCH',
  MANAGE_EXPENSES: 'ROLE_MANAGE_EXPENSES',
  MANAGE_CHORES: 'ROLE_MANAGE_CHORES',
  MANAGE_GROCERIES: 'ROLE_MANAGE_GROCERIES',
  VIEW_DASHBOARD: 'ROLE_VIEW_DASHBOARD',
  ADMIN_USERS: 'ROLE_ADMIN_USERS',
} as const;

export type Permission = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

// A API serializa com NON_NULL: campos ausentes chegam omitidos.
export const userProfileSchema = z.object({
  budget: z.number().nullish(),
  hasPets: z.boolean().nullish(),
  isSmoker: z.boolean().nullish(),
  profession: z.string().nullish(),
  bio: z.string().nullish(),
  allergies: z.string().nullish(),
  organizationLevel: z.number().int().nullish(),
  visitorTolerance: z.number().int().nullish(),
  hasFurniture: z.boolean().nullish(),
});

export type UserProfile = z.infer<typeof userProfileSchema>;

export const userMeSchema = z.object({
  id: z.string(),
  name: z.string(),
  email: z.string(),
  houseId: z.string().nullable().default(null),
  houseName: z.string().nullable().default(null),
  isHouseAdmin: z.boolean().nullable().default(null),
  profile: userProfileSchema.nullable().default(null),
  profileComplete: z.boolean(),
  permissions: z.array(z.string()).default([]),
  points: z.object({ total: z.number(), thisMonth: z.number() }),
  residenceMonths: z.number().int().nullable().default(null),
  pendingWelcome: z.boolean().default(false),
});

export type UserMe = z.infer<typeof userMeSchema>;

export function fetchMe(): Promise<UserMe> {
  return request('/api/users/me', { schema: userMeSchema });
}

export function useMeQuery(options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: fetchMe,
    enabled: options.enabled,
  });
}
