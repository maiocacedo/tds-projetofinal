// Chaves centralizadas para invalidação cruzada entre telas
// (ex.: "Paguei" em Contas invalida `dashboard(h)` e `points`).
export const queryKeys = {
  /** Espelho do usuário da sessão. Para mudar o estado do guard use `useSession().refreshMe()`. */
  me: ['me'] as const,
  points: ['me', 'points'] as const,
  houses: ['houses'] as const,
  house: (id: string) => ['house', id, 'detail'] as const,
  myMatchRequests: ['matches', 'mine'] as const,
  houseMatches: (houseId: string) => ['house', houseId, 'matches'] as const,
  houseRemovals: (houseId: string) => ['house', houseId, 'removals'] as const,
  expenses: (houseId: string) => ['house', houseId, 'expenses'] as const,
  expenseSummary: (houseId: string) =>
    ['house', houseId, 'expenses', 'summary'] as const,
  chores: (houseId: string) => ['house', houseId, 'chores'] as const,
  groceries: (houseId: string) => ['house', houseId, 'groceries'] as const,
  dashboard: (houseId: string) => ['house', houseId, 'dashboard'] as const,
  adminUsers: ['admin', 'users'] as const,
  adminPermissions: ['admin', 'permissions'] as const,
};
