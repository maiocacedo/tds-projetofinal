import { ArrowRight, Calendar, Clock, Inbox, Vote } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  useMyMatchRequestsQuery,
  useWithdrawMatchRequestMutation,
  type MyMatchRequest,
} from '../../api/candidate';
import {
  Alert,
  Button,
  Dialog,
  EmptyState,
  StatusChip,
  useToast,
  type StatusTone,
} from '../../components';
import { buildPath, paths } from '../paths';

const STATUS_META: Record<string, { label: string; tone: StatusTone }> = {
  PENDING: { label: 'Em votação', tone: 'vence-logo' },
  ACCEPTED: { label: 'Aceita', tone: 'feito' },
  REJECTED: { label: 'Recusada', tone: 'atrasado' },
  CANCELLED: { label: 'Cancelada', tone: 'neutro' },
  WITHDRAWN: { label: 'Você desistiu', tone: 'neutro' },
  AWAITING_MOVE: { label: 'Aguardando mudança', tone: 'pontos' },
  EXPIRED: { label: 'Expirada', tone: 'atrasado' },
};

function getStatusMeta(status: string): { label: string; tone: StatusTone } {
  return STATUS_META[status] ?? { label: status, tone: 'neutro' };
}

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString('pt-BR', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function SolicitacoesPage() {
  const { data: requests, isLoading, isError, error, refetch } = useMyMatchRequestsQuery();
  const withdrawMutation = useWithdrawMatchRequestMutation();
  const toast = useToast();

  const [confirmTarget, setConfirmTarget] = useState<MyMatchRequest | null>(null);
  const [withdrawError, setWithdrawError] = useState<string | null>(null);

  const handleConfirmWithdraw = async () => {
    if (!confirmTarget) return;
    setWithdrawError(null);
    try {
      await withdrawMutation.mutateAsync(confirmTarget.id);
      toast.show({
        message: `Você desistiu do pedido para ${confirmTarget.houseName}.`,
        tone: 'neutro',
      });
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Não foi possível desistir agora. Tente novamente.';
      setWithdrawError(msg);
      throw err;
    }
  };

  return (
    <section className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-[28px] font-extrabold tracking-tight text-tinta">
          Solicitações
        </h1>
        <p className="text-[15px] text-texto-suave">
          Acompanhe o andamento da votação e o estado de cada pedido de vaga.
        </p>
      </div>

      {isLoading && (
        <div role="status" aria-live="polite" className="space-y-3">
          <span className="sr-only">Carregando suas solicitações…</span>
          {[0, 1].map((idx) => (
            <div
              key={idx}
              className="h-32 rounded-[24px] border border-linha bg-superficie/70 animate-pulse"
            />
          ))}
        </div>
      )}

      {isError && (
        <Alert
          tone="atrasado"
          title="Não foi possível carregar suas solicitações"
          description={
            error instanceof Error ? error.message : 'Tente novamente em instantes.'
          }
          action={{ label: 'Tentar de novo', onClick: () => void refetch() }}
        />
      )}

      {!isLoading && !isError && (requests?.length ?? 0) === 0 && (
        <EmptyState
          icon={<Inbox />}
          title="Nenhum pedido enviado ainda"
          description="Explore as repúblicas com vaga aberta e peça vaga nas que mais combinam com você."
          action={
            <Button asChild variant="primary">
              <Link to={paths.explorar}>Explorar casas</Link>
            </Button>
          }
        />
      )}

      {!isLoading && !isError && requests && requests.length > 0 && (
        <ul className="space-y-4">
          {requests.map((req) => {
            const statusMeta = getStatusMeta(req.status);
            const total = Math.max(0, req.votersTotal);
            const cast = Math.min(req.votesCast, total || req.votesCast);
            const progressPct = total > 0 ? Math.round((cast / total) * 100) : 0;

            return (
              <li
                key={req.id}
                className="rounded-[24px] border border-linha bg-fundo p-5 sm:p-6"
              >
                <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0 space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusChip tone={statusMeta.tone}>{statusMeta.label}</StatusChip>
                      <span className="inline-flex items-center gap-1 text-xs font-semibold text-legenda">
                        <Calendar aria-hidden="true" className="size-3.5" />
                        Enviado em {formatDateTime(req.createdAt)}
                      </span>
                    </div>

                    <h2 className="text-xl font-extrabold text-tinta">
                      <Link
                        to={buildPath.casaDetalhe(req.houseId)}
                        className="hover:text-tomate-escuro"
                      >
                        {req.houseName}
                      </Link>
                    </h2>

                    <div className="space-y-1.5 pt-1">
                      <p className="flex items-center gap-1.5 text-sm font-semibold text-texto-suave">
                        <Vote aria-hidden="true" className="size-4 text-legenda" />
                        <span>
                          {req.votesCast} de {req.votersTotal}{' '}
                          {req.votersTotal === 1 ? 'voto' : 'votos'}
                        </span>
                      </p>
                      <div
                        role="progressbar"
                        aria-label={`Progresso da votação em ${req.houseName}`}
                        aria-valuemin={0}
                        aria-valuemax={total}
                        aria-valuenow={cast}
                        className="h-2 w-full max-w-xs overflow-hidden rounded-full bg-superficie"
                      >
                        <div
                          className="h-full rounded-full bg-tomate transition-all duration-300"
                          style={{ width: `${progressPct}%` }}
                        />
                      </div>
                    </div>

                    {req.status === 'AWAITING_MOVE' && req.moveDeadline && (
                      <p className="flex items-center gap-1.5 text-sm font-bold text-pontos-texto">
                        <Clock aria-hidden="true" className="size-4" />
                        Vaga reservada até {formatDateTime(req.moveDeadline)}
                      </p>
                    )}
                  </div>

                  <div className="flex flex-wrap items-center gap-2">
                    {req.status === 'AWAITING_MOVE' && (
                      <Button asChild variant="primary" size="sm">
                        <Link to={buildPath.mudanca(req.id)}>
                          Confirmar mudança
                          <ArrowRight aria-hidden="true" className="size-4" />
                        </Link>
                      </Button>
                    )}

                    {req.status === 'PENDING' && (
                      <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => {
                          setWithdrawError(null);
                          setConfirmTarget(req);
                        }}
                      >
                        Desistir
                      </Button>
                    )}
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      )}

      <Dialog
        open={Boolean(confirmTarget)}
        onOpenChange={(open) => {
          if (!open) {
            setConfirmTarget(null);
            setWithdrawError(null);
          }
        }}
        title="Desistir desta solicitação?"
        description={
          confirmTarget
            ? `Seu pedido de vaga em ${confirmTarget.houseName} será retirado da votação.`
            : undefined
        }
        confirmLabel="Sim, desistir"
        cancelLabel="Manter pedido"
        tone="danger"
        loading={withdrawMutation.isPending}
        onConfirm={handleConfirmWithdraw}
      >
        {withdrawError && (
          <Alert
            tone="atrasado"
            title="Não foi possível desistir"
            description={withdrawError}
          />
        )}
      </Dialog>
    </section>
  );
}
