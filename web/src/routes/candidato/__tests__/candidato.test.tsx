import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  explainCompatibility,
  isProfileComplete,
  type CompatibilityHouseInput,
  type CompatibilityProfileInput,
} from '../../../api/candidate';
import { translateApiError } from '../../../api/errors';
import {
  HOUSE_JABUTICABA_ID,
  HOUSE_VERDE_ID,
  MOCK_CANDIDATO_ID,
  MOCK_MORADOR_ID,
  db,
  seedSession,
} from '../../../mocks/db';
import {
  getCandidateMockRequests,
  resetCandidateMocks,
  seedCandidateMatchRequests,
} from '../../../mocks/handlers/candidate';
import { server } from '../../../mocks/server';
import { setPrefersReducedMotion } from '../../../test/motion';
import { renderWithProviders } from '../../../test/renderWithProviders';

function location() {
  return screen.getByTestId('location').textContent;
}

beforeEach(() => {
  resetCandidateMocks();
});

describe('explainCompatibility — paridade exata com CompatibilityCalculator.java', () => {
  const parityCases: Array<{
    name: string;
    profile: CompatibilityProfileInput | null | undefined;
    house: CompatibilityHouseInput | null | undefined;
    expectedScore: number | null;
    expectedPoints?: Record<string, number>;
  }> = [
    {
      name: 'retorna null quando profile é null',
      profile: null,
      house: { rentAmount: 1200, allowsPets: true, allowsSmokers: false },
      expectedScore: null,
    },
    {
      name: 'retorna null quando house é null',
      profile: {
        budget: 1500,
        hasPets: false,
        isSmoker: false,
        organizationLevel: 3,
        visitorTolerance: 3,
      },
      house: null,
      expectedScore: null,
    },
    {
      name: 'retorna null quando ambos são null',
      profile: null,
      house: null,
      expectedScore: null,
    },
    {
      name: 'retorna null (nunca 0%) quando a ficha está totalmente vazia',
      profile: {
        budget: null,
        hasPets: null,
        isSmoker: null,
        organizationLevel: null,
        visitorTolerance: null,
      },
      house: {
        rentAmount: null,
        allowsPets: false,
        allowsSmokers: false,
        expectedOrganizationLevel: 5,
        visitorTolerance: 5,
      },
      expectedScore: null,
    },
    {
      name: 'retorna null quando falta apenas 1 das 5 respostas do match',
      profile: {
        budget: 1500,
        hasPets: false,
        isSmoker: false,
        organizationLevel: 4,
        visitorTolerance: null,
      },
      house: {
        rentAmount: 1200,
        allowsPets: true,
        allowsSmokers: false,
        expectedOrganizationLevel: 4,
        visitorTolerance: 3,
      },
      expectedScore: null,
    },
    {
      name: 'retorna 100 quando todos os 5 critérios combinam perfeitamente',
      profile: {
        budget: 1500,
        hasPets: true,
        isSmoker: false,
        organizationLevel: 4,
        visitorTolerance: 3,
      },
      house: {
        rentAmount: 1200,
        allowsPets: true,
        allowsSmokers: false,
        expectedOrganizationLevel: 4,
        visitorTolerance: 3,
      },
      expectedScore: 100,
      expectedPoints: {
        budget: 20,
        pets: 20,
        smoker: 20,
        organization: 20,
        visitors: 20,
      },
    },
    {
      name: 'desconta 20 pts de orçamento quando o aluguel passa do orçamento',
      profile: {
        budget: 1000,
        hasPets: false,
        isSmoker: false,
        organizationLevel: 3,
        visitorTolerance: 3,
      },
      house: {
        rentAmount: 1200,
        allowsPets: true,
        allowsSmokers: false,
        expectedOrganizationLevel: 3,
        visitorTolerance: 3,
      },
      expectedScore: 80,
      expectedPoints: {
        budget: 0,
        pets: 20,
        smoker: 20,
        organization: 20,
        visitors: 20,
      },
    },
    {
      name: 'desconta 40 pts quando pets e cigarro são incompatíveis',
      profile: {
        budget: 2000,
        hasPets: true,
        isSmoker: true,
        organizationLevel: 3,
        visitorTolerance: 3,
      },
      house: {
        rentAmount: 1500,
        allowsPets: false,
        allowsSmokers: false,
        expectedOrganizationLevel: 3,
        visitorTolerance: 3,
      },
      expectedScore: 60,
      expectedPoints: {
        budget: 20,
        pets: 0,
        smoker: 0,
        organization: 20,
        visitors: 20,
      },
    },
    {
      name: 'desconta 5 pts por diferença em organização (diff 2 -> 10) e visitas (diff 4 -> 0)',
      profile: {
        budget: 1500,
        hasPets: false,
        isSmoker: false,
        organizationLevel: 5,
        visitorTolerance: 1,
      },
      house: {
        rentAmount: 1500,
        allowsPets: false,
        allowsSmokers: false,
        expectedOrganizationLevel: 3,
        visitorTolerance: 5,
      },
      expectedScore: 70,
      expectedPoints: {
        budget: 20,
        pets: 20,
        smoker: 20,
        organization: 10,
        visitors: 0,
      },
    },
    {
      name: 'dá pontuação cheia para atributos opcionais nulos da casa com ficha completa',
      profile: {
        budget: 900,
        hasPets: false,
        isSmoker: false,
        organizationLevel: 2,
        visitorTolerance: 4,
      },
      house: {
        rentAmount: null,
        allowsPets: false,
        allowsSmokers: false,
        expectedOrganizationLevel: null,
        visitorTolerance: null,
      },
      expectedScore: 100,
      expectedPoints: {
        budget: 20,
        pets: 20,
        smoker: 20,
        organization: 20,
        visitors: 20,
      },
    },
  ];

  it.each(parityCases)('$name', ({ profile, house, expectedScore, expectedPoints }) => {
    const result = explainCompatibility(profile, house);
    expect(result.score).toBe(expectedScore);
    if (expectedScore === null) {
      expect(result.criteria).toEqual([]);
    } else {
      expect(result.criteria).toHaveLength(5);
      const sum = result.criteria.reduce((acc, c) => acc + c.points, 0);
      expect(sum).toBe(expectedScore);
      if (expectedPoints) {
        for (const criterion of result.criteria) {
          expect(criterion.points).toBe(expectedPoints[criterion.key]);
        }
      }
    }
  });

  it('isProfileComplete exige todas as 5 respostas', () => {
    expect(isProfileComplete(null)).toBe(false);
    expect(
      isProfileComplete({
        budget: 1000,
        hasPets: false,
        isSmoker: false,
        organizationLevel: 3,
        visitorTolerance: 3,
      }),
    ).toBe(true);
    expect(
      isProfileComplete({
        budget: 1000,
        hasPets: false,
        isSmoker: undefined,
        organizationLevel: 3,
        visitorTolerance: 3,
      }),
    ).toBe(false);
  });
});

