import { configure, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it } from 'vitest';
import { db, HOUSE_JABUTICABA_ID, MOCK_MORADOR_ID, seedSession } from '../../../mocks/db';
import { getChoresMockState, resetChoresMocks } from '../../../mocks/handlers/chores';
import { server } from '../../../mocks/server';
import { renderWithProviders } from '../../../test/renderWithProviders';

const CARLA_ID = 'user-carla';

// Boot da sessão + primeira renderização das rotas fica lento com vários workers
// em paralelo: folga só neste arquivo, sem mexer no vitest.config.
configure({ asyncUtilTimeout: 5000 });
const SLOW = { timeout: 20_000 };

function renderTarefas(route = '/tarefas') {
  return renderWithProviders(undefined, { route });
}

/** Cartão da tarefa (o `article` tem a descrição como nome acessível). */
async function findCard(description: string) {
  return screen.findByRole('article', { name: description });
}

function getCard(description: string) {
  return screen.getByRole('article', { name: description });
}

function location() {
  return screen.getByTestId('location').textContent;
}

beforeEach(() => {
  resetChoresMocks();
  seedSession({}, MOCK_MORADOR_ID);
});

describe('Tarefas — semana (/tarefas)', SLOW, () => {
  it('conclui uma tarefa simples no lugar e desfaz', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const card = await findCard('Lavar a louça');
    const checkbox = within(card).getByRole('checkbox', { name: /Lavar a louça/ });
    expect(checkbox).not.toBeChecked();

    await user.click(checkbox);
    await waitFor(() => expect(within(getCard('Lavar a louça')).getByText('Feita')).toBeInTheDocument());
    expect(within(getCard('Lavar a louça')).getByRole('checkbox')).toBeChecked();
    expect(getChoresMockState().chores.find((c) => c.id === 'chore-louca')?.status).toBe('COMPLETED');

    await user.click(within(getCard('Lavar a louça')).getByRole('button', { name: 'Desfazer' }));
    await waitFor(() =>
      expect(within(getCard('Lavar a louça')).getByRole('checkbox')).not.toBeChecked(),
    );
    expect(within(getCard('Lavar a louça')).getByText('A fazer')).toBeInTheDocument();
    expect(getChoresMockState().chores.find((c) => c.id === 'chore-louca')?.completionsThisCycle).toBe(0);
  });

  it('marca +1 numa tarefa de 3× por semana até concluir (2 de 3 → 3 de 3)', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const card = await findCard('Tirar o lixo');
    expect(within(card).getByText('2 de 3')).toBeInTheDocument();
    expect(within(card).getByText('3× por semana')).toBeInTheDocument();

    await user.click(within(card).getByRole('button', { name: 'Marcar +1' }));
    await waitFor(() => expect(within(getCard('Tirar o lixo')).getByText('3 de 3')).toBeInTheDocument());
    expect(within(getCard('Tirar o lixo')).getByText('Feita')).toBeInTheDocument();
    expect(within(getCard('Tirar o lixo')).getByRole('button', { name: 'Marcar +1' })).toBeDisabled();

    await user.click(within(getCard('Tirar o lixo')).getByRole('button', { name: 'Desfazer' }));
    await waitFor(() => expect(within(getCard('Tirar o lixo')).getByText('2 de 3')).toBeInTheDocument());
  });

  it('só o responsável conclui: tarefas de outro morador não têm caixa nem "Marcar +1"', async () => {
    renderTarefas();

    const sala = await findCard('Varrer a sala');
    expect(within(sala).getByText('Com Carla Mendes')).toBeInTheDocument();
    expect(within(sala).getByText('0 de 2')).toBeInTheDocument();
    expect(within(sala).queryByRole('button', { name: 'Marcar +1' })).toBeNull();

    const mercado = getCard('Fazer o mercado');
    expect(within(mercado).queryByRole('checkbox')).toBeNull();
  });

  it('mostra o erro traduzido quando a API recusa a conclusão', async () => {
    server.use(
      http.put('/api/houses/:houseId/chores/:choreId/complete', () =>
        HttpResponse.json({ error: 'only the assignee can complete' }, { status: 403 }),
      ),
    );
    const user = userEvent.setup();
    renderTarefas();

    const card = await findCard('Lavar a louça');
    await user.click(within(card).getByRole('checkbox'));

    expect(
      await screen.findByText('Só quem está responsável pela tarefa pode marcar como feita.'),
    ).toBeInTheDocument();
    await waitFor(() => expect(within(getCard('Lavar a louça')).getByRole('checkbox')).not.toBeChecked());
  });

  it('filtra só as minhas tarefas', async () => {
    const user = userEvent.setup();
    renderTarefas();

    await findCard('Varrer a sala');
    await user.click(screen.getByRole('radio', { name: 'Minhas' }));

    expect(screen.queryByRole('article', { name: 'Varrer a sala' })).toBeNull();
    expect(getCard('Lavar a louça')).toBeInTheDocument();
    expect(getCard('Tirar o lixo')).toBeInTheDocument();
  });

  it('aceita a troca pendente recebida e mostra o aviso do ciclo', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const banner = await screen.findByRole('region', { name: 'Trocas pendentes' });
    expect(banner).toHaveTextContent('Carla Mendes quer trocar “Varrer a sala” pela sua “Lavar a louça”');

    await user.click(within(banner).getByRole('button', { name: 'Aceitar' }));

    await waitFor(() => expect(screen.queryByRole('region', { name: 'Trocas pendentes' })).toBeNull());
    expect(getChoresMockState().swaps[0]?.status).toBe('ACCEPTED');

    const louca = await findCard('Lavar a louça');
    await waitFor(() =>
      expect(within(getCard('Lavar a louça')).getByText('Trocada neste ciclo com Carla Mendes.')).toBeInTheDocument(),
    );
    expect(within(louca).queryByRole('checkbox')).toBeNull();
    expect(within(getCard('Lavar a louça')).getByText('Próximo na roda: Carla Mendes')).toBeInTheDocument();

    const sala = getCard('Varrer a sala');
    expect(within(sala).getByText('Trocada neste ciclo com Carla Mendes.')).toBeInTheDocument();
    expect(within(sala).getByRole('button', { name: 'Marcar +1' })).toBeEnabled();
    // Tarefa fixa trocada: mostra para quem ela volta no próximo ciclo.
    expect(within(sala).getByText('Volta para: Carla Mendes')).toBeInTheDocument();
  });

  it('recusa a troca pendente sem mudar os responsáveis', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const banner = await screen.findByRole('region', { name: 'Trocas pendentes' });
    await user.click(within(banner).getByRole('button', { name: 'Recusar' }));

    await waitFor(() => expect(screen.queryByRole('region', { name: 'Trocas pendentes' })).toBeNull());
    expect(getChoresMockState().swaps[0]?.status).toBe('REJECTED');
    expect(screen.queryByText(/Trocada neste ciclo/)).toBeNull();
    expect(within(getCard('Lavar a louça')).getByRole('checkbox')).toBeInTheDocument();
  });

  it('propõe troca de uma tarefa própria com a de outro morador', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const lixo = await findCard('Tirar o lixo');
    // Lavar a louça já tem troca pendente: não oferece outra.
    expect(within(getCard('Lavar a louça')).queryByRole('button', { name: 'Propor troca' })).toBeNull();

    await user.click(within(lixo).getByRole('button', { name: 'Propor troca' }));
    const panel = await screen.findByRole('dialog', { name: 'Propor troca' });
    // Varrer a sala tem troca pendente; Limpar o banheiro já foi feita.
    expect(within(panel).queryByRole('button', { name: /Varrer a sala/ })).toBeNull();
    expect(within(panel).queryByRole('button', { name: /Limpar o banheiro/ })).toBeNull();

    await user.click(within(panel).getByRole('button', { name: /Fazer o mercado/ }));

    expect(await screen.findByText('Pedido de troca enviado para Carla Mendes.')).toBeInTheDocument();
    const created = getChoresMockState().swaps.at(-1);
    expect(created).toMatchObject({
      requesterChoreId: 'chore-lixo',
      targetChoreId: 'chore-mercado',
      requesterId: MOCK_MORADOR_ID,
      targetUserId: CARLA_ID,
      status: 'PENDING',
    });
    await waitFor(() =>
      expect(within(getCard('Tirar o lixo')).getByText('Troca proposta, esperando Carla Mendes.')).toBeInTheDocument(),
    );
  });

  it('reabre a tarefa concluída de outro morador e ela volta atrasada', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const banheiro = await findCard('Limpar o banheiro');
    expect(within(banheiro).getByText('Feita')).toBeInTheDocument();

    await user.click(within(banheiro).getByRole('button', { name: 'Reabrir' }));
    const dialog = await screen.findByRole('dialog', { name: 'Reabrir tarefa?' });
    expect(dialog).toHaveTextContent('volta para atrasada');
    expect(dialog).toHaveTextContent('não gera +20');

    await user.click(within(dialog).getByRole('button', { name: 'Reabrir' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    await waitFor(() =>
      expect(within(getCard('Limpar o banheiro')).getByText('Atrasada')).toBeInTheDocument(),
    );
    expect(within(getCard('Limpar o banheiro')).getByText('Reaberta por você')).toBeInTheDocument();
    const reopened = getChoresMockState().chores.find((c) => c.id === 'chore-banheiro');
    expect(reopened).toMatchObject({ status: 'DELAYED', delayedThisCycle: true, reopenedById: MOCK_MORADOR_ID });
  });

  it('o próprio responsável não vê "Reabrir" na tarefa que concluiu', async () => {
    renderTarefas();

    const plantas = await findCard('Regar as plantas');
    expect(within(plantas).getByText('Feita')).toBeInTheDocument();
    expect(within(plantas).queryByRole('button', { name: 'Reabrir' })).toBeNull();
  });

  it('tarefa ligada ao mercado tem atalho para a lista', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const mercado = await findCard('Fazer o mercado');
    const link = within(mercado).getByRole('link', { name: 'Abrir lista do mercado' });
    expect(link).toHaveAttribute('href', '/mercado');
    expect(within(getCard('Lavar a louça')).queryByRole('link', { name: 'Abrir lista do mercado' })).toBeNull();

    await user.click(link);
    await waitFor(() => expect(location()).toBe('/mercado'));
  });

  it('admin fecha a semana: +20 para as feitas no prazo e a roda gira', async () => {
    const user = userEvent.setup();
    renderTarefas();

    await findCard('Limpar o banheiro');
    await user.click(screen.getByRole('button', { name: 'Fechar semana' }));
    const dialog = await screen.findByRole('dialog', { name: 'Fechar a semana agora?' });
    expect(dialog).toHaveTextContent('+20');

    await user.click(within(dialog).getByRole('button', { name: 'Fechar semana' }));

    expect(
      await screen.findByText(
        'Semana fechada: 2 tarefas feitas no prazo renderam +20 para quem fez.',
      ),
    ).toBeInTheDocument();
    // Limpar o banheiro (rotativa, feita pela Carla) gira para Bruno.
    await waitFor(() =>
      expect(within(getCard('Limpar o banheiro')).getByRole('checkbox')).not.toBeChecked(),
    );
    // Lavar a louça ficou por fazer: segue atrasada com o mesmo responsável.
    expect(within(getCard('Lavar a louça')).getByText('Atrasada')).toBeInTheDocument();
    expect(db.users.find((u) => u.id === MOCK_MORADOR_ID)?.points.total).toBe(140);
  });

  it('admin reordena a roda de uma tarefa rotativa', async () => {
    const user = userEvent.setup();
    renderTarefas();

    const louca = await findCard('Lavar a louça');
    await user.click(within(louca).getByRole('button', { name: 'Reordenar roda' }));
    const panel = await screen.findByRole('dialog', { name: 'Reordenar roda' });

    await user.click(within(panel).getByRole('button', { name: 'Mover Carla Mendes para cima' }));
    const items = within(within(panel).getByRole('list', { name: 'Ordem da roda' })).getAllByRole('listitem');
    expect(items.map((li) => li.textContent)).toEqual([
      expect.stringContaining('Carla Mendes'),
      expect.stringContaining('Bruno Lima'),
    ]);

    await user.click(within(panel).getByRole('button', { name: 'Salvar ordem' }));
    expect(await screen.findByText('Ordem da roda salva.')).toBeInTheDocument();
    expect(getChoresMockState().chores.find((c) => c.id === 'chore-louca')?.rotationOrder).toEqual([
      CARLA_ID,
      MOCK_MORADOR_ID,
    ]);
  });

  it('morador comum não vê "Fechar semana" nem "Reordenar roda"', async () => {
    seedSession({ isHouseAdmin: false }, MOCK_MORADOR_ID);
    const house = db.houses.find((h) => h.id === HOUSE_JABUTICABA_ID);
    const me = house?.members.find((m) => m.id === MOCK_MORADOR_ID);
    if (me) me.isHouseAdmin = false;
    renderTarefas();

    await findCard('Lavar a louça');
    expect(screen.queryByRole('button', { name: 'Fechar semana' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Reordenar roda' })).toBeNull();
    expect(screen.getByRole('link', { name: 'Nova tarefa' })).toHaveAttribute('href', '/tarefas/nova');
  });
});

describe('Nova tarefa (/tarefas/nova)', SLOW, () => {
  it('cria tarefa rotativa reordenando a roda com ↑/↓', async () => {
    const user = userEvent.setup();
    renderTarefas('/tarefas/nova');

    await user.type(await screen.findByLabelText('Descrição'), 'Limpar a geladeira');
    expect(screen.getByRole('radio', { name: 'Rotativa' })).toHaveAttribute('aria-checked', 'true');
    await user.click(screen.getByRole('radio', { name: '3× por semana' }));
    await user.click(screen.getByRole('switch', { name: 'Ligada à lista do mercado' }));

    const roda = await screen.findByRole('list', { name: 'Ordem da roda' });
    expect(within(roda).getByRole('button', { name: 'Mover Bruno Lima para cima' })).toBeDisabled();
    expect(within(roda).getByRole('button', { name: 'Mover Carla Mendes para baixo' })).toBeDisabled();

    await user.click(within(roda).getByRole('button', { name: 'Mover Bruno Lima para baixo' }));
    expect(within(roda).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      expect.stringContaining('Carla Mendes'),
      expect.stringContaining('Bruno Lima'),
    ]);
    await user.click(within(roda).getByRole('button', { name: 'Mover Bruno Lima para cima' }));
    await user.click(within(roda).getByRole('button', { name: 'Mover Carla Mendes para cima' }));

    await user.click(screen.getByRole('button', { name: 'Criar tarefa' }));

    await waitFor(() => expect(location()).toBe('/tarefas'));
    expect(getChoresMockState().lastCreateBody).toEqual({
      description: 'Limpar a geladeira',
      choreType: 'ROTATIONAL',
      frequency: 'THRICE',
      linkedToGroceries: true,
      rotationOrder: [CARLA_ID, MOCK_MORADOR_ID],
    });
    const card = await findCard('Limpar a geladeira');
    expect(within(card).getByText('Com Carla Mendes')).toBeInTheDocument();
    expect(within(card).getByText('0 de 3')).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'Abrir lista do mercado' })).toBeInTheDocument();
  });

  it('cria tarefa fixa com responsável escolhido', async () => {
    const user = userEvent.setup();
    renderTarefas('/tarefas/nova');

    await user.type(await screen.findByLabelText('Descrição'), 'Pagar a internet');
    await user.click(screen.getByRole('radio', { name: 'Fixa' }));
    await user.click(screen.getByRole('radio', { name: 'A cada 2 semanas' }));
    await user.selectOptions(await screen.findByLabelText('Responsável'), CARLA_ID);
    expect(screen.queryByRole('list', { name: 'Ordem da roda' })).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Criar tarefa' }));

    await waitFor(() => expect(location()).toBe('/tarefas'));
    expect(getChoresMockState().lastCreateBody).toEqual({
      description: 'Pagar a internet',
      choreType: 'FIXED',
      frequency: 'BIWEEKLY',
      linkedToGroceries: false,
      assigneeId: CARLA_ID,
    });
  });

  it('exige a descrição antes de enviar', async () => {
    const user = userEvent.setup();
    renderTarefas('/tarefas/nova');

    await screen.findByRole('list', { name: 'Ordem da roda' });
    await user.click(screen.getByRole('button', { name: 'Criar tarefa' }));

    expect(await screen.findByText('Diga qual é a tarefa.')).toBeInTheDocument();
    expect(screen.getByLabelText('Descrição')).toHaveAttribute('aria-invalid', 'true');
    expect(getChoresMockState().lastCreateBody).toBeNull();
  });
});
