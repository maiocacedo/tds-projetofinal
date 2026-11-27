import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { PERMISSIONS, type UserMe } from '../api/me';
import {
  ADMIN_PERMISSIONS,
  MOCK_ADMIN_ID,
  MOCK_MORADOR_ID,
  db,
  seedSession,
} from '../mocks/db';
import { server } from '../mocks/server';
import { renderWithProviders } from '../test/renderWithProviders';
import { getHomePathForUser } from './paths';

function location() {
  return screen.getByTestId('location').textContent;
}

async function expectPage(heading: string | RegExp, path: string) {
  expect(
    await screen.findByRole('heading', { level: 1, name: heading }),
  ).toBeInTheDocument();
  expect(location()).toBe(path);
}

function baseUser(patch: Partial<UserMe> = {}): UserMe {
  return {
    id: 'u',
    name: 'Fulano',
    email: 'f@x.dev',
    houseId: null,
    houseName: null,
    isHouseAdmin: null,
    profile: null,
    profileComplete: true,
    permissions: [],
    points: { total: 0, thisMonth: 0 },
    residenceMonths: null,
    pendingWelcome: false,
    ...patch,
  };
}

describe('getHomePathForUser', () => {
  it('follows the state order', () => {
    expect(getHomePathForUser(baseUser({ pendingWelcome: true, profileComplete: false }))).toBe('/bem-vindo');
    expect(
      getHomePathForUser(
        baseUser({ profileComplete: false, permissions: [PERMISSIONS.ADMIN_USERS] }),
      ),
    ).toBe('/admin/usuarios');
    expect(getHomePathForUser(baseUser({ profileComplete: false }))).toBe('/onboarding');
    expect(getHomePathForUser(baseUser({ houseId: 'h1' }))).toBe('/inicio');
    expect(getHomePathForUser(baseUser())).toBe('/explorar');
  });
});

