import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { parseUtcLocalDateTime, type MatchStatus } from '../../../api/houseManagement';
import type { HouseMember } from '../../../api/houses';
import { PERMISSIONS } from '../../../api/me';
import {
  db,
  findMockUser,
  HOUSE_JABUTICABA_ID,
  HOUSE_VERDE_ID,
  MOCK_MORADOR_ID,
  seedSession,
  USER_PERMISSIONS,
} from '../../../mocks/db';
import {
  houseManagementMock,
  resetHouseManagementMocks,
  type MockMatchRequest,
  type MockRemoval,
} from '../../../mocks/handlers/houseManagement';
import { server } from '../../../mocks/server';
import { renderWithProviders } from '../../../test/renderWithProviders';

const BRUNO = MOCK_MORADOR_ID;
const CARLA = 'user-carla';
const LUIZA = 'user-luiza';
const TIAGO = 'user-tiago';

beforeEach(() => {
  resetHouseManagementMocks();
});

function jabuticaba() {
  const house = db.houses.find((h) => h.id === HOUSE_JABUTICABA_ID);
  if (!house) throw new Error('seed sem a Jabuticaba');
  return house;
}

function member(id: string): HouseMember {
  const found = jabuticaba().members.find((m) => m.id === id);
  if (!found) throw new Error(`morador ${id} não encontrado`);
  return found;
}

/** Jabuticaba com quatro moradores (Bruno admin, Carla, Luiza, Tiago). */
function addHousemates({ tiagoAdmin = false } = {}) {
  jabuticaba().members.push(
    { id: LUIZA, name: 'Luiza Prado', email: 'luiza@nossoteto.dev', isHouseAdmin: false },
    { id: TIAGO, name: 'Tiago Alves', email: 'tiago@nossoteto.dev', isHouseAdmin: tiagoAdmin },
  );
}

function loginAsBruno() {
  seedSession({}, BRUNO);
}

function location() {
  return screen.getByTestId('location').textContent;
}

function removal(patch: Partial<MockRemoval>): MockRemoval {
  return {
    id: 'removal-1',
    houseId: HOUSE_JABUTICABA_ID,
    targetUserId: CARLA,
    targetUserName: 'Carla Mendes',
    proposedById: BRUNO,
    proposedByName: 'Bruno Lima',
    reason: null,
    status: 'OPEN',
    createdAt: '2026-11-25T12:00:00',
    votes: {},
    ...patch,
  };
}

function moveRequest(patch: Partial<MockMatchRequest>): MockMatchRequest {
  return {
    id: 'move-1',
    houseId: HOUSE_VERDE_ID,
    status: 'AWAITING_MOVE',
    createdAt: '2026-11-24T12:00:00',
    moveDeadline: '2026-11-30T09:30:00',
    candidate: {
      id: BRUNO,
      name: 'Bruno Lima',
      compatibilityScore: 64,
      points: 120,
      residenceMonths: 14,
    },
    votes: {},
    ...patch,
  };
}

/** `GET /mine` é da F5: aqui ele espelha o estado do mock da F6. */
function serveMineFromState() {
  server.use(
    http.get('/api/matches/requests/mine', () =>
      HttpResponse.json(
        houseManagementMock.matches
          .filter((m) => m.candidate.id === db.session.userId)
          .map((m) => ({
            id: m.id,
            houseId: m.houseId,
            houseName: db.houses.find((h) => h.id === m.houseId)?.name,
            status: m.status,
            createdAt: m.createdAt,
            votesCast: Object.keys(m.votes).length,
            votersTotal: 1,
            moveDeadline: m.moveDeadline,
          })),
      ),
    ),
  );
}

async function findCard(name: string) {
  return screen.findByRole('listitem', { name });
}

// ---------------------------------------------------------------------------
// /casa — moradores e admins
// ---------------------------------------------------------------------------