describe('Dicionário de erros do candidato', () => {
  it('traduz exclusivamente as 3 chaves da API para pt-BR', () => {
    expect(translateApiError('only the owner can perform this action')).toMatch(/quem enviou/i);
    expect(translateApiError('request is not pending')).toMatch(/não está mais pendente/i);
    expect(translateApiError('house is not accepting requests')).toMatch(/não está recebendo pedidos/i);
  });
});

describe('ExplorarPage (/explorar)', () => {
  it('lista casas com vagas, anel de compatibilidade e aplica filtros de aluguel, pets e mobília', async () => {
    const user = userEvent.setup();
    seedSession();
    renderWithProviders(undefined, { route: '/explorar' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Explorar' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'República Jabuticaba' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Casa Verde' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /criar casa/i })).toHaveAttribute('href', '/casas/nova');

    // Filtrar por aluguel até R$ 800 mantém apenas Casa Verde (R$ 700)
    await user.click(screen.getByRole('radio', { name: 'Até R$ 800' }));
    await waitFor(() => {
      expect(screen.queryByRole('link', { name: 'República Jabuticaba' })).toBeNull();
    });
    expect(screen.getByRole('link', { name: 'Casa Verde' })).toBeInTheDocument();

    // Limpar filtros volta a listar as duas
    await user.click(screen.getByRole('button', { name: /limpar filtros/i }));
    expect(await screen.findByRole('link', { name: 'República Jabuticaba' })).toBeInTheDocument();

    // Filtrar por Aceita pets mantém apenas República Jabuticaba
    await user.click(screen.getByRole('switch', { name: /aceita pets/i }));
    await waitFor(() => {
      expect(screen.queryByRole('link', { name: 'Casa Verde' })).toBeNull();
    });
    expect(screen.getByRole('link', { name: 'República Jabuticaba' })).toBeInTheDocument();

    // Filtrar também por Permite fumar não casa com Jabuticaba -> estado vazio
    await user.click(screen.getByRole('switch', { name: /permite fumar/i }));
    expect(await screen.findByText(/nenhuma casa encontrada/i)).toBeInTheDocument();
  });

  it('mostra "Novo no Nosso Teto" (nunca 0%) quando a casa vem com compatibilityScore null', async () => {
    seedSession();
    server.use(
      http.get('/api/houses', () =>
        HttpResponse.json(
          db.houses.map((h) => ({ ...h, compatibilityScore: null })),
        ),
      ),
    );
    renderWithProviders(undefined, { route: '/explorar' });

    expect(await screen.findByRole('link', { name: 'República Jabuticaba' })).toBeInTheDocument();
    expect(screen.getAllByText('Novo no Nosso Teto').length).toBeGreaterThanOrEqual(1);
    expect(screen.queryByText('0%')).toBeNull();
  });

  it('permite que um morador explore outras casas (US 34) sem exibir botão Criar casa', async () => {
    seedSession({}, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: '/explorar' });

    expect(await screen.findByRole('link', { name: 'Casa Verde' })).toBeInTheDocument();
    expect(screen.getByText(/você já mora em uma casa/i)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /criar casa/i })).toBeNull();
  });
});

