import { ArrowLeftRight, ClipboardList, ListOrdered, Plus, ShoppingCart } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CHORE_FREQUENCY_LABELS,
  useAnswerSwapMutation,
  useChoreSwapsQuery,
  useChoresQuery,
  useCompleteChoreMutation,
  useCreateSwapMutation,
  useReopenChoreMutation,
  useRotateChoresMutation,
  useUncompleteChoreMutation,
  useUpdateRotationMutation,
  type ChoreResponse,
  type ChoreStatus,
  type ChoreSwapDto,
} from '../../api/chores';
import { translateApiError } from '../../api/errors';
import { useHouseDetailQuery } from '../../api/houses';
import { ApiError } from '../../auth/apiClient';
import { useSession } from '../../auth/SessionContext';
import {
  Alert,
  Button,
  ChecklistItem,
  Dialog,
  EmptyState,
  Panel,
  Segmented,
  StatusChip,
  useToast,
  type StatusTone,
} from '../../components';
import { paths } from '../paths';
import { RotationOrderEditor } from './NovaTarefaPage';

type Filter = 'todas' | 'minhas';

const FILTER_OPTIONS: { value: Filter; label: string }[] = [
  { value: 'todas', label: 'Todas' },
  { value: 'minhas', label: 'Minhas' },
];

const STATUS_META: Record<ChoreStatus, { label: string; tone: StatusTone }> = {
  DELAYED: { label: 'Atrasada', tone: 'atrasado' },
  PENDING: { label: 'A fazer', tone: 'neutro' },
  COMPLETED: { label: 'Feita', tone: 'feito' },
};

/** Atrasadas primeiro (destacadas), depois as a fazer e as feitas. */
const STATUS_ORDER: Record<ChoreStatus, number> = { DELAYED: 0, PENDING: 1, COMPLETED: 2 };

function errorMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : translateApiError('unknown_error');
}

function plural(count: number, singular: string, pluralForm: string): string {
  return count === 1 ? singular : pluralForm;
}

/** Quem pode receber a proposta de troca: tarefa de outro morador, ainda aberta e sem troca. */
function isSwappable(chore: ChoreResponse, meId: string): boolean {
  return (
    chore.effectiveAssigneeId !== null &&
    chore.effectiveAssigneeId !== meId &&
    chore.status !== 'COMPLETED' &&
    chore.temporaryAssigneeId === null &&
    chore.pendingSwaps.length === 0
  );
}

function swapNotice(chore: ChoreResponse, meId: string): string | null {
  if (!chore.temporaryAssigneeId) return null;
  const owner = chore.currentAssigneeName ?? 'o responsável';
  const temporary = chore.temporaryAssigneeName ?? 'outro morador';
  if (chore.currentAssigneeId === meId) return `Trocada neste ciclo com ${temporary}.`;
  if (chore.temporaryAssigneeId === meId) return `Trocada neste ciclo com ${owner}.`;
  return `Trocada neste ciclo: ${temporary} no lugar de ${owner}.`;
}

interface ChoreCardProps {
  chore: ChoreResponse;
  meId: string;
  isAdmin: boolean;
  busy: boolean;
  onComplete: (chore: ChoreResponse) => void;
  onUncomplete: (chore: ChoreResponse) => void;
  onReopen: (chore: ChoreResponse) => void;
  onProposeSwap: (chore: ChoreResponse) => void;
  onReorder: (chore: ChoreResponse) => void;
}