describe('/casa — moradores e vários admins', () => {
  it('lista moradores com selo de admin, pontos e tempo de moradia', async () => {
    Object.assign(member(CARLA), { points: 95, residenceMonths: 3 });
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'República Jabuticaba' }),
    ).toBeInTheDocument();
    const bruno = await findCard('Bruno Lima');
    expect(within(bruno).getByText('Admin')).toBeInTheDocument();
    expect(within(bruno).getByText('(você)')).toBeInTheDocument();
    expect(within(bruno).getByText('120 pts · 14 meses no Nosso Teto')).toBeInTheDocument();

    const carla = await findCard('Carla Mendes');
    expect(within(carla).queryByText('Admin')).not.toBeInTheDocument();
    expect(within(carla).getByText('95 pts · 3 meses no Nosso Teto')).toBeInTheDocument();

    const atalhos = screen.getByRole('navigation', { name: 'Atalhos da casa' });
    expect(within(atalhos).getByRole('link', { name: /configurar casa/i })).toHaveAttribute(
      'href',
      '/casa/configurar',
    );
    expect(within(atalhos).getByRole('link', { name: /candidatos/i })).toHaveAttribute(
      'href',
      '/casa/candidatos',
    );
    expect(within(atalhos).getByRole('link', { name: /sair da casa/i })).toHaveAttribute(
      'href',
      '/casa/sair',
    );
  });

  it('promove outro morador e depois rebaixa a si mesmo, atualizando a sessão', async () => {
    const user = userEvent.setup();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const carla = await findCard('Carla Mendes');
    await user.click(within(carla).getByRole('button', { name: 'Tornar admin' }));

    expect(await screen.findByText('Carla Mendes agora é admin da casa.')).toBeInTheDocument();
    await waitFor(() => expect(within(carla).getByText('Admin')).toBeInTheDocument());
    expect(member(CARLA).isHouseAdmin).toBe(true);

    const bruno = await findCard('Bruno Lima');
    const demoteSelf = within(bruno).getByRole('button', { name: 'Remover admin' });
    await waitFor(() => expect(demoteSelf).toBeEnabled());
    await user.click(demoteSelf);

    expect(await screen.findByText('Bruno Lima deixou de ser admin.')).toBeInTheDocument();
    // refreshMe: a sessão (e as ações de admin) refletem o novo cargo.
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Tornar admin' })).not.toBeInTheDocument(),
    );
    expect(findMockUser(BRUNO)?.isHouseAdmin).toBe(false);
    expect(screen.queryByRole('link', { name: /configurar casa/i })).not.toBeInTheDocument();
  });

  it('protege o último admin na tela', async () => {
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const bruno = await findCard('Bruno Lima');
    expect(within(bruno).getByRole('button', { name: 'Remover admin' })).toBeDisabled();
    expect(within(bruno).getByText('A casa precisa de pelo menos um admin.')).toBeInTheDocument();
  });

  it('mostra o erro "house must keep at least one admin" em pt-BR quando a API recusa', async () => {
    const user = userEvent.setup();
    member(CARLA).isHouseAdmin = true;
    loginAsBruno();
    server.use(
      http.put('/api/houses/:id/members/:memberId/admin', () =>
        HttpResponse.json({ error: 'house must keep at least one admin' }, { status: 400 }),
      ),
    );
    renderWithProviders(undefined, { route: '/casa' });

    const carla = await findCard('Carla Mendes');
    await user.click(within(carla).getByRole('button', { name: 'Remover admin' }));

    expect(
      await screen.findByText(/a casa precisa de pelo menos um admin\. promova outra pessoa/i),
    ).toBeInTheDocument();
    expect(member(CARLA).isHouseAdmin).toBe(true);
  });

  it('admin sem permissão MANAGE_HOUSE não vê ações de gestão de moradores', async () => {
    seedSession(
      { permissions: USER_PERMISSIONS.filter((permission) => permission !== PERMISSIONS.MANAGE_HOUSE) },
      BRUNO,
    );
    renderWithProviders(undefined, { route: '/casa' });

    const bruno = await findCard('Bruno Lima');
    expect(within(bruno).getByText('Admin')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /configurar casa/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Tornar admin' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Remover admin' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Propor saída' })).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// /casa — remoção por votação
// ---------------------------------------------------------------------------

describe('/casa — remoção por votação', () => {
  it('admin propõe a saída de outro morador com motivo opcional (nunca de si mesmo)', async () => {
    const user = userEvent.setup();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const bruno = await findCard('Bruno Lima');
    expect(within(bruno).queryByRole('button', { name: 'Propor saída' })).not.toBeInTheDocument();

    const carla = await findCard('Carla Mendes');
    await user.click(within(carla).getByRole('button', { name: 'Propor saída' }));
    const dialog = await screen.findByRole('dialog', { name: 'Propor a saída de Carla Mendes?' });
    await user.type(within(dialog).getByLabelText('Motivo (opcional)'), 'Não paga as contas');
    await user.click(within(dialog).getByRole('button', { name: 'Abrir votação' }));

    const card = await findCard('Saída de Carla Mendes');
    expect(within(card).getByText('Não paga as contas')).toBeInTheDocument();
    expect(within(card).getByText('0 de 1 voto')).toBeInTheDocument();
    expect(within(card).getByText('Ninguém votou ainda.')).toBeInTheDocument();
    expect(houseManagementMock.removals).toHaveLength(1);
    expect(houseManagementMock.removals[0]).toMatchObject({
      targetUserId: CARLA,
      proposedById: BRUNO,
      reason: 'Não paga as contas',
    });
    await waitFor(() =>
      expect(within(carla).queryByRole('button', { name: 'Propor saída' })).not.toBeInTheDocument(),
    );
  });

  it('mostra quem votou sem a direção e permite trocar o voto', async () => {
    const user = userEvent.setup();
    // Tiago (admin) propôs; Bruno, Luiza, Tiago e Marcos votam a saída da Carla.
    addHousemates({ tiagoAdmin: true });
    jabuticaba().members.push({
      id: 'user-marcos',
      name: 'Marcos Dias',
      email: 'marcos@nossoteto.dev',
      isHouseAdmin: false,
    });
    houseManagementMock.removals.push(
      removal({ proposedById: TIAGO, proposedByName: 'Tiago Alves', votes: { [LUIZA]: 'APPROVE' } }),
    );
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const card = await findCard('Saída de Carla Mendes');
    expect(within(card).getByText('1 de 4 votos')).toBeInTheDocument();
    expect(within(card).getByText('Já votaram:').parentElement).toHaveTextContent(
      /^Já votaram: Luiza Prado$/,
    );
    // Nenhuma direção de voto alheio na tela (só os botões do meu voto).
    expect(card).not.toHaveTextContent(/aprovou|rejeitou|a favor|contra/i);
    expect(within(card).getByText('Você ainda não votou.')).toBeInTheDocument();
    // Só quem propôs cancela.
    expect(within(card).queryByRole('button', { name: 'Cancelar proposta' })).not.toBeInTheDocument();

    const rejeitar = within(card).getByRole('button', { name: 'Rejeitar' });
    const aprovar = within(card).getByRole('button', { name: 'Aprovar' });
    await user.click(rejeitar);
    await waitFor(() => expect(rejeitar).toHaveAttribute('aria-pressed', 'true'));
    expect(within(card).getByText('2 de 4 votos')).toBeInTheDocument();
    expect(within(card).getByText('Já votaram:').parentElement).toHaveTextContent(
      /^Já votaram: Luiza Prado, Bruno Lima$/,
    );
    expect(within(card).getByText(/seu voto: rejeitar/i)).toBeInTheDocument();

    await user.click(aprovar);
    await waitFor(() => expect(aprovar).toHaveAttribute('aria-pressed', 'true'));
    expect(rejeitar).toHaveAttribute('aria-pressed', 'false');
    expect(houseManagementMock.removals[0].votes[BRUNO]).toBe('APPROVE');
    expect(houseManagementMock.removals[0].status).toBe('OPEN');
  });

  it('a pessoa alvo não vota na própria remoção', async () => {
    addHousemates({ tiagoAdmin: true });
    houseManagementMock.removals.push(
      removal({
        targetUserId: BRUNO,
        targetUserName: 'Bruno Lima',
        proposedById: TIAGO,
        proposedByName: 'Tiago Alves',
        reason: 'Mudou de cidade',
        votes: { [LUIZA]: 'APPROVE' },
      }),
    );
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const card = await findCard('Saída de Bruno Lima');
    expect(within(card).getByText('Sua saída está em votação')).toBeInTheDocument();
    expect(within(card).getByText('1 de 3 votos')).toBeInTheDocument();
    expect(within(card).getByText(/você não vota na sua própria saída/i)).toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();
    expect(within(card).queryByRole('button', { name: 'Rejeitar' })).not.toBeInTheDocument();
    expect(within(card).getByText('Já votaram:').parentElement).toHaveTextContent(
      'Já votaram: Luiza Prado',
    );
  });

  it('quem propôs pode cancelar a proposta', async () => {
    const user = userEvent.setup();
    addHousemates();
    houseManagementMock.removals.push(removal({ id: 'removal-carla' }));
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const card = await findCard('Saída de Carla Mendes');
    await user.click(within(card).getByRole('button', { name: 'Cancelar proposta' }));

    expect(await screen.findByText('Proposta de saída cancelada.')).toBeInTheDocument();
    expect(await screen.findByText('Nenhuma saída em votação')).toBeInTheDocument();
    expect(houseManagementMock.removals[0].status).toBe('CANCELLED');
  });

  it('mostra alerta quando falha ao carregar as votações de remoção', async () => {
    server.use(
      http.get('/api/houses/:id/removals', () =>
        HttpResponse.json({ error: 'internal error' }, { status: 500 }),
      ),
    );
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa' });

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/não foi possível carregar as votações/i);
    expect(screen.queryByText('Nenhuma saída em votação')).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// /casa/sair
// ---------------------------------------------------------------------------

describe('/casa/sair', () => {
  it('avisa das dívidas e exige herdeiro quando é o único admin', async () => {
    const user = userEvent.setup();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/sair' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Sair da casa' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Contas em aberto continuam valendo')).toBeInTheDocument();

    const heir = await screen.findByLabelText('Quem assume como admin');
    const leaveButton = screen.getByRole('button', { name: 'Sair da casa' });
    expect(leaveButton).toBeDisabled();

    await user.selectOptions(heir, CARLA);
    expect(leaveButton).toBeEnabled();
    await user.click(leaveButton);

    const dialog = await screen.findByRole('dialog', { name: 'Sair de República Jabuticaba?' });
    expect(within(dialog).getByText('Carla Mendes assume como admin.')).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar saída' }));

    await waitFor(() => expect(location()).toBe('/explorar'));
    expect(findMockUser(BRUNO)?.houseId).toBeNull();
    expect(member(CARLA).isHouseAdmin).toBe(true);
    expect(jabuticaba().members.map((m) => m.id)).toEqual([CARLA]);
  });

  it('sem herdeiro obrigatório quando há outro admin', async () => {
    const user = userEvent.setup();
    member(CARLA).isHouseAdmin = true;
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/sair' });

    const leaveButton = await screen.findByRole('button', { name: 'Sair da casa' });
    await waitFor(() => expect(leaveButton).toBeEnabled());
    expect(screen.queryByLabelText('Quem assume como admin')).not.toBeInTheDocument();
    await user.click(leaveButton);
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: 'Confirmar saída' }));

    await waitFor(() => expect(location()).toBe('/explorar'));
    expect(findMockUser(BRUNO)?.houseId).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// /casa/configurar
// ---------------------------------------------------------------------------

describe('/casa/configurar', () => {
  it('edita vagas pelo Counter digitável, Receber pedidos e regra de votação', async () => {
    const user = userEvent.setup();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/configurar' });

    const vagas = await screen.findByRole('spinbutton', { name: 'Vagas livres' });
    expect(vagas).toHaveValue('1');
    await user.clear(vagas);
    await user.type(vagas, '3');
    expect(vagas).toHaveAttribute('aria-valuenow', '3');

    const receber = screen.getByRole('switch', { name: 'Receber pedidos' });
    expect(receber).toBeChecked();
    await user.click(receber);
    expect(receber).not.toBeChecked();
    expect(screen.getByText(/pedidos pausados/i)).toBeInTheDocument();

    await user.click(screen.getByRole('radio', { name: 'Unanimidade' }));

    const aluguel = screen.getByLabelText('Aluguel por pessoa (R$)');
    await user.clear(aluguel);
    await user.type(aluguel, '900,50');

    await user.click(screen.getByRole('button', { name: 'Salvar configurações' }));

    expect(await screen.findByText('Configurações da casa salvas.')).toBeInTheDocument();
    expect(jabuticaba()).toMatchObject({
      vacancies: 3,
      isAcceptingMatches: false,
      votingRule: 'UNANIMITY',
      rentAmount: 900.5,
    });
  });

  it('pausar pedidos não zera as vagas', async () => {
    const user = userEvent.setup();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/configurar' });

    await user.click(await screen.findByRole('switch', { name: 'Receber pedidos' }));
    await user.click(screen.getByRole('button', { name: 'Salvar configurações' }));

    expect(await screen.findByText('Configurações da casa salvas.')).toBeInTheDocument();
    expect(jabuticaba()).toMatchObject({ vacancies: 1, isAcceptingMatches: false });
  });

  it('quem não é admin vê um aviso claro', async () => {
    member(BRUNO).isHouseAdmin = false;
    member(CARLA).isHouseAdmin = true;
    seedSession({ isHouseAdmin: false }, BRUNO);
    renderWithProviders(undefined, { route: '/casa/configurar' });

    expect(await screen.findByText('Só admins configuram a casa')).toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: 'Vagas livres' })).not.toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// /casa/candidatos
// ---------------------------------------------------------------------------

describe('/casa/candidatos', () => {
  it('mostra compatibilidade, pontos e "Novo no Nosso Teto" para quem nunca morou', async () => {
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/candidatos' });

    const marina = await findCard('Marina Costa');
    expect(within(marina).getByRole('img', { name: 'Compatibilidade de 85%' })).toBeInTheDocument();
    expect(within(marina).getByText('420 pts · 8 meses no Nosso Teto')).toBeInTheDocument();
    expect(within(marina).getByText('Já votaram:').parentElement).toHaveTextContent(
      /^Já votaram: Carla Mendes$/,
    );

    const rafael = await findCard('Rafael Fontes');
    expect(within(rafael).getByText('0 pts · Novo no Nosso Teto')).toBeInTheDocument();
    expect(within(rafael).getByText('Ninguém votou ainda.')).toBeInTheDocument();
  });

  it('registra e troca o voto sem revelar a direção dos outros', async () => {
    const user = userEvent.setup();
    addHousemates();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/candidatos' });

    const rafael = await findCard('Rafael Fontes');
    const aprovar = within(rafael).getByRole('button', { name: 'Aprovar' });
    const rejeitar = within(rafael).getByRole('button', { name: 'Rejeitar' });

    await user.click(aprovar);
    await waitFor(() => expect(aprovar).toHaveAttribute('aria-pressed', 'true'));
    expect(within(rafael).getByText('Já votaram:').parentElement).toHaveTextContent(
      'Já votaram: Bruno Lima',
    );
    expect(within(rafael).getByText(/seu voto: aprovar/i)).toBeInTheDocument();

    await user.click(rejeitar);
    await waitFor(() => expect(rejeitar).toHaveAttribute('aria-pressed', 'true'));
    expect(aprovar).toHaveAttribute('aria-pressed', 'false');
    expect(within(rafael).getByText(/seu voto: rejeitar/i)).toBeInTheDocument();

    const match = houseManagementMock.matches.find((m) => m.id === 'match-rafael');
    expect(match?.votes[BRUNO]).toBe('REJECT');
    expect(match?.status).toBe('PENDING');
  });

  it('admin retira a reserva de quem está aguardando a mudança (72 h)', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-27T12:00:00Z'));
    const user = userEvent.setup();
    const vacanciesBefore = jabuticaba().vacancies;
    houseManagementMock.matches.push({
      id: 'match-diego',
      houseId: HOUSE_JABUTICABA_ID,
      status: 'AWAITING_MOVE',
      createdAt: '2026-11-26T12:00:00',
      moveDeadline: '2026-11-29T12:00:00',
      candidate: {
        id: 'user-diego',
        name: 'Diego Ramos',
        compatibilityScore: 75,
        points: 210,
        residenceMonths: 5,
      },
      votes: { [BRUNO]: 'APPROVE', [CARLA]: 'APPROVE' },
    });
    loginAsBruno();
    renderWithProviders(undefined, { route: '/casa/candidatos' });

    const diego = await findCard('Diego Ramos');
    expect(
      within(diego).getByText('Vaga reservada: faltam 48 h 00 min para a mudança'),
    ).toBeInTheDocument();
    // Prazo UTC exibido no horário de Brasília (12:00Z → 09:00).
    expect(within(diego).getByText(/29\/11, 09:00/)).toBeInTheDocument();
    expect(within(diego).queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();

    await user.click(within(diego).getByRole('button', { name: 'Retirar reserva' }));
    const dialog = await screen.findByRole('dialog', {
      name: 'Retirar a reserva de Diego Ramos?',
    });
    await user.click(within(dialog).getByRole('button', { name: 'Retirar reserva' }));

    expect(await screen.findByText('Reserva de Diego Ramos retirada.')).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.queryByRole('listitem', { name: 'Diego Ramos' })).not.toBeInTheDocument(),
    );
    expect(houseManagementMock.matches.find((m) => m.id === 'match-diego')?.status).toBe(
      'CANCELLED',
    );
    expect(jabuticaba().vacancies).toBe(vacanciesBefore + 1);
  });
});

// ---------------------------------------------------------------------------
// /mudanca/:requestId
// ---------------------------------------------------------------------------

describe('parseUtcLocalDateTime', () => {
  it('lê LocalDateTime sem offset como UTC e respeita offsets explícitos', () => {
    expect(parseUtcLocalDateTime('2026-11-29T12:00:00').toISOString()).toBe(
      '2026-11-29T12:00:00.000Z',
    );
    expect(parseUtcLocalDateTime('2026-11-29T12:00:00.123456').toISOString()).toBe(
      '2026-11-29T12:00:00.123Z',
    );
    expect(parseUtcLocalDateTime('2026-11-29T12:00:00Z').toISOString()).toBe(
      '2026-11-29T12:00:00.000Z',
    );
    expect(parseUtcLocalDateTime('2026-11-29T09:00:00-03:00').toISOString()).toBe(
      '2026-11-29T12:00:00.000Z',
    );
  });
});

describe('/mudanca/:requestId', () => {
  it('reserva ativa: contagem de 72 h em UTC, herdeiro obrigatório e confirmação', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-27T12:00:00Z'));
    const user = userEvent.setup();
    houseManagementMock.matches.push(moveRequest({}));
    serveMineFromState();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/mudanca/move-1' });

    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'Sua vaga em Casa Verde está reservada',
      }),
    ).toBeInTheDocument();
    const timer = screen.getByRole('timer', { name: 'Tempo para confirmar a mudança' });
    // 2026-11-30T09:30:00 (UTC) − 2026-11-27T12:00Z = 69 h 30 min.
    expect(timer).toHaveTextContent('Faltam 69 h 30 min');
    expect(timer).toHaveTextContent('30/11, 06:30');

    const confirmButton = screen.getByRole('button', { name: 'Confirmar mudança' });
    const heir = await screen.findByLabelText('Quem assume como admin');
    expect(confirmButton).toBeDisabled();
    await user.selectOptions(heir, CARLA);
    expect(confirmButton).toBeEnabled();
    await user.click(confirmButton);

    await waitFor(() => expect(location()).toBe('/inicio'));
    expect(findMockUser(BRUNO)).toMatchObject({ houseId: HOUSE_VERDE_ID, isHouseAdmin: false });
    expect(member(CARLA).isHouseAdmin).toBe(true);
    expect(houseManagementMock.matches.find((m) => m.id === 'move-1')?.status).toBe('ACCEPTED');
  });

  it('recusar a mudança pede confirmação e mostra o estado final', async () => {
    const user = userEvent.setup();
    member(CARLA).isHouseAdmin = true;
    const deadline = new Date(Date.now() + 10 * 60 * 60 * 1000).toISOString().slice(0, 19);
    houseManagementMock.matches.push(moveRequest({ moveDeadline: deadline }));
    serveMineFromState();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/mudanca/move-1' });

    await screen.findByRole('heading', { level: 1, name: /casa verde está reservada/i });
    expect(screen.queryByLabelText('Quem assume como admin')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Recusar mudança' }));
    const dialog = await screen.findByRole('dialog', { name: 'Recusar a vaga em Casa Verde?' });
    await user.click(within(dialog).getByRole('button', { name: 'Recusar mudança' }));

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Você recusou a mudança' }),
    ).toBeInTheDocument();
    expect(houseManagementMock.matches.find((m) => m.id === 'move-1')?.status).toBe('WITHDRAWN');
  });

  it('exibe erro dentro do diálogo se falhar ao recusar a mudança', async () => {
    const user = userEvent.setup();
    member(CARLA).isHouseAdmin = true;
    const deadline = new Date(Date.now() + 10 * 60 * 60 * 1000).toISOString().slice(0, 19);
    houseManagementMock.matches.push(moveRequest({ moveDeadline: deadline }));
    serveMineFromState();
    server.use(
      http.post('/api/matches/requests/:requestId/decline-move', () =>
        HttpResponse.json({ error: 'request is not awaiting move' }, { status: 400 }),
      ),
    );
    loginAsBruno();
    renderWithProviders(undefined, { route: '/mudanca/move-1' });

    await screen.findByRole('heading', { level: 1, name: /casa verde está reservada/i });
    await user.click(screen.getByRole('button', { name: 'Recusar mudança' }));
    const dialog = await screen.findByRole('dialog', { name: 'Recusar a vaga em Casa Verde?' });
    await user.click(within(dialog).getByRole('button', { name: 'Recusar mudança' }));

    expect(await within(dialog).findByRole('alert')).toBeInTheDocument();
    expect(dialog).toBeInTheDocument();
  });

  it.each<[MatchStatus, string, string, string]>([
    ['ACCEPTED', 'Mudança concluída', 'Ir para o Início', '/inicio'],
    ['WITHDRAWN', 'Você recusou a mudança', 'Voltar para o Início', '/inicio'],
    ['EXPIRED', 'O prazo de 72 horas expirou', 'Explorar casas', '/explorar'],
    ['CANCELLED', 'A reserva foi retirada pela casa', 'Explorar outras casas', '/explorar'],
  ])('estado terminal %s', async (status, title, cta, href) => {
    houseManagementMock.matches.push(moveRequest({ status }));
    serveMineFromState();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/mudanca/move-1' });

    expect(await screen.findByRole('heading', { level: 1, name: title })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: cta })).toHaveAttribute('href', href);
    expect(screen.queryByRole('button', { name: 'Confirmar mudança' })).not.toBeInTheDocument();
  });

  it('solicitação inexistente mostra aviso', async () => {
    serveMineFromState();
    loginAsBruno();
    renderWithProviders(undefined, { route: '/mudanca/nao-existe' });

    expect(
      await screen.findByRole('heading', { level: 1, name: 'Solicitação não encontrada' }),
    ).toBeInTheDocument();
  });
});