describe('route guards', () => {
  it('shows a loading state and does not redirect while the session is loading', async () => {
    server.use(
      http.post('/oauth/token', async () => {
        await delay('infinite');
        return HttpResponse.json({});
      }),
    );

    renderWithProviders(undefined, { route: '/inicio' });

    expect(await screen.findByRole('status')).toHaveTextContent(/carregando/i);
    expect(location()).toBe('/inicio');
  });

  it('public pages render without a session', async () => {
    renderWithProviders(undefined, { route: '/sobre' });
    await expectPage(/sobre/i, '/sobre');
  });

  it('anonymous user on an authenticated route goes to the landing', async () => {
    renderWithProviders(undefined, { route: '/inicio' });
    await expectPage(/nosso teto/i, '/');
  });

  it('anonymous user can open /entrar and /cadastro', async () => {
    renderWithProviders(undefined, { route: '/cadastro' });
    await expectPage(/criar conta/i, '/cadastro');
  });

  it('authenticated candidate on /entrar goes to /explorar', async () => {
    seedSession();
    renderWithProviders(undefined, { route: '/entrar' });
    await expectPage('Explorar', '/explorar');
  });

  it('authenticated morador on /cadastro goes to /inicio', async () => {
    seedSession({}, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: '/cadastro' });
    await expectPage('Início', '/inicio');
  });

  it('pendingWelcome sends any authenticated route to /bem-vindo, which stays reachable', async () => {
    seedSession({ pendingWelcome: true }, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: '/contas' });
    await expectPage(/boas-vindas/i, '/bem-vindo');
  });

  it('incomplete profile sends to /onboarding, which never redirects to itself', async () => {
    seedSession({ profileComplete: false });
    renderWithProviders(undefined, { route: '/explorar' });
    await expectPage(/ficha/i, '/onboarding');
  });

  it('admin without ficha reaches /admin/usuarios (Golden Path 6)', async () => {
    seedSession({}, MOCK_ADMIN_ID);
    renderWithProviders(undefined, { route: '/admin/usuarios' });
    await expectPage(/administração/i, '/admin/usuarios');
  });

  it('admin without ficha lands on /admin/usuarios after login routes', async () => {
    seedSession({}, MOCK_ADMIN_ID);
    renderWithProviders(undefined, { route: '/entrar' });
    await expectPage(/administração/i, '/admin/usuarios');
  });

  it('admin without ficha is still sent to /onboarding outside /admin', async () => {
    seedSession({}, MOCK_ADMIN_ID);
    renderWithProviders(undefined, { route: '/explorar' });
    await expectPage(/ficha/i, '/onboarding');
  });

  it('admin exemption follows the matched route, not the URL casing (/ADMIN/usuarios)', async () => {
    seedSession({}, MOCK_ADMIN_ID);
    renderWithProviders(undefined, { route: '/ADMIN/usuarios' });
    await expectPage(/administração/i, '/ADMIN/usuarios');
  });

  describe('landing and unknown routes send a logged-in user to the home of its state', () => {
    const cases: [string, string, Partial<UserMe>, string | RegExp, string][] = [
      ['morador', MOCK_MORADOR_ID, {}, 'Início', '/inicio'],
      ['candidato', 'user-ana', {}, 'Explorar', '/explorar'],
      ['pendingWelcome', MOCK_MORADOR_ID, { pendingWelcome: true }, /boas-vindas/i, '/bem-vindo'],
    ];

    it.each(cases)('%s opening /', async (_label, userId, patch, heading, home) => {
      seedSession(patch, userId);
      renderWithProviders(undefined, { route: '/' });
      await expectPage(heading, home);
    });

    it.each(cases)('%s opening an unknown route', async (_label, userId, patch, heading, home) => {
      seedSession(patch, userId);
      renderWithProviders(undefined, { route: '/rota-que-nao-existe' });
      await expectPage(heading, home);
    });

    it('anonymous user stays on / and unknown routes go to /', async () => {
      const first = renderWithProviders(undefined, { route: '/' });
      await expectPage(/nosso teto/i, '/');
      await waitFor(() => expect(db.session.refreshCalls).toBe(1));
      expect(location()).toBe('/');
      first.unmount();

      renderWithProviders(undefined, { route: '/rota-que-nao-existe' });
      await expectPage(/nosso teto/i, '/');
    });
  });

  it('non-admin user without ficha cannot use the /admin exemption', async () => {
    seedSession({ profileComplete: false });
    renderWithProviders(undefined, { route: '/admin/usuarios' });
    await expectPage(/ficha/i, '/onboarding');
  });

  it.each([
    ['/explorar', 'Explorar'],
    ['/casas/house-verde', /detalhe da casa/i],
    ['/solicitacoes', 'Solicitações'],
    ['/mudanca/req-42', /mudança de casa/i],
  ])('morador can open %s', async (path, heading) => {
    seedSession({}, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: path });
    await expectPage(heading, path);
  });

  it('candidate on a morador route goes to its home', async () => {
    seedSession();
    renderWithProviders(undefined, { route: '/contas' });
    await expectPage('Explorar', '/explorar');
  });

  it('route without permission renders NoAccessPage inline, without redirect, and hides the tab', async () => {
    seedSession(
      {
        permissions: db.users
          .find((u) => u.id === MOCK_MORADOR_ID)!
          .permissions.filter((p) => p !== PERMISSIONS.MANAGE_GROCERIES),
      },
      MOCK_MORADOR_ID,
    );
    renderWithProviders(undefined, { route: '/mercado' });

    await expectPage(/sem acesso/i, '/mercado');
    const nav = screen.getByRole('navigation', { name: /principal/i });
    expect(within(nav).queryByRole('link', { name: /mercado/i })).toBeNull();
    expect(within(nav).getByRole('link', { name: /contas/i })).toBeInTheDocument();
  });

  it('candidate sees Explorar, Solicitações and Perfil tabs', async () => {
    seedSession();
    renderWithProviders(undefined, { route: '/explorar' });
    await expectPage('Explorar', '/explorar');

    for (const name of ['Navegação principal', 'Navegação inferior']) {
      const nav = screen.getByRole('navigation', { name });
      const labels = within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent);
      expect(labels).toEqual(['Explorar', 'Solicitações', 'Perfil']);
    }
  });

  it('morador sees Início, Contas, Tarefas, Mercado and Casa tabs', async () => {
    seedSession({}, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: '/inicio' });
    await expectPage('Início', '/inicio');

    const nav = screen.getByRole('navigation', { name: 'Navegação principal' });
    expect(
      within(nav)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['Início', 'Contas', 'Tarefas', 'Mercado', 'Casa']);
  });

  describe('no redirect cycles: home of each state is stable and foreign routes end there', () => {
    const fixtures: [string, string, Partial<UserMe>, string][] = [
      ['welcome + ficha incompleta', 'user-ana', { pendingWelcome: true, profileComplete: false }, '/inicio'],
      ['welcome + morador', MOCK_MORADOR_ID, { pendingWelcome: true }, '/explorar'],
      ['admin sem ficha', MOCK_ADMIN_ID, {}, '/entrar'],
      ['ficha incompleta', 'user-ana', { profileComplete: false }, '/admin/usuarios'],
      ['morador', MOCK_MORADOR_ID, {}, '/entrar'],
      ['candidato', 'user-ana', {}, '/mercado'],
    ];

    it.each(fixtures)('%s', async (_label, userId, patch, foreign) => {
      const user = seedSession(patch, userId);
      const home = getHomePathForUser(user);

      const first = renderWithProviders(undefined, { route: home });
      await screen.findByRole('heading', { level: 1 });
      await new Promise((resolve) => setTimeout(resolve, 20));
      expect(location()).toBe(home);
      first.unmount();

      renderWithProviders(undefined, { route: foreign });
      await waitFor(() => expect(location()).toBe(home));
      await screen.findByRole('heading', { level: 1 });
      await new Promise((resolve) => setTimeout(resolve, 20));
      expect(location()).toBe(home);
    });
  });

  it('avatar menu shows Administração only with ROLE_ADMIN_USERS', async () => {
    const user = userEvent.setup();
    seedSession({}, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: '/inicio' });
    await expectPage('Início', '/inicio');

    await user.click(screen.getByRole('button', { name: /menu da conta/i }));
    const menu = screen.getByRole('menu');
    expect(within(menu).getByRole('menuitem', { name: /perfil/i })).toBeInTheDocument();
    expect(within(menu).queryByRole('menuitem', { name: /administração/i })).toBeNull();
    expect(within(menu).getByRole('menuitem', { name: /sair/i })).toBeInTheDocument();
  });

  it('avatar menu hides Perfil without ROLE_MANAGE_PROFILE', async () => {
    const user = userEvent.setup();
    const morador = seedSession({}, MOCK_MORADOR_ID);
    // O mock de /me exige ROLE_MANAGE_PROFILE: responde direto com o usuário sem ela.
    server.use(
      http.get('/api/users/me', () => {
        const { password: _password, ...me } = morador;
        void _password;
        return HttpResponse.json({
          ...me,
          permissions: me.permissions.filter((p) => p !== PERMISSIONS.MANAGE_PROFILE),
        });
      }),
    );
    renderWithProviders(undefined, { route: '/inicio' });
    await expectPage('Início', '/inicio');

    await user.click(screen.getByRole('button', { name: /menu da conta/i }));
    const menu = screen.getByRole('menu');
    expect(within(menu).queryByRole('menuitem', { name: /perfil/i })).toBeNull();
    expect(within(menu).getByRole('menuitem', { name: /sair/i })).toBeInTheDocument();
  });

  it('avatar menu shows Administração for admins and Sair logs out to the landing', async () => {
    const user = userEvent.setup();
    seedSession(
      { profileComplete: true, permissions: [...ADMIN_PERMISSIONS] },
      MOCK_ADMIN_ID,
    );
    renderWithProviders(undefined, { route: '/explorar' });
    await expectPage('Explorar', '/explorar');

    await user.click(screen.getByRole('button', { name: /menu da conta/i }));
    await user.click(screen.getByRole('menuitem', { name: /administração/i }));
    await expectPage(/administração/i, '/admin/usuarios');

    await user.click(screen.getByRole('button', { name: /menu da conta/i }));
    await user.click(screen.getByRole('menuitem', { name: /sair/i }));
    await expectPage(/nosso teto/i, '/');
    await waitFor(() => expect(db.session.refreshValid).toBe(false));
  });
});