function ChoreCard({
  chore,
  meId,
  isAdmin,
  busy,
  onComplete,
  onUncomplete,
  onReopen,
  onProposeSwap,
  onReorder,
}: ChoreCardProps) {
  const mine = chore.effectiveAssigneeId === meId;
  const simple = chore.requiredCompletions <= 1;
  const done = chore.status === 'COMPLETED';
  const status = STATUS_META[chore.status];
  const assigneeName = mine ? 'Com você' : `Com ${chore.effectiveAssigneeName ?? 'ninguém'}`;
  const frequency = CHORE_FREQUENCY_LABELS[chore.frequency];
  const notice = swapNotice(chore, meId);
  const sentSwap = chore.pendingSwaps.find((s) => s.requesterId === meId);
  const receivedSwap = chore.pendingSwaps.find((s) => s.targetUserId === meId);
  // Rotativa sempre mostra o próximo; fixa com troca temporária mostra para quem ela volta.
  const showNext =
    (chore.type === 'ROTATIONAL' || chore.temporaryAssigneeId !== null) && Boolean(chore.nextAssigneeName);
  const nextLabel =
    chore.type === 'FIXED' && chore.temporaryAssigneeId !== null ? 'Volta para' : 'Próximo na roda';
  const canSwap = mine && !done && chore.temporaryAssigneeId === null && chore.pendingSwaps.length === 0;

  return (
    <li>
      <article
        aria-label={chore.description}
        className={[
          'flex flex-col gap-3 rounded-[24px] border p-4',
          chore.status === 'DELAYED' ? 'border-atrasado-texto/40 bg-atrasado-fundo/40' : 'border-linha bg-fundo',
        ].join(' ')}
      >
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusChip tone={status.tone}>{status.label}</StatusChip>
          <StatusChip>{frequency}</StatusChip>
          <StatusChip>{chore.type === 'ROTATIONAL' ? 'Rotativa' : 'Fixa'}</StatusChip>
        </div>

        {simple && mine ? (
          <ChecklistItem
            checked={done}
            disabled={busy}
            label={chore.description}
            meta={assigneeName}
            onCheckedChange={(checked) => (checked ? onComplete(chore) : onUncomplete(chore))}
            action={
              done ? (
                <Button variant="ghost" size="sm" disabled={busy} onClick={() => onUncomplete(chore)}>
                  Desfazer
                </Button>
              ) : undefined
            }
          />
        ) : (
          <div className="flex flex-col gap-0.5">
            <h2
              className={[
                'text-[17px] font-extrabold leading-snug',
                done ? 'text-legenda line-through' : 'text-tinta',
              ].join(' ')}
            >
              {chore.description}
            </h2>
            <p className="text-[13px] text-legenda">{assigneeName}</p>
          </div>
        )}

        {!simple && (
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-3">
              <div
                aria-hidden="true"
                className="h-2 flex-1 overflow-hidden rounded-full bg-superficie"
              >
                <div
                  className="h-full rounded-full bg-tomate transition-[width] duration-300 motion-reduce:transition-none"
                  style={{
                    width: `${Math.round((chore.completionsThisCycle / chore.requiredCompletions) * 100)}%`,
                  }}
                />
              </div>
              <span className="shrink-0 text-sm font-bold text-tinta">
                {chore.completionsThisCycle} de {chore.requiredCompletions}
              </span>
            </div>
            {mine && (
              <div className="flex flex-wrap gap-2">
                <Button size="sm" disabled={busy || done} onClick={() => onComplete(chore)}>
                  Marcar +1
                </Button>
                <Button
                  size="sm"
                  variant="ghost"
                  disabled={busy || chore.completionsThisCycle === 0}
                  onClick={() => onUncomplete(chore)}
                >
                  Desfazer
                </Button>
              </div>
            )}
          </div>
        )}

        {(notice || showNext || chore.reopenedById || sentSwap || receivedSwap) && (
          <ul className="flex flex-col gap-1 text-[13px] text-texto-suave">
            {notice && <li className="font-semibold text-tinta">{notice}</li>}
            {showNext && (
              <li>
                {nextLabel}: {chore.nextAssigneeName}
              </li>
            )}
            {chore.reopenedById && (
              <li className="font-semibold text-atrasado-texto">
                Reaberta por {chore.reopenedById === meId ? 'você' : (chore.reopenedByName ?? 'outro morador')}
              </li>
            )}
            {sentSwap && (
              <li>Troca proposta, esperando {sentSwap.targetUserName ?? 'resposta'}.</li>
            )}
            {receivedSwap && (
              <li>{receivedSwap.requesterName ?? 'Alguém'} pediu para trocar esta tarefa.</li>
            )}
          </ul>
        )}

        {(chore.linkedToGroceries || (done && !mine) || canSwap || (isAdmin && chore.type === 'ROTATIONAL')) && (
          <div className="flex flex-wrap gap-2">
            {chore.linkedToGroceries && (
              <Button asChild size="sm" variant="secondary">
                <Link to={paths.mercado}>
                  <ShoppingCart aria-hidden="true" className="size-4" />
                  Abrir lista do mercado
                </Link>
              </Button>
            )}
            {done && !mine && (
              <Button size="sm" variant="secondary" disabled={busy} onClick={() => onReopen(chore)}>
                Reabrir
              </Button>
            )}
            {canSwap && (
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => onProposeSwap(chore)}>
                <ArrowLeftRight aria-hidden="true" className="size-4" />
                Propor troca
              </Button>
            )}
            {isAdmin && chore.type === 'ROTATIONAL' && (
              <Button size="sm" variant="ghost" disabled={busy} onClick={() => onReorder(chore)}>
                <ListOrdered aria-hidden="true" className="size-4" />
                Reordenar roda
              </Button>
            )}
          </div>
        )}
      </article>
    </li>
  );
}