describe('CasaDetalhePage (/casas/:id)', () => {
  it('abre o painel "Por que N%?" com os 5 critérios e permite pedir vaga com toast de confirmação', async () => {
    const user = userEvent.setup();
    seedSession();
    renderWithProviders(undefined, { route: `/casas/${HOUSE_JABUTICABA_ID}` });

    expect(
      await screen.findByRole('heading', { level: 1, name: /república jabuticaba/i }),
    ).toBeInTheDocument();
    expect(screen.getByText('Bruno Lima')).toBeInTheDocument();
    expect(screen.getByText('Carla Mendes')).toBeInTheDocument();

    // Abre o painel "Por que 100%?" (Ana + República Jabuticaba = 100%)
    await user.click(screen.getByRole('button', { name: /por que 100%\?/i }));
    const panel = await screen.findByRole('dialog');
    expect(within(panel).getByText('Orçamento')).toBeInTheDocument();
    expect(within(panel).getByText('Pets')).toBeInTheDocument();
    expect(within(panel).getByText('Cigarro')).toBeInTheDocument();
    expect(within(panel).getByText('Organização')).toBeInTheDocument();
    expect(within(panel).getByText('Visitas')).toBeInTheDocument();

    await user.click(within(panel).getByRole('button', { name: /fechar/i }));

    // Pede vaga
    await user.click(screen.getByRole('button', { name: /pedir vaga/i }));
    expect(
      await screen.findByText(/pedido enviado para república jabuticaba/i),
    ).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: /pedido enviado/i })).toBeDisabled();
    expect(getCandidateMockRequests()).toHaveLength(1);
    expect(getCandidateMockRequests()[0]?.houseId).toBe(HOUSE_JABUTICABA_ID);
  });

  it('desabilita o pedido quando o morador visualiza a própria casa', async () => {
    seedSession({}, MOCK_MORADOR_ID);
    renderWithProviders(undefined, { route: `/casas/${HOUSE_JABUTICABA_ID}` });

    expect(
      await screen.findByRole('heading', { level: 1, name: /república jabuticaba/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: /você já mora nesta casa/i }),
    ).toBeDisabled();
  });
});

