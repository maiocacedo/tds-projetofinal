import { LoaderCircle } from 'lucide-react';
import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { PERMISSIONS } from '../api/me';
import { useSession } from '../auth/SessionContext';
import { NoAccessPage } from './NoAccessPage';
import {
  getHomePathForUser,
  isAdminPath,
  paths,
  routeAccess,
  type AppPath,
} from './paths';

export function SessionLoading() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="flex min-h-[50vh] items-center justify-center gap-2 text-texto-suave"
    >
      <LoaderCircle aria-hidden="true" className="size-5 animate-spin" />
      <span>Carregando sua sessão…</span>
    </div>
  );
}

/** `/entrar` e `/cadastro`: quem já está logado vai para a home do estado. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { status, user } = useSession();
  if (status === 'loading') return <SessionLoading />;
  if (status === 'authenticated' && user) {
    return <Navigate to={getHomePathForUser(user)} replace />;
  }
  return <>{children}</>;
}

/**
 * `/`: a landing aparece na hora (inclusive enquanto a sessão carrega); só
 * quem já está logado é levado para a home do estado.
 */
export function LandingGuard({ children }: { children: ReactNode }) {
  const { status, user } = useSession();
  if (status === 'authenticated' && user) {
    return <Navigate to={getHomePathForUser(user)} replace />;
  }
  return <>{children}</>;
}

/** Rota desconhecida: home do estado se logado, landing se anônimo. */
export function UnknownRoute() {
  const { status, user } = useSession();
  if (status === 'loading') return <SessionLoading />;
  if (status === 'authenticated' && user) {
    return <Navigate to={getHomePathForUser(user)} replace />;
  }
  return <Navigate to={paths.landing} replace />;
}

interface RequireAuthProps {
  /** Padrão da rota (chave de `routeAccess`), ex.: `/casas/:id`. */
  route: AppPath;
  children: ReactNode;
}

/**
 * Ordem: carregando → sem sessão → boas-vindas pendentes → ficha incompleta
 * (isenção de /admin/** para ROLE_ADMIN_USERS) → casa exigida → permissão.
 * Todo redirect leva a `getHomePathForUser(user)` ou a um passo anterior dele,
 * e a home de cada estado é estável (coberto por teste de invariante).
 */
export function RequireAuth({ route, children }: RequireAuthProps) {
  const { status, user, hasPermission } = useSession();
  const access = routeAccess[route];

  if (status === 'loading') return <SessionLoading />;
  if (status !== 'authenticated' || !user) {
    return <Navigate to={paths.landing} replace />;
  }

  // Boas-vindas pendentes: só /bem-vindo, liberado sem as demais checagens
  // (senão welcome + ficha incompleta ficaria pulando entre as duas rotas).
  if (user.pendingWelcome) {
    return route === paths.bemVindo ? <>{children}</> : <Navigate to={paths.bemVindo} replace />;
  }

  if (!user.profileComplete && route !== paths.onboarding) {
    // Pelo padrão da rota casada (não pela URL): o router casa sem diferenciar
    // maiúsculas, então /ADMIN/usuarios também é rota de admin.
    const adminExempt =
      isAdminPath(route) && hasPermission(PERMISSIONS.ADMIN_USERS);
    if (!adminExempt) {
      return <Navigate to={paths.onboarding} replace />;
    }
  }

  if (access.requiresHouse && !user.houseId) {
    return <Navigate to={getHomePathForUser(user)} replace />;
  }

  if (access.permission && !hasPermission(access.permission)) {
    return <NoAccessPage />;
  }

  return <>{children}</>;
}
