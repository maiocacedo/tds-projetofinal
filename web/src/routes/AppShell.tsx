import {
  ChevronDown,
  Compass,
  House,
  Inbox,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Receipt,
  ShieldCheck,
  ShoppingBasket,
  UserRound,
  type LucideIcon,
} from 'lucide-react';
import { useEffect, useId, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { PERMISSIONS, type UserMe } from '../api/me';
import { useSession } from '../auth/SessionContext';
import { Avatar } from '../components/Avatar';
import { Logo } from '../components/Logo';
import { useJellyIndicator } from '../motion/useJellyIndicator';
import { getHomePathForUser, paths, routeAccess, type AppPath } from './paths';

interface Tab {
  to: AppPath;
  label: string;
  icon: LucideIcon;
}

const CANDIDATO_TABS: Tab[] = [
  { to: paths.explorar, label: 'Explorar', icon: Compass },
  { to: paths.solicitacoes, label: 'Solicitações', icon: Inbox },
  { to: paths.perfil, label: 'Perfil', icon: UserRound },
];

const MORADOR_TABS: Tab[] = [
  { to: paths.inicio, label: 'Início', icon: LayoutDashboard },
  { to: paths.contas, label: 'Contas', icon: Receipt },
  { to: paths.tarefas, label: 'Tarefas', icon: ListChecks },
  { to: paths.mercado, label: 'Mercado', icon: ShoppingBasket },
  { to: paths.casa, label: 'Casa', icon: House },
];

function visibleTabs(user: UserMe, hasPermission: (p: string) => boolean): Tab[] {
  // Primeiro acesso (ficha / boas-vindas): sem abas.
  if (user.pendingWelcome || !user.profileComplete) return [];
  const tabs = user.houseId ? MORADOR_TABS : CANDIDATO_TABS;
  return tabs.filter((tab) => {
    const permission = routeAccess[tab.to].permission;
    return !permission || hasPermission(permission);
  });
}

function AccountMenu() {
  const { user, hasPermission, logout } = useSession();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  const menuId = useId();

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  const itemClass =
    'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm text-tinta hover:bg-fundo focus-visible:bg-fundo focus-visible:outline-none';

  const handleLogout = async () => {
    setOpen(false);
    await logout();
    navigate(paths.landing, { replace: true });
  };

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        aria-label="Abrir menu da conta"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((value) => !value)}
        className="flex items-center gap-1 rounded-full p-1 text-tinta hover:bg-fundo"
      >
        <span aria-hidden="true" className="inline-flex">
          <Avatar name={user?.name ?? ''} size="sm" />
        </span>
        <span className="sr-only">{user?.name}</span>
        <ChevronDown aria-hidden="true" className="size-4" />
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label="Conta"
          className="absolute right-0 z-20 mt-2 w-52 rounded-xl border border-linha bg-superficie p-1 shadow-lg"
        >
          {hasPermission(PERMISSIONS.MANAGE_PROFILE) && (
            <Link
              role="menuitem"
              to={paths.perfil}
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              <UserRound aria-hidden="true" className="size-4" />
              Perfil
            </Link>
          )}
          {hasPermission(PERMISSIONS.ADMIN_USERS) && (
            <Link
              role="menuitem"
              to={paths.adminUsuarios}
              onClick={() => setOpen(false)}
              className={itemClass}
            >
              <ShieldCheck aria-hidden="true" className="size-4" />
              Administração
            </Link>
          )}
          <button
            role="menuitem"
            type="button"
            onClick={() => void handleLogout()}
            className={itemClass}
          >
            <LogOut aria-hidden="true" className="size-4" />
            Sair
          </button>
        </div>
      )}
    </div>
  );
}

function isTabActive(pathname: string, to: string): boolean {
  return pathname === to || pathname.startsWith(`${to}/`);
}

function TabLink({
  tab,
  compact,
  indicatorReady,
}: {
  tab: Tab;
  compact?: boolean;
  indicatorReady: boolean;
}) {
  const Icon = tab.icon;
  return (
    <NavLink
      to={tab.to}
      data-jelly-key={tab.to}
      className={({ isActive }) =>
        [
          'nt-press relative z-[1] flex min-h-11 items-center rounded-full font-semibold transition-colors duration-300',
          compact
            ? 'flex-1 flex-col justify-center gap-0.5 px-1 py-1.5 text-xs'
            : 'gap-2 px-3 py-1.5 text-sm',
          isActive
            ? // Com o indicador geleia medido, ele pinta o fundo; sem medida
              // (jsdom, primeira pintura), o próprio link pinta.
              indicatorReady
              ? 'text-superficie'
              : 'bg-tinta text-superficie'
            : 'text-texto-suave hover:text-tinta',
        ].join(' ')
      }
    >
      <Icon aria-hidden="true" className={compact ? 'size-5' : 'size-4'} />
      {tab.label}
    </NavLink>
  );
}

/** Lista de abas com um único indicador que desliza em geleia até a ativa. */
function TabList({ tabs, compact }: { tabs: Tab[]; compact?: boolean }) {
  const { pathname } = useLocation();
  const containerRef = useRef<HTMLDivElement>(null);
  const activeKey = tabs.find((tab) => isTabActive(pathname, tab.to))?.to ?? null;
  const indicator = useJellyIndicator(containerRef, activeKey);

  return (
    <div ref={containerRef} className="relative">
      {indicator.ready && (
        <span
          aria-hidden="true"
          className="nt-ind top-0 bottom-0 rounded-full bg-tinta"
          style={indicator.style}
        />
      )}
      <ul className={compact ? 'flex items-stretch gap-1' : 'flex items-center gap-1'}>
        {tabs.map((tab) => (
          <li key={tab.to} className={compact ? 'flex flex-1' : undefined}>
            <TabLink tab={tab} compact={compact} indicatorReady={indicator.ready} />
          </li>
        ))}
      </ul>
    </div>
  );
}

export function AppShell() {
  const { user, hasPermission } = useSession();

  // Sem usuário (carregando ou saindo): só o guard da rota, sem cromo.
  if (!user) return <Outlet />;
  const tabs = visibleTabs(user, hasPermission);
  const home = getHomePathForUser(user);

  return (
    <div className="min-h-screen bg-fundo text-tinta">
      <header className="sticky top-0 z-10 border-b border-linha bg-superficie/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4">
          <Link to={home} className="nt-press rounded-xl">
            <Logo size="sm" />
          </Link>
          {tabs.length > 0 && (
            <nav aria-label="Navegação principal" className="hidden md:block">
              <TabList tabs={tabs} />
            </nav>
          )}
          <AccountMenu />
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 pb-24 pt-6 md:pb-10">
        <Outlet />
      </main>

      {tabs.length > 0 && (
        <nav
          aria-label="Navegação inferior"
          className="fixed inset-x-0 bottom-0 z-10 border-t border-linha bg-superficie md:hidden"
        >
          <div className="px-2 py-1">
            <TabList tabs={tabs} compact />
          </div>
        </nav>
      )}
    </div>
  );
}
