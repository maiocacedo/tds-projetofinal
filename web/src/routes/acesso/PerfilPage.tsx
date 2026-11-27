import { useQueryClient } from '@tanstack/react-query';
import { CalendarClock, Sparkles } from 'lucide-react';
import { useState } from 'react';
import {
  formatResidenceMonths,
  POINT_REASON_LABELS,
  updateMyProfile,
  useMyPointsQuery,
  type UpdateProfilePayload,
} from '../../api/profile';
import { queryKeys } from '../../api/queryKeys';
import { ApiError } from '../../auth/apiClient';
import { useSession } from '../../auth/SessionContext';
import { Alert, Avatar, StatusChip, useToast } from '../../components';
import { FichaForm } from './FichaForm';

function formatPointDate(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) return iso;
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  }).format(parsed);
}

export function PerfilPage() {
  const { user, refreshMe } = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const pointsQuery = useMyPointsQuery({ enabled: Boolean(user) });

  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!user) return null;

  const totalPoints = pointsQuery.data?.total ?? user.points.total;
  const monthPoints = pointsQuery.data?.thisMonth ?? user.points.thisMonth;
  const history = pointsQuery.data?.history ?? [];

  const handleSaveProfile = async (payload: UpdateProfilePayload) => {
    setServerError(null);
    setSubmitting(true);
    try {
      await updateMyProfile(payload);
      await refreshMe();
      await queryClient.invalidateQueries({ queryKey: queryKeys.houses });
      toast.show({
        message: 'Ficha atualizada com sucesso.',
        tone: 'feito',
      });
    } catch (error) {
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível atualizar sua ficha agora. Tente novamente.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl space-y-8">
      <header className="flex flex-wrap items-center justify-between gap-4 rounded-[28px] bg-superficie p-6">
        <div className="flex items-center gap-4">
          <Avatar name={user.name} size="lg" />
          <div className="space-y-1">
            <h1 className="text-[28px] font-extrabold leading-tight tracking-tight text-tinta">
              Perfil
            </h1>
            <p className="text-base font-bold text-tinta">{user.name}</p>
            <p className="text-sm text-texto-suave">{user.email}</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <StatusChip tone="neutro" className="bg-fundo px-3.5 py-1.5 text-tinta">
            <CalendarClock aria-hidden="true" className="size-3.5" />
            {formatResidenceMonths(user.residenceMonths)}
          </StatusChip>
          {user.houseName && (
            <StatusChip tone="feito" className="px-3.5 py-1.5">
              {user.houseName}
            </StatusChip>
          )}
        </div>
      </header>

      <section
        aria-labelledby="pontos-heading"
        className="space-y-4 rounded-[28px] border border-pontos-borda bg-pontos-fundo p-6"
      >
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <span
              aria-hidden="true"
              className="flex size-11 items-center justify-center rounded-full bg-pontos-texto text-fundo"
            >
              <Sparkles className="size-5" />
            </span>
            <div>
              <h2
                id="pontos-heading"
                className="text-xl font-extrabold tracking-tight text-tinta"
              >
                Seus pontos
              </h2>
              <p className="text-sm text-texto-suave">
                Pontos só somam e acompanham você em toda casa.
              </p>
            </div>
          </div>

          <div className="flex items-baseline gap-4">
            <div className="text-right">
              <p className="text-[28px] font-extrabold leading-none text-pontos-texto">
                {totalPoints} pts
              </p>
              <p className="mt-1 text-xs font-bold text-pontos-texto">
                {monthPoints} pts este mês
              </p>
            </div>
          </div>
        </div>

        {pointsQuery.isLoading && (
          <p role="status" className="text-sm text-texto-suave">
            Carregando histórico de pontos…
          </p>
        )}

        {pointsQuery.isError && (
          <Alert
            tone="vence-logo"
            title="Não foi possível carregar o histórico de pontos agora."
          />
        )}

        {!pointsQuery.isLoading && !pointsQuery.isError && history.length === 0 && (
          <p className="rounded-2xl bg-fundo/80 px-4 py-3 text-sm text-texto-suave">
            Você ainda não tem pontos registrados. Conclua tarefas no prazo,
            pague contas até o vencimento ou compre itens do mercado.
          </p>
        )}

        {history.length > 0 && (
          <ul className="divide-y divide-pontos-borda rounded-2xl bg-fundo/90 px-4">
            {history.map((item) => (
              <li
                key={item.id}
                className="flex items-center justify-between gap-4 py-3"
              >
                <div className="min-w-0 space-y-0.5">
                  <p className="text-sm font-bold text-tinta">
                    {POINT_REASON_LABELS[item.reason]}
                  </p>
                  {item.description && (
                    <p className="truncate text-xs text-texto-suave">
                      {item.description}
                    </p>
                  )}
                  <p className="text-xs text-legenda">
                    {formatPointDate(item.createdAt)}
                  </p>
                </div>
                <StatusChip tone="pontos" className="shrink-0">
                  +{item.amount} pts
                </StatusChip>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="editar-ficha-heading" className="space-y-4">
        <div className="space-y-1">
          <h2
            id="editar-ficha-heading"
            className="text-2xl font-extrabold tracking-tight text-tinta"
          >
            Sua ficha de convivência
          </h2>
          <p className="text-sm text-texto-suave">
            Atualize suas preferências sempre que sua rotina mudar.
          </p>
        </div>

        <FichaForm
          userName={user.name}
          residenceMonths={user.residenceMonths}
          pointsTotal={totalPoints}
          initialProfile={user.profile}
          submitLabel="Salvar alterações"
          submitting={submitting}
          serverError={serverError}
          onSubmit={handleSaveProfile}
        />
      </section>
    </div>
  );
}
