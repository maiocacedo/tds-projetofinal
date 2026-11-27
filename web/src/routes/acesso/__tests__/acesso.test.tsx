import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { translateApiError } from '../../../api/errors';
import { invalidateSessionEpoch } from '../../../auth/apiClient';
import {
  db,
  MOCK_CANDIDATO_ID,
  MOCK_MORADOR_ID,
  MOCK_PASSWORD,
  seedSession,
} from '../../../mocks/db';
import {
  resetProfileMocks,
  setDemoPasswordResetEnabled,
} from '../../../mocks/handlers/profile';
import { server } from '../../../mocks/server';
import { renderWithProviders } from '../../../test/renderWithProviders';

function currentPath() {
  return screen.getByTestId('location').textContent;
}

describe('F4 — Acesso, Onboarding e Perfil', () => {
  beforeEach(() => {
    resetProfileMocks();
  });

  describe('Cadastro (/cadastro)', () => {
    it('valida nome, e-mail e senha >= 8 caracteres em pt-BR', async () => {
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/cadastro' });

      await screen.findByRole('heading', { level: 1, name: /criar conta/i });
      await user.click(screen.getByRole('button', { name: 'Criar conta' }));

      expect(
        await screen.findByText(/informe seu nome para continuar/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/digite um e-mail válido/i),
      ).toBeInTheDocument();
      expect(
        screen.getByText(/pelo menos 8 caracteres/i),
      ).toBeInTheDocument();

      await user.type(screen.getByLabelText('Nome'), 'Clara Dias');
      await user.type(screen.getByLabelText('E-mail'), 'clara@nossoteto.dev');
      await user.type(screen.getByLabelText('Senha'), '1234567');
      await user.click(screen.getByRole('button', { name: 'Criar conta' }));

      expect(
        await screen.findByText(/pelo menos 8 caracteres/i),
      ).toBeInTheDocument();
    });

    it('cadastra, autentica em seguida e leva para /onboarding', async () => {
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/cadastro' });

      await screen.findByRole('heading', { level: 1, name: /criar conta/i });
      await user.type(screen.getByLabelText('Nome'), 'Clara Dias');
      await user.type(screen.getByLabelText('E-mail'), 'clara@nossoteto.dev');
      await user.type(screen.getByLabelText('Senha'), 'segredo-forte-8');
      await user.click(screen.getByRole('button', { name: 'Criar conta' }));

      expect(
        await screen.findByRole('heading', {
          level: 1,
          name: /sua ficha de morador/i,
        }),
      ).toBeInTheDocument();
      expect(currentPath()).toBe('/onboarding');
      expect(
        db.users.some((u) => u.email === 'clara@nossoteto.dev'),
      ).toBe(true);
    });

    it('traduz erro de e-mail duplicado da API', async () => {
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/cadastro' });

      await screen.findByRole('heading', { level: 1, name: /criar conta/i });
      await user.type(screen.getByLabelText('Nome'), 'Ana Duplicada');
      await user.type(screen.getByLabelText('E-mail'), 'ana@nossoteto.dev');
      await user.type(screen.getByLabelText('Senha'), 'segredo-forte-8');
      await user.click(screen.getByRole('button', { name: 'Criar conta' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent(
        translateApiError('Email already exists'),
      );
    });
  });

  describe('Login (/entrar)', () => {
    it('exibe erro amigável quando credenciais não conferem', async () => {
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/entrar' });

      await screen.findByRole('heading', { level: 1, name: /^entrar$/i });
      await user.type(screen.getByLabelText('E-mail'), 'ana@nossoteto.dev');
      await user.type(screen.getByLabelText('Senha'), 'senha-errada');
      await user.click(screen.getByRole('button', { name: 'Entrar' }));

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent(/e-mail ou senha não conferem/i);
    });

    it('ignora SessionSupersededError silenciosamente sem exibir erro nem toast', async () => {
      let releaseToken!: () => void;
      let passwordRequested = false;
      const tokenGate = new Promise<void>((resolve) => {
        releaseToken = resolve;
      });
      server.use(
        http.post('/oauth/token', async ({ request }) => {
          const body = new URLSearchParams(await request.clone().text());
          if (body.get('grant_type') !== 'password') return undefined;
          passwordRequested = true;
          await tokenGate;
          return HttpResponse.json({
            access_token: 'late-token',
            token_type: 'bearer',
            expires_in: 900,
            scope: 'read write',
            user_id: MOCK_CANDIDATO_ID,
          });
        }),
      );

      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/entrar' });

      await screen.findByRole('heading', { level: 1, name: /^entrar$/i });
      await user.type(screen.getByLabelText('E-mail'), 'ana@nossoteto.dev');
      await user.type(screen.getByLabelText('Senha'), MOCK_PASSWORD);

      const submitPromise = user.click(
        screen.getByRole('button', { name: 'Entrar' }),
      );
      await waitFor(() => expect(passwordRequested).toBe(true));
      invalidateSessionEpoch();
      releaseToken();
      await submitPromise;

      await waitFor(() => expect(currentPath()).toBe('/entrar'));
      expect(screen.queryByRole('alert')).toBeNull();
      expect(screen.queryByRole('status')).toBeNull();
    });
  });

  describe('Esqueci minha senha', () => {
    it('fluxo demo com demoToken redefine a senha (204) e permite entrar com a nova senha', async () => {
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/entrar' });

      await screen.findByRole('heading', { level: 1, name: /^entrar$/i });
      await user.click(
        screen.getByRole('button', { name: /esqueci minha senha/i }),
      );

      expect(
        await screen.findByRole('heading', {
          level: 1,
          name: /vamos trocar sua senha/i,
        }),
      ).toBeInTheDocument();

      await user.type(screen.getByLabelText('E-mail'), 'ana@nossoteto.dev');
      await user.click(
        screen.getByRole('button', { name: /verificar e-mail/i }),
      );

      expect(
        await screen.findByRole('heading', {
          level: 1,
          name: /crie uma senha nova/i,
        }),
      ).toBeInTheDocument();

      const tokenInput = screen.getByLabelText(
        /código de recuperação/i,
      ) as HTMLInputElement;
      expect(tokenInput.value).toMatch(/^demo-reset-/);

      await user.type(screen.getByLabelText('Nova senha'), 'nova-senha-123');
      await user.type(
        screen.getByLabelText('Repita a nova senha'),
        'outra-senha-123',
      );
      await user.click(
        screen.getByRole('button', { name: /salvar nova senha/i }),
      );
      expect(
        await screen.findByText(/as duas senhas não são iguais/i),
      ).toBeInTheDocument();

      await user.clear(screen.getByLabelText('Repita a nova senha'));
      await user.type(
        screen.getByLabelText('Repita a nova senha'),
        'nova-senha-123',
      );
      await user.click(
        screen.getByRole('button', { name: /salvar nova senha/i }),
      );

      expect(
        await screen.findByRole('heading', { level: 1, name: /^entrar$/i }),
      ).toBeInTheDocument();
      expect(currentPath()).toBe('/entrar');
      expect(
        screen.getAllByText(/senha trocada\. entre com a senha nova/i).length,
      ).toBeGreaterThanOrEqual(1);

      await user.clear(screen.getByLabelText('E-mail'));
      await user.type(screen.getByLabelText('E-mail'), 'ana@nossoteto.dev');
      await user.type(screen.getByLabelText('Senha'), 'nova-senha-123');
      await user.click(screen.getByRole('button', { name: 'Entrar' }));

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Explorar' }),
      ).toBeInTheDocument();
    });

    it('quando o servidor retorna {} não vaza se o e-mail existe e exibe mensagem neutra', async () => {
      setDemoPasswordResetEnabled(false);
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/entrar' });

      await screen.findByRole('heading', { level: 1, name: /^entrar$/i });
      await user.click(
        screen.getByRole('button', { name: /esqueci minha senha/i }),
      );
      await user.type(
        screen.getByLabelText('E-mail'),
        'naoexiste@nossoteto.dev',
      );
      await user.click(
        screen.getByRole('button', { name: /verificar e-mail/i }),
      );

      expect(
        await screen.findByText(/se o e-mail estiver cadastrado/i),
      ).toBeInTheDocument();
      const tokenInput = screen.getByLabelText(
        /código de recuperação/i,
      ) as HTMLInputElement;
      expect(tokenInput.value).toBe('');
      expect(screen.queryByText(/não existe/i)).toBeNull();
      expect(screen.queryByText(/não encontrado/i)).toBeNull();

      await user.type(tokenInput, 'token-invalido');
      await user.type(screen.getByLabelText('Nova senha'), 'nova-senha-123');
      await user.type(
        screen.getByLabelText('Repita a nova senha'),
        'nova-senha-123',
      );
      await user.click(
        screen.getByRole('button', { name: /salvar nova senha/i }),
      );

      const alert = await screen.findByRole('alert');
      expect(alert).toHaveTextContent(
        translateApiError('invalid or expired token'),
      );
    });
  });

  describe('Onboarding (/onboarding)', () => {
    it('exige as 5 respostas obrigatórias, atualiza prévia ao vivo, chama refreshMe e navega para /explorar', async () => {
      seedSession({ profile: null, profileComplete: false }, MOCK_CANDIDATO_ID);
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/onboarding' });

      await screen.findByRole('heading', {
        level: 1,
        name: /sua ficha de morador/i,
      });

      const preview = screen.getByRole('complementary', {
        name: /prévia da ficha/i,
      });
      expect(within(preview).getByText('Ana Souza')).toBeInTheDocument();
      expect(
        screen.getByText(/faltam 3 de 5 respostas que pesam no match/i),
      ).toBeInTheDocument();

      await user.click(
        screen.getByRole('button', { name: /salvar e explorar casas/i }),
      );
      expect(
        await screen.findByText(/informe quanto cabe no seu bolso/i),
      ).toBeInTheDocument();

      await user.type(
        screen.getByLabelText(/aluguel que cabe no bolso/i),
        '950',
      );
      await user.click(screen.getByRole('switch', { name: /tem pet/i }));

      const orgGroup = screen.getByRole('radiogroup', { name: 'Organização' });
      await user.click(within(orgGroup).getByRole('radio', { name: '4' }));

      const visitsGroup = screen.getByRole('radiogroup', { name: 'Visitas' });
      await user.click(within(visitsGroup).getByRole('radio', { name: '3' }));

      await user.click(
        screen.getByRole('switch', { name: /móveis próprios/i }),
      );
      await user.type(screen.getByLabelText('Profissão'), 'Engenheira');
      await user.type(
        screen.getByLabelText('Sobre você'),
        'Gosto de cozinhar aos domingos.',
      );

      expect(
        screen.getByText(/tudo certo para calcular sua compatibilidade/i),
      ).toBeInTheDocument();
      expect(within(preview).getByText(/até R\$ 950/i)).toBeInTheDocument();
      expect(within(preview).getByText('Tem pet')).toBeInTheDocument();
      expect(within(preview).getByText('Organização 4/5')).toBeInTheDocument();
      expect(within(preview).getByText('Visitas 3/5')).toBeInTheDocument();
      expect(within(preview).getByText('Tem móveis')).toBeInTheDocument();
      expect(within(preview).getByText('Engenheira')).toBeInTheDocument();

      await user.click(
        screen.getByRole('button', { name: /salvar e explorar casas/i }),
      );

      expect(
        await screen.findByRole('heading', { level: 1, name: 'Explorar' }),
      ).toBeInTheDocument();
      expect(currentPath()).toBe('/explorar');
      expect(db.users[0].profileComplete).toBe(true);
      expect(db.users[0].profile?.budget).toBe(950);
    });

    it('mantém profileComplete falso se faltarem campos do match mesmo enviando opcionais', async () => {
      seedSession({ profile: null, profileComplete: false }, MOCK_CANDIDATO_ID);
      renderWithProviders(undefined, { route: '/onboarding' });
      await screen.findByRole('heading', {
        level: 1,
        name: /sua ficha de morador/i,
      });

      const res = await fetch('/api/users/me/profile', {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${db.session.accessToken}`,
        },
        body: JSON.stringify({
          hasFurniture: true,
          profession: 'Designer',
          bio: 'Só opcionais',
        }),
      });
      expect(res.ok).toBe(true);
      expect(db.users[0].profileComplete).toBe(false);
    });
  });

  describe('Perfil (/perfil)', () => {
    it('exibe pontos, histórico traduzido, tempo de moradia e permite editar a ficha', async () => {
      seedSession({}, MOCK_MORADOR_ID);
      const user = userEvent.setup();
      renderWithProviders(undefined, { route: '/perfil' });

      await screen.findByRole('heading', { level: 1, name: 'Perfil' });

      expect(screen.getByText('120 pts')).toBeInTheDocument();
      expect(screen.getByText('35 pts este mês')).toBeInTheDocument();
      expect(screen.getByText('14 meses')).toBeInTheDocument();

      expect((await screen.findAllByText('Tarefa no prazo')).length).toBeGreaterThan(0);
      expect(screen.getByText('Conta paga em dia')).toBeInTheDocument();
      expect(screen.getByText('Item comprado no mercado')).toBeInTheDocument();
      expect(screen.getAllByText('+20 pts').length).toBeGreaterThan(0);
      expect(screen.getByText('+10 pts')).toBeInTheDocument();
      expect(screen.getByText('+5 pts')).toBeInTheDocument();

      const budgetInput = screen.getByLabelText(
        /aluguel que cabe no bolso/i,
      ) as HTMLInputElement;
      expect(budgetInput.value).toBe('900');
      await user.clear(budgetInput);
      await user.type(budgetInput, '1100');
      await user.click(
        screen.getByRole('button', { name: /salvar alterações/i }),
      );

      expect(
        await screen.findByText(/ficha atualizada com sucesso/i),
      ).toBeInTheDocument();
      expect(
        db.users.find((u) => u.id === MOCK_MORADOR_ID)?.profile?.budget,
      ).toBe(1100);
    });

    it.each([
      ['null → Novo no Nosso Teto', null, 'Novo no Nosso Teto'],
      ['0 → Menos de 1 mês', 0, 'Menos de 1 mês'],
      ['1 → 1 mês', 1, '1 mês'],
      ['8 → 8 meses', 8, '8 meses'],
    ] as const)(
      'formata tempo de moradia (%s)',
      async (_label, months, expected) => {
        seedSession({ residenceMonths: months }, MOCK_CANDIDATO_ID);
        renderWithProviders(undefined, { route: '/perfil' });

        await screen.findByRole('heading', { level: 1, name: 'Perfil' });
        expect(screen.getByText(expected)).toBeInTheDocument();
      },
    );
  });
});