interface SwapBannerProps {
  swaps: ChoreSwapDto[];
  answeringId: string | null;
  onAnswer: (swap: ChoreSwapDto, answer: 'accept' | 'reject') => void;
}

function SwapBanner({ swaps, answeringId, onAnswer }: SwapBannerProps) {
  if (swaps.length === 0) return null;
  return (
    <section
      aria-label="Trocas pendentes"
      className="flex flex-col gap-2 rounded-[24px] border border-pontos-borda bg-pontos-fundo p-4"
    >
      <h2 className="text-[15px] font-extrabold text-pontos-texto">
        {swaps.length === 1 ? 'Pedido de troca' : `${swaps.length} pedidos de troca`}
      </h2>
      <ul className="flex flex-col gap-3">
        {swaps.map((swap) => {
          const busy = answeringId === swap.id;
          return (
            <li key={swap.id} className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <p className="flex-1 text-[15px] text-tinta">
                <strong>{swap.requesterName ?? 'Um morador'}</strong> quer trocar “
                {swap.requesterChoreDescription ?? 'uma tarefa'}” pela sua “
                {swap.targetChoreDescription ?? 'tarefa'}” nesta semana.
              </p>
              <div className="flex gap-2">
                <Button size="sm" loading={busy} onClick={() => onAnswer(swap, 'accept')}>
                  Aceitar
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={busy}
                  onClick={() => onAnswer(swap, 'reject')}
                >
                  Recusar
                </Button>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

export function TarefasPage() {
  const { user } = useSession();
  const houseId = user?.houseId ?? '';
  const meId = user?.id ?? '';
  const isAdmin = user?.isHouseAdmin === true;
  const toast = useToast();

  const choresQuery = useChoresQuery(houseId);
  const swapsQuery = useChoreSwapsQuery(houseId);
  const house = useHouseDetailQuery(houseId || undefined);

  const complete = useCompleteChoreMutation(houseId);
  const uncomplete = useUncompleteChoreMutation(houseId);
  const reopen = useReopenChoreMutation(houseId);
  const updateRotation = useUpdateRotationMutation(houseId);
  const createSwap = useCreateSwapMutation(houseId);
  const answerSwap = useAnswerSwapMutation(houseId);
  const rotate = useRotateChoresMutation(houseId);

  const [filter, setFilter] = useState<Filter>('todas');
  const [busyChoreIds, setBusyChoreIds] = useState<Set<string>>(() => new Set());
  const [answeringId, setAnsweringId] = useState<string | null>(null);
  const [reopenTarget, setReopenTarget] = useState<ChoreResponse | null>(null);
  const [swapSource, setSwapSource] = useState<ChoreResponse | null>(null);
  const [rotationTarget, setRotationTarget] = useState<ChoreResponse | null>(null);
  const [rotationDraft, setRotationDraft] = useState<string[]>([]);
  const [closeOpen, setCloseOpen] = useState(false);

  const chores = choresQuery.data ?? [];
  const names = new Map<string, string>();
  for (const chore of chores) {
    for (const [id, name] of [
      [chore.currentAssigneeId, chore.currentAssigneeName],
      [chore.effectiveAssigneeId, chore.effectiveAssigneeName],
      [chore.temporaryAssigneeId, chore.temporaryAssigneeName],
      [chore.nextAssigneeId, chore.nextAssigneeName],
    ] as const) {
      if (id && name) names.set(id, name);
    }
  }
  for (const member of house.data?.members ?? []) names.set(member.id, member.name);

  const visible = chores
    .filter((chore) => filter === 'todas' || chore.effectiveAssigneeId === meId)
    .slice()
    .sort((a, b) => STATUS_ORDER[a.status] - STATUS_ORDER[b.status]);

  const receivedSwaps = (swapsQuery.data ?? []).filter(
    (swap) => swap.status === 'PENDING' && swap.targetUserId === meId,
  );

  const runOnChore = async (chore: ChoreResponse, action: () => Promise<unknown>) => {
    setBusyChoreIds((prev) => {
      const next = new Set(prev);
      next.add(chore.id);
      return next;
    });
    try {
      await action();
    } catch (error) {
      toast.show({ message: errorMessage(error), tone: 'atrasado' });
    } finally {
      setBusyChoreIds((prev) => {
        const next = new Set(prev);
        next.delete(chore.id);
        return next;
      });
    }
  };

  const handleComplete = (chore: ChoreResponse) =>
    void runOnChore(chore, async () => {
      const updated = await complete.mutateAsync(chore.id);
      if (updated.status === 'COMPLETED') {
        toast.show({ message: `“${chore.description}” feita. Boa!`, tone: 'feito' });
      }
    });

  const handleUncomplete = (chore: ChoreResponse) =>
    void runOnChore(chore, () => uncomplete.mutateAsync(chore.id));

  const handleAnswer = async (swap: ChoreSwapDto, answer: 'accept' | 'reject') => {
    setAnsweringId(swap.id);
    try {
      await answerSwap.mutateAsync({ swapId: swap.id, answer });
      toast.show({
        message:
          answer === 'accept'
            ? `Troca aceita: vale só para este ciclo.`
            : `Troca recusada. ${swap.requesterName ?? 'O morador'} fica com a tarefa.`,
        tone: answer === 'accept' ? 'feito' : 'neutro',
      });
    } catch (error) {
      toast.show({ message: errorMessage(error), tone: 'atrasado' });
    } finally {
      setAnsweringId(null);
    }
  };

  const confirmReopen = async () => {
    if (!reopenTarget) return;
    try {
      await reopen.mutateAsync(reopenTarget.id);
      toast.show({ message: `“${reopenTarget.description}” voltou para atrasada.`, tone: 'neutro' });
    } catch (error) {
      toast.show({ message: errorMessage(error), tone: 'atrasado' });
      throw error;
    }
  };

  const proposeSwap = async (target: ChoreResponse) => {
    if (!swapSource) return;
    try {
      await createSwap.mutateAsync({ choreId: swapSource.id, targetChoreId: target.id });
      toast.show({
        message: `Pedido de troca enviado para ${target.effectiveAssigneeName ?? 'o morador'}.`,
        tone: 'feito',
      });
      setSwapSource(null);
    } catch (error) {
      toast.show({ message: errorMessage(error), tone: 'atrasado' });
    }
  };

  const openRotation = (chore: ChoreResponse) => {
    setRotationDraft(chore.rotationOrder);
    setRotationTarget(chore);
  };

  const saveRotation = async () => {
    if (!rotationTarget) return;
    try {
      await updateRotation.mutateAsync({ choreId: rotationTarget.id, order: rotationDraft });
      toast.show({ message: 'Ordem da roda salva.', tone: 'feito' });
      setRotationTarget(null);
    } catch (error) {
      toast.show({ message: errorMessage(error), tone: 'atrasado' });
    }
  };

  const confirmClose = async () => {
    const before = chores;
    try {
      const after = await rotate.mutateAsync();
      const credited = before.filter((chore) => {
        const next = after.find((a) => a.id === chore.id);
        return (
          chore.status === 'COMPLETED' &&
          !chore.delayedThisCycle &&
          next !== undefined &&
          next.cycleNumber > chore.cycleNumber
        );
      }).length;
      toast.show({
        message:
          credited > 0
            ? `Semana fechada: ${credited} ${plural(credited, 'tarefa feita', 'tarefas feitas')} no prazo ${plural(credited, 'rendeu', 'renderam')} +20 para quem fez.`
            : 'Semana fechada. Nenhuma tarefa rendeu +20 desta vez.',
        tone: 'feito',
      });
    } catch (error) {
      toast.show({ message: errorMessage(error), tone: 'atrasado' });
      throw error;
    }
  };

  const swapCandidates = swapSource ? chores.filter((chore) => isSwappable(chore, meId)) : [];

  return (
    <section className="mx-auto flex w-full max-w-2xl flex-col gap-5">
      <header className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-extrabold tracking-tight text-tinta">Tarefas</h1>
          <p className="text-texto-suave">A roda de tarefas da semana.</p>
        </div>
        <div className="flex flex-wrap gap-2">
          {isAdmin && (
            <Button
              variant="secondary"
              size="sm"
              disabled={!choresQuery.isSuccess || chores.length === 0}
              onClick={() => setCloseOpen(true)}
            >
              Fechar semana
            </Button>
          )}
          <Button asChild size="sm">
            <Link to={paths.novaTarefa}>
              <Plus aria-hidden="true" className="size-4" />
              Nova tarefa
            </Link>
          </Button>
        </div>
      </header>

      <SwapBanner swaps={receivedSwaps} answeringId={answeringId} onAnswer={(s, a) => void handleAnswer(s, a)} />

      <Segmented
        aria-label="Filtro de tarefas"
        options={FILTER_OPTIONS}
        value={filter}
        onChange={setFilter}
      />

      {choresQuery.isPending ? (
        <p role="status" className="text-[15px] text-texto-suave">
          Carregando tarefas…
        </p>
      ) : choresQuery.isError ? (
        <Alert
          tone="atrasado"
          title="Não foi possível carregar as tarefas."
          description={errorMessage(choresQuery.error)}
          action={{ label: 'Tentar de novo', onClick: () => void choresQuery.refetch() }}
        />
      ) : visible.length === 0 ? (
        <EmptyState
          icon={<ClipboardList />}
          title={filter === 'minhas' ? 'Nada com você nesta semana' : 'Nenhuma tarefa ainda'}
          description={
            filter === 'minhas'
              ? 'Quando a roda chegar em você, suas tarefas aparecem aqui.'
              : 'Crie a primeira tarefa da casa e defina quem faz.'
          }
          action={
            filter === 'todas' ? (
              <Button asChild size="sm">
                <Link to={paths.novaTarefa}>Nova tarefa</Link>
              </Button>
            ) : undefined
          }
        />
      ) : (
        <ul aria-label="Tarefas da semana" className="flex flex-col gap-3">
          {visible.map((chore) => (
            <ChoreCard
              key={chore.id}
              chore={chore}
              meId={meId}
              isAdmin={isAdmin}
              busy={busyChoreIds.has(chore.id)}
              onComplete={handleComplete}
              onUncomplete={handleUncomplete}
              onReopen={setReopenTarget}
              onProposeSwap={setSwapSource}
              onReorder={openRotation}
            />
          ))}
        </ul>
      )}

      <Dialog
        open={reopenTarget !== null}
        onOpenChange={(open) => {
          if (!open) setReopenTarget(null);
        }}
        title="Reabrir tarefa?"
        description={
          reopenTarget
            ? `“${reopenTarget.description}” volta para atrasada agora, com o seu nome, e não gera +20 no fechamento da semana.`
            : undefined
        }
        confirmLabel="Reabrir"
        tone="danger"
        loading={reopen.isPending}
        onConfirm={confirmReopen}
      />

      <Dialog
        open={closeOpen}
        onOpenChange={setCloseOpen}
        title="Fechar a semana agora?"
        description="As tarefas feitas no prazo rendem +20 para quem fez. As que ficaram por fazer continuam atrasadas com o mesmo responsável, e a roda gira para as outras."
        confirmLabel="Fechar semana"
        loading={rotate.isPending}
        onConfirm={confirmClose}
      />

      <Panel
        open={swapSource !== null}
        onOpenChange={(open) => {
          if (!open) setSwapSource(null);
        }}
        title="Propor troca"
        description={
          swapSource
            ? `Escolha a tarefa de outro morador para trocar com “${swapSource.description}” só neste ciclo.`
            : undefined
        }
      >
        {swapCandidates.length === 0 ? (
          <p className="text-[15px] text-texto-suave">
            Nenhuma tarefa de outro morador está disponível para troca agora.
          </p>
        ) : (
          <ul aria-label="Tarefas para trocar" className="flex flex-col gap-2">
            {swapCandidates.map((candidate) => (
              <li key={candidate.id}>
                <button
                  type="button"
                  disabled={createSwap.isPending}
                  onClick={() => void proposeSwap(candidate)}
                  className="nt-press flex min-h-11 w-full flex-col items-start gap-0.5 rounded-[18px] bg-superficie px-4 py-3 text-left hover:bg-linha cursor-pointer disabled:cursor-not-allowed disabled:opacity-60"
                >
                  <span className="text-[15px] font-semibold text-tinta">{candidate.description}</span>
                  <span className="text-[13px] text-legenda">
                    Com {candidate.effectiveAssigneeName ?? 'outro morador'} ·{' '}
                    {CHORE_FREQUENCY_LABELS[candidate.frequency]}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </Panel>

      <Panel
        open={rotationTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRotationTarget(null);
        }}
        title="Reordenar roda"
        description={
          rotationTarget
            ? `Ordem em que “${rotationTarget.description}” passa pelos moradores.`
            : undefined
        }
        footer={
          <Button
            className="w-full"
            loading={updateRotation.isPending}
            onClick={() => void saveRotation()}
          >
            Salvar ordem
          </Button>
        }
      >
        <RotationOrderEditor order={rotationDraft} onChange={setRotationDraft} names={names} />
      </Panel>
    </section>
  );
}