describe('SolicitacoesPage (/solicitacoes)', () => {
  it('exibe status, progresso de votos, link de mudança para AWAITING_MOVE e permite desistir com confirmação', async () => {
    const user = userEvent.setup();
    seedSession();
    seedCandidateMatchRequests([
      {
        id: 'req-pendente',
        userId: MOCK_CANDIDATO_ID,
        houseId: HOUSE_JABUTICABA_ID,
        houseName: 'República Jabuticaba',
        status: 'PENDING',
        createdAt: '2026-11-26T10:15:00',
        votesCast: 1,
        votersTotal: 2,
        moveDeadline: null,
      },
      {
        id: 'req-mudanca',
        userId: MOCK_CANDIDATO_ID,
        houseId: HOUSE_VERDE_ID,
        houseName: 'Casa Verde',
        status: 'AWAITING_MOVE',
        createdAt: '2026-11-25T18:00:00',
        votesCast: 1,
        votersTotal: 1,
        moveDeadline: '2026-11-29T18:00:00',
      },
    ]);

    renderWithProviders(undefined, { route: '/solicitacoes' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Solicitações' })).toBeInTheDocument();
    expect(await screen.findByText('Em votação')).toBeInTheDocument();
    expect(screen.getByText('1 de 2 votos')).toBeInTheDocument();
    expect(screen.getByText('Aguardando mudança')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /confirmar mudança/i })).toHaveAttribute(
      'href',
      '/mudanca/req-mudanca',
    );

    // Desistir abre Dialog e chama DELETE /api/matches/requests/:id
    await user.click(screen.getByRole('button', { name: /^desistir$/i }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/desistir desta solicitação/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole('button', { name: /sim, desistir/i }));

    expect(await screen.findByText('Você desistiu')).toBeInTheDocument();
    expect(
      getCandidateMockRequests().find((r) => r.id === 'req-pendente')?.status,
    ).toBe('WITHDRAWN');
  });
});

describe('BemVindoPage (/bem-vindo — RF06 + Celebration)', () => {
  it('exibe Celebration com confete (e sem confete em reduced-motion) e executa cancel-others no RF06', async () => {
    const user = userEvent.setup();
    seedSession({ pendingWelcome: true }, MOCK_MORADOR_ID);
    seedCandidateMatchRequests([
      {
        id: 'req-outra',
        userId: MOCK_MORADOR_ID,
        houseId: HOUSE_VERDE_ID,
        houseName: 'Casa Verde',
        status: 'PENDING',
        createdAt: '2026-11-26T09:00:00',
        votesCast: 0,
        votersTotal: 1,
        moveDeadline: null,
      },
    ]);

    renderWithProviders(undefined, { route: '/bem-vindo' });

    expect(
      await screen.findByRole('heading', { level: 1, name: /boas-vindas à república jabuticaba/i }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('celebration-confetti')).toBeInTheDocument();
    expect(await screen.findByText(/casa verde/i)).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: /cancelar outras solicitações e ir para a casa/i }),
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Início' })).toBeInTheDocument();
    expect(location()).toBe('/inicio');
    expect(getCandidateMockRequests()[0]?.status).toBe('CANCELLED');
    expect(db.users.find((u) => u.id === MOCK_MORADOR_ID)?.pendingWelcome).toBe(false);
  });

  it('permite manter outras solicitações abertas chamando welcome-ack e respeita prefers-reduced-motion', async () => {
    const user = userEvent.setup();
    setPrefersReducedMotion(true);
    seedSession({ pendingWelcome: true }, MOCK_MORADOR_ID);
    seedCandidateMatchRequests([
      {
        id: 'req-outra',
        userId: MOCK_MORADOR_ID,
        houseId: HOUSE_VERDE_ID,
        houseName: 'Casa Verde',
        status: 'PENDING',
        createdAt: '2026-11-26T09:00:00',
        votesCast: 0,
        votersTotal: 1,
        moveDeadline: null,
      },
    ]);

    renderWithProviders(undefined, { route: '/bem-vindo' });

    expect(
      await screen.findByRole('heading', { level: 1, name: /boas-vindas/i }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('celebration-confetti')).toBeNull();

    await user.click(
      await screen.findByRole('button', { name: /manter outras abertas e ir para a casa/i }),
    );

    expect(await screen.findByRole('heading', { level: 1, name: 'Início' })).toBeInTheDocument();
    expect(location()).toBe('/inicio');
    expect(getCandidateMockRequests()[0]?.status).toBe('PENDING');
    expect(db.users.find((u) => u.id === MOCK_MORADOR_ID)?.pendingWelcome).toBe(false);
  });

  it('quando não há outras solicitações pendentes, chama welcome-ack direto ao clicar em "Ir para a casa"', async () => {
    const user = userEvent.setup();
    seedSession({ pendingWelcome: true }, MOCK_MORADOR_ID);

    renderWithProviders(undefined, { route: '/bem-vindo' });

    await user.click(await screen.findByRole('button', { name: /ir para a casa/i }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Início' })).toBeInTheDocument();
    expect(location()).toBe('/inicio');
    expect(db.users.find((u) => u.id === MOCK_MORADOR_ID)?.pendingWelcome).toBe(false);
  });
});

describe('CriarCasaPage (/casas/nova)', () => {
  it('atualiza a prévia ao vivo em tempo real e cria a casa redirecionando para /inicio', async () => {
    const user = userEvent.setup();
    seedSession();
    renderWithProviders(undefined, { route: '/casas/nova' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Criar casa' })).toBeInTheDocument();
    const preview = screen.getByRole('complementary', {
      name: /prévia de como os candidatos veem a casa/i,
    });

    await user.type(screen.getByLabelText(/nome da casa/i), 'República Girassol');
    await user.type(screen.getByLabelText(/endereço ou bairro/i), 'Rua das Acácias, 77');
    await user.type(screen.getByLabelText(/aluguel por pessoa/i), '950');

    // Counter digitável de vagas
    const vacanciesInput = screen.getByRole('spinbutton', { name: /vagas disponíveis/i });
    await user.clear(vacanciesInput);
    await user.type(vacanciesInput, '3');

    // Switch mobiliada + Segmented regra de votação
    await user.click(screen.getByRole('switch', { name: /casa mobiliada/i }));
    await user.click(screen.getByRole('radio', { name: 'Unanimidade' }));

    // Confere prévia ao vivo
    expect(within(preview).getByText('República Girassol')).toBeInTheDocument();
    expect(within(preview).getByText('Rua das Acácias, 77')).toBeInTheDocument();
    expect(within(preview).getByText(/950\/mês/)).toBeInTheDocument();
    expect(within(preview).getByText('3 vagas')).toBeInTheDocument();
    expect(within(preview).getByText('Mobiliada')).toBeInTheDocument();
    expect(within(preview).getByText('Unanimidade')).toBeInTheDocument();

    // Envia formulário
    await user.click(screen.getByRole('button', { name: /^criar casa$/i }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Início' })).toBeInTheDocument();
    expect(location()).toBe('/inicio');

    const created = db.houses.find((h) => h.name === 'República Girassol');
    expect(created).toBeDefined();
    expect(created?.vacancies).toBe(3);
    expect(created?.rentAmount).toBe(950);
    expect(created?.hasFurniture).toBe(true);
    expect(created?.votingRule).toBe('UNANIMITY');

    const ana = db.users.find((u) => u.id === MOCK_CANDIDATO_ID);
    expect(ana?.houseId).toBe(created?.id);
    expect(ana?.isHouseAdmin).toBe(true);
  });
});
