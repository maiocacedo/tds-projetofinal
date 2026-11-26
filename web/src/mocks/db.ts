import type { House } from '../api/houses';
import { PERMISSIONS, type UserMe } from '../api/me';

// Estado mutável compartilhado pelos handlers MSW. Telas F3–F11 podem alterar
// `db` nos próprios testes/handlers sem editar `handlers/core.ts`.
// `resetMockDb()` roda no afterEach de `test/setup.ts`.

export interface MockUser extends UserMe {
  password: string;
}

export interface MockSession {
  /** Dono do cookie de refresh (simulado: o Node não guarda Set-Cookie). */
  userId: string | null;
  /** O cookie de refresh existe e a família não foi revogada. */
  refreshValid: boolean;
  /** Valor atual do refresh (rotaciona a cada uso). */
  refreshToken: string | null;
  /** Único access token aceito pelos handlers `/api/**`. */
  accessToken: string | null;
  /** Quantas vezes `grant_type=refresh_token` chegou ao servidor. */
  refreshCalls: number;
  tokenSeq: number;
}

export interface MockDb {
  users: MockUser[];
  houses: House[];
  session: MockSession;
}

export const MOCK_PASSWORD = 'senha-forte-123';
export const MOCK_CANDIDATO_ID = 'user-ana';
export const MOCK_MORADOR_ID = 'user-bruno';
export const MOCK_ADMIN_ID = 'user-admin';
export const HOUSE_JABUTICABA_ID = 'house-jabuticaba';
export const HOUSE_VERDE_ID = 'house-verde';

export const USER_PERMISSIONS: readonly string[] = [
  PERMISSIONS.MANAGE_PROFILE,
  PERMISSIONS.SEARCH_HOUSES,
  PERMISSIONS.MANAGE_HOUSE,
  PERMISSIONS.MATCH,
  PERMISSIONS.MANAGE_EXPENSES,
  PERMISSIONS.MANAGE_CHORES,
  PERMISSIONS.MANAGE_GROCERIES,
  PERMISSIONS.VIEW_DASHBOARD,
];

export const ADMIN_PERMISSIONS: readonly string[] = [
  ...USER_PERMISSIONS,
  PERMISSIONS.ADMIN_USERS,
];

const completeProfile = {
  budget: 900,
  hasPets: false,
  isSmoker: false,
  profession: 'Estudante',
  bio: 'Curto cozinhar e silêncio depois das 22h.',
  allergies: null,
  organizationLevel: 4,
  visitorTolerance: 3,
  hasFurniture: true,
};

const seed: MockDb = {
  users: [
    {
      id: MOCK_CANDIDATO_ID,
      name: 'Ana Souza',
      email: 'ana@nossoteto.dev',
      password: MOCK_PASSWORD,
      houseId: null,
      houseName: null,
      isHouseAdmin: null,
      profile: completeProfile,
      profileComplete: true,
      permissions: [...USER_PERMISSIONS],
      points: { total: 0, thisMonth: 0 },
      residenceMonths: null,
      pendingWelcome: false,
    },
    {
      id: MOCK_MORADOR_ID,
      name: 'Bruno Lima',
      email: 'bruno@nossoteto.dev',
      password: MOCK_PASSWORD,
      houseId: HOUSE_JABUTICABA_ID,
      houseName: 'República Jabuticaba',
      isHouseAdmin: true,
      profile: { ...completeProfile, profession: 'Designer' },
      profileComplete: true,
      permissions: [...USER_PERMISSIONS],
      points: { total: 120, thisMonth: 35 },
      residenceMonths: 14,
      pendingWelcome: false,
    },
    {
      id: MOCK_ADMIN_ID,
      name: 'Admin do Sistema',
      email: 'admin@nossoteto.dev',
      password: MOCK_PASSWORD,
      houseId: null,
      houseName: null,
      isHouseAdmin: null,
      profile: null,
      profileComplete: false,
      permissions: [...ADMIN_PERMISSIONS],
      points: { total: 0, thisMonth: 0 },
      residenceMonths: null,
      pendingWelcome: false,
    },
  ],
  houses: [
    {
      id: HOUSE_JABUTICABA_ID,
      name: 'República Jabuticaba',
      address: 'Rua das Jabuticabeiras, 120 — Centro',
      description: 'Casa grande com quintal e horta.',
      basicRules: 'Silêncio depois das 22h. Louça lavada no mesmo dia.',
      rentAmount: 850,
      allowsPets: true,
      allowsSmokers: false,
      expectedOrganizationLevel: 4,
      visitorTolerance: 3,
      hasFurniture: true,
      votingRule: 'MAJORITY',
      isAcceptingMatches: true,
      compatibilityScore: 87,
      vacancies: 1,
      members: [
        {
          id: MOCK_MORADOR_ID,
          name: 'Bruno Lima',
          email: 'bruno@nossoteto.dev',
          isHouseAdmin: true,
        },
        {
          id: 'user-carla',
          name: 'Carla Mendes',
          email: 'carla@nossoteto.dev',
          isHouseAdmin: false,
        },
      ],
    },
    {
      id: HOUSE_VERDE_ID,
      name: 'Casa Verde',
      address: 'Av. dos Ipês, 45 — Jardim Botânico',
      description: 'Sobrado perto da universidade.',
      basicRules: 'Visitas avisadas no grupo.',
      rentAmount: 700,
      allowsPets: false,
      allowsSmokers: true,
      expectedOrganizationLevel: 3,
      visitorTolerance: 4,
      hasFurniture: false,
      votingRule: 'UNANIMITY',
      isAcceptingMatches: true,
      compatibilityScore: 64,
      vacancies: 2,
      members: [
        {
          id: 'user-diego',
          name: 'Diego Ramos',
          email: 'diego@nossoteto.dev',
          isHouseAdmin: true,
        },
      ],
    },
  ],
  session: {
    userId: null,
    refreshValid: false,
    refreshToken: null,
    accessToken: null,
    refreshCalls: 0,
    tokenSeq: 0,
  },
};

export const db: MockDb = structuredClone(seed);

export function resetMockDb(): void {
  const fresh = structuredClone(seed);
  db.users = fresh.users;
  db.houses = fresh.houses;
  db.session = fresh.session;
}

export function findMockUser(id: string | null): MockUser | undefined {
  return id ? db.users.find((u) => u.id === id) : undefined;
}

/**
 * Simula um cookie de refresh válido para `userId` (padrão: candidata Ana),
 * aplicando `patch` ao usuário. O próximo boot do SessionProvider entra logado.
 */
export function seedSession(
  patch: Partial<MockUser> = {},
  userId: string = MOCK_CANDIDATO_ID,
): MockUser {
  const user = findMockUser(userId);
  if (!user) throw new Error(`Mock user "${userId}" not found`);
  Object.assign(user, patch);
  db.session.userId = userId;
  db.session.refreshValid = true;
  db.session.refreshToken = null;
  db.session.accessToken = null;
  return user;
}
