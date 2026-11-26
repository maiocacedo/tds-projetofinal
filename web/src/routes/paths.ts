import { PERMISSIONS, type UserMe } from '../api/me';

export const paths = {
  landing: '/',
  sobre: '/sobre',
  entrar: '/entrar',
  cadastro: '/cadastro',
  onboarding: '/onboarding',
  perfil: '/perfil',
  explorar: '/explorar',
  casaDetalhe: '/casas/:id',
  solicitacoes: '/solicitacoes',
  criarCasa: '/casas/nova',
  bemVindo: '/bem-vindo',
  inicio: '/inicio',
  contas: '/contas',
  novaDespesa: '/contas/nova',
  despesaDetalhe: '/contas/:id',
  tarefas: '/tarefas',
  novaTarefa: '/tarefas/nova',
  mercado: '/mercado',
  casa: '/casa',
  candidatos: '/casa/candidatos',
  configurarCasa: '/casa/configurar',
  sairCasa: '/casa/sair',
  mudanca: '/mudanca/:requestId',
  adminUsuarios: '/admin/usuarios',
} as const;

export type AppPath = (typeof paths)[keyof typeof paths];

const enc = encodeURIComponent;

export const buildPath = {
  casaDetalhe: (id: string) => `/casas/${enc(id)}`,
  despesaDetalhe: (id: string) => `/contas/${enc(id)}`,
  mudanca: (requestId: string) => `/mudanca/${enc(requestId)}`,
};

export type RouteKind = 'public' | 'guest' | 'auth';

export interface RouteAccess {
  /** public: sem guard · guest: só sem sessão (/entrar, /cadastro) · auth: logado. */
  kind: RouteKind;
  /** Sem a permissão a rota mostra NoAccessPage e a aba some. */
  permission?: string;
  /** Rotas de morador: sem casa, volta para a home do estado. */
  requiresHouse?: boolean;
}

/**
 * Fonte única de acesso por rota: lida pelo guard e pelas abas do AppShell,
 * para que aba oculta e "sem acesso" nunca discordem.
 */
export const routeAccess: Record<AppPath, RouteAccess> = {
  [paths.landing]: { kind: 'public' },
  [paths.sobre]: { kind: 'public' },
  [paths.entrar]: { kind: 'guest' },
  [paths.cadastro]: { kind: 'guest' },
  [paths.onboarding]: { kind: 'auth', permission: PERMISSIONS.MANAGE_PROFILE },
  [paths.perfil]: { kind: 'auth', permission: PERMISSIONS.MANAGE_PROFILE },
  [paths.explorar]: { kind: 'auth', permission: PERMISSIONS.SEARCH_HOUSES },
  [paths.casaDetalhe]: { kind: 'auth', permission: PERMISSIONS.SEARCH_HOUSES },
  [paths.solicitacoes]: { kind: 'auth', permission: PERMISSIONS.MATCH },
  [paths.criarCasa]: { kind: 'auth', permission: PERMISSIONS.MANAGE_HOUSE },
  [paths.bemVindo]: { kind: 'auth' },
  [paths.inicio]: {
    kind: 'auth',
    permission: PERMISSIONS.VIEW_DASHBOARD,
    requiresHouse: true,
  },
  [paths.contas]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_EXPENSES,
    requiresHouse: true,
  },
  [paths.novaDespesa]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_EXPENSES,
    requiresHouse: true,
  },
  [paths.despesaDetalhe]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_EXPENSES,
    requiresHouse: true,
  },
  [paths.tarefas]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_CHORES,
    requiresHouse: true,
  },
  [paths.novaTarefa]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_CHORES,
    requiresHouse: true,
  },
  [paths.mercado]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_GROCERIES,
    requiresHouse: true,
  },
  [paths.casa]: { kind: 'auth', requiresHouse: true },
  [paths.candidatos]: {
    kind: 'auth',
    permission: PERMISSIONS.MATCH,
    requiresHouse: true,
  },
  [paths.configurarCasa]: {
    kind: 'auth',
    permission: PERMISSIONS.MANAGE_HOUSE,
    requiresHouse: true,
  },
  [paths.sairCasa]: { kind: 'auth', requiresHouse: true },
  [paths.mudanca]: { kind: 'auth', permission: PERMISSIONS.MATCH },
  [paths.adminUsuarios]: { kind: 'auth', permission: PERMISSIONS.ADMIN_USERS },
};

export function isAdminPath(pathname: string): boolean {
  return pathname === '/admin' || pathname.startsWith('/admin/');
}

/** Destino do usuário logado conforme o estado de `/api/users/me`. */
export function getHomePathForUser(user: UserMe): AppPath {
  if (user.pendingWelcome) return paths.bemVindo;
  if (!user.profileComplete) {
    return user.permissions.includes(PERMISSIONS.ADMIN_USERS)
      ? paths.adminUsuarios
      : paths.onboarding;
  }
  if (user.houseId) return paths.inicio;
  return paths.explorar;
}
