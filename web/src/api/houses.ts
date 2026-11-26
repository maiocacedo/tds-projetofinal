import { useQuery } from '@tanstack/react-query';
import { z } from 'zod';
import { request } from '../auth/apiClient';
import { queryKeys } from './queryKeys';

export const houseMemberSchema = z.object({
  id: z.string(),
  name: z.string(),
  /** Só aparece para quem mora na casa (NON_NULL omite do JSON). */
  email: z.string().optional(),
  isHouseAdmin: z.boolean().nullable().default(null),
});

export type HouseMember = z.infer<typeof houseMemberSchema>;

export const votingRuleSchema = z.enum(['UNANIMITY', 'MAJORITY']);

export const houseSchema = z.object({
  id: z.string(),
  name: z.string(),
  address: z.string().nullish(),
  description: z.string().nullish(),
  basicRules: z.string().nullish(),
  /** `null` quando a casa não informou aluguel (NON_NULL omite do JSON). */
  rentAmount: z.number().nullable().default(null),
  allowsPets: z.boolean(),
  allowsSmokers: z.boolean(),
  expectedOrganizationLevel: z.number().int(),
  visitorTolerance: z.number().int(),
  hasFurniture: z.boolean(),
  votingRule: votingRuleSchema,
  isAcceptingMatches: z.boolean(),
  /** Compatibilidade da CASA com o usuário logado; `null` com ficha incompleta. */
  compatibilityScore: z.number().nullable().default(null),
  vacancies: z.number().int(),
  members: z.array(houseMemberSchema).default([]),
});

export type House = z.infer<typeof houseSchema>;

export interface HouseFilters {
  maxRent?: number;
  allowsPets?: boolean;
  allowsSmokers?: boolean;
}

function toQueryString(filters: HouseFilters): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(filters)) {
    if (value !== undefined) params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

export function fetchHouses(filters: HouseFilters = {}): Promise<House[]> {
  return request(`/api/houses${toQueryString(filters)}`, {
    schema: z.array(houseSchema),
  });
}

export function fetchHouse(id: string): Promise<House> {
  return request(`/api/houses/${encodeURIComponent(id)}`, { schema: houseSchema });
}

export function useHousesQuery(filters: HouseFilters = {}) {
  return useQuery({
    queryKey: [...queryKeys.houses, filters],
    queryFn: () => fetchHouses(filters),
  });
}

export function useHouseDetailQuery(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.house(id ?? ''),
    queryFn: () => fetchHouse(id as string),
    enabled: Boolean(id),
  });
}
