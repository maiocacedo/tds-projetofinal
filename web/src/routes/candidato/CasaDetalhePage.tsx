import {
  ArrowLeft,
  CheckCircle2,
  Cigarette,
  HelpCircle,
  Home,
  MapPin,
  PawPrint,
  Send,
  ShieldCheck,
  Sofa,
  Sparkles,
  Users,
  Vote,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  explainCompatibility,
  resolveHouseCompatibilityScore,
  useCreateMatchRequestMutation,
  useHouseDetailQuery,
  useMyMatchRequestsQuery,
  type CriterionStatus,
} from '../../api/candidate';
import { PERMISSIONS } from '../../api/me';
import { useSession } from '../../auth/SessionContext';
import {
  Alert,
  Avatar,
  Button,
  CompatibilityRing,
  EmptyState,
  Panel,
  StatusChip,
  useToast,
  type StatusTone,
} from '../../components';
import { buildPath, paths } from '../paths';

function formatRent(amount: number | null): string {
  if (amount == null) return 'Aluguel a combinar';
  return `${amount.toLocaleString('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: amount % 1 === 0 ? 0 : 2,
  })}/mês`;
}

function criterionBadge(status: CriterionStatus): { label: string; tone: StatusTone } {
  if (status === 'match') return { label: 'Combina', tone: 'feito' };
  if (status === 'partial') return { label: 'Parcial', tone: 'vence-logo' };
  return { label: 'Diverge', tone: 'atrasado' };
}

export function CasaDetalhePage() {
  const { id } = useParams<{ id: string }>();
  const { user, hasPermission } = useSession();
  const toast = useToast();

  const [whyOpen, setWhyOpen] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const canMatch = hasPermission(PERMISSIONS.MATCH);
  const { data: house, isLoading, isError, error } = useHouseDetailQuery(id);
  const { data: myRequests } = useMyMatchRequestsQuery({ enabled: canMatch });
  const createMatchMutation = useCreateMatchRequestMutation();

  const profileComplete = Boolean(user?.profileComplete);

  const explanation = useMemo(
    () => (house ? explainCompatibility(user?.profile, house) : { score: null, criteria: [] }),
    [house, user?.profile],
  );

  const displayScore = house
    ? resolveHouseCompatibilityScore(user?.profile, profileComplete, house)
    : null;

  const activeRequest = useMemo(() => {
    if (!house || !myRequests) return undefined;
    return myRequests.find(
      (req) =>
        req.houseId === house.id &&
        (req.status === 'PENDING' || req.status === 'AWAITING_MOVE'),
    );
  }, [house, myRequests]);

  const isOwnHouse = Boolean(house && user?.houseId === house.id);
  const acceptsNewRequest = Boolean(
    house && house.isAcceptingMatches && house.vacancies > 0,
  );

  const handleRequestSpot = async () => {
    if (!house) return;
    setSubmitError(null);
    try {
      await createMatchMutation.mutateAsync(house.id);
      toast.show({
        message: `Pedido enviado para ${house.name}! Os moradores vão votar.`,
        tone: 'feito',
      });
    } catch (err) {
      const msg =
        err instanceof Error
          ? err.message
          : 'Não foi possível enviar o pedido agora. Tente novamente.';
      setSubmitError(msg);
    }
  };

  if (isLoading) {
    return (
      <section className="space-y-4">
        <Link
          to={paths.explorar}
          className="inline-flex min-h-11 items-center gap-1.5 text-sm font-bold text-texto-suave hover:text-tinta"
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          Voltar para Explorar
        </Link>
        <h1 className="text-[28px] font-extrabold tracking-tight text-tinta">
          Detalhe da casa
        </h1>
        <div
          role="status"
          aria-live="polite"
          className="h-64 rounded-[28px] border border-linha bg-superficie/70 p-6 animate-pulse"
        >
          <span className="sr-only">Carregando detalhes da casa…</span>
        </div>
      </section>
    );
  }

  if (isError || !house) {
    return (
      <section className="space-y-4">
        <Link
          to={paths.explorar}
          className="inline-flex min-h-11 items-center gap-1.5 text-sm font-bold text-texto-suave hover:text-tinta"
        >
          <ArrowLeft aria-hidden="true" className="size-4" />
          Voltar para Explorar
        </Link>
        <h1 className="text-[28px] font-extrabold tracking-tight text-tinta">
          Detalhe da casa
        </h1>
        <EmptyState
          icon={<Home />}
          title="Casa não encontrada"
          description={
            error instanceof Error
              ? error.message
              : 'Essa república pode ter fechado as vagas ou não existe mais.'
          }
          action={
            <Button asChild variant="secondary">
              <Link to={paths.explorar}>Voltar para Explorar</Link>
            </Button>
          }
        />
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <Link
        to={paths.explorar}
        className="inline-flex min-h-11 items-center gap-1.5 text-sm font-bold text-texto-suave hover:text-tinta"
      >
        <ArrowLeft aria-hidden="true" className="size-4" />
        Voltar para Explorar
      </Link>

      <div className="rounded-[32px] border border-linha bg-superficie/60 p-6 md:p-8">
        <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
          <div className="min-w-0 space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <StatusChip tone={acceptsNewRequest ? 'feito' : 'neutro'}>
                {!house.isAcceptingMatches
                  ? 'Pedidos pausados'
                  : house.vacancies === 0
                    ? 'Sem vagas'
                    : house.vacancies === 1
                      ? '1 vaga aberta'
                      : `${house.vacancies} vagas abertas`}
              </StatusChip>
              <StatusChip tone="neutro">
                <Vote aria-hidden="true" className="size-3.5" />
                {house.votingRule === 'UNANIMITY'
                  ? 'Votação por unanimidade'
                  : 'Votação por maioria'}
              </StatusChip>
            </div>

            <h1 className="text-[30px] font-extrabold leading-tight tracking-tight text-tinta">
              <span className="sr-only">Detalhe da casa: </span>
              {house.name}
            </h1>

            {house.address && (
              <p className="flex items-center gap-2 text-[15px] font-medium text-texto-suave">
                <MapPin aria-hidden="true" className="size-4 shrink-0 text-legenda" />
                {house.address}
              </p>
            )}

            <p className="text-2xl font-extrabold text-tinta">
              {formatRent(house.rentAmount)}
            </p>
          </div>

          <div className="flex flex-col items-start gap-3 md:items-end">
            <CompatibilityRing score={displayScore} size="lg" />
            {displayScore !== null ? (
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setWhyOpen(true)}
              >
                <HelpCircle aria-hidden="true" className="size-4" />
                Por que {displayScore}%?
              </Button>
            ) : (
              <p className="max-w-[26ch] text-xs font-semibold text-legenda md:text-right">
                Complete as 5 respostas da ficha para ver a compatibilidade.
              </p>
            )}
          </div>
        </div>

        {submitError && (
          <Alert
            tone="atrasado"
            title="Não foi possível enviar o pedido"
            description={submitError}
            className="mt-5"
          />
        )}

        {canMatch && (
          <div className="mt-6 flex flex-wrap items-center gap-3 border-t border-linha pt-5">
            {isOwnHouse ? (
              <>
                <Button variant="secondary" disabled>
                  Você já mora nesta casa
                </Button>
                <span className="text-sm text-texto-suave">
                  Esta é a sua república atual.
                </span>
              </>
            ) : activeRequest ? (
              activeRequest.status === 'AWAITING_MOVE' ? (
                <>
                  <Button asChild variant="primary" size="lg">
                    <Link to={buildPath.mudanca(activeRequest.id)}>
                      Confirmar mudança
                    </Link>
                  </Button>
                  <span className="text-sm font-semibold text-pontos-texto">
                    Você foi aceito! Confirme sua mudança antes do prazo.
                  </span>
                </>
              ) : (
                <>
                  <Button variant="secondary" disabled>
                    <CheckCircle2 aria-hidden="true" className="size-4" />
                    Pedido enviado
                  </Button>
                  <Link
                    to={paths.solicitacoes}
                    className="inline-flex min-h-11 items-center text-sm font-bold text-tomate-escuro underline underline-offset-4"
                  >
                    Acompanhar em Solicitações
                  </Link>
                </>
              )
            ) : !acceptsNewRequest ? (
              <>
                <Button variant="secondary" disabled>
                  Pedir vaga
                </Button>
                <span className="text-sm text-texto-suave">
                  Esta casa não está recebendo pedidos no momento.
                </span>
              </>
            ) : (
              <Button
                variant="primary"
                size="lg"
                loading={createMatchMutation.isPending}
                onClick={() => void handleRequestSpot()}
              >
                <Send aria-hidden="true" className="size-4" />
                Pedir vaga
              </Button>
            )}
          </div>
        )}
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <div className="space-y-3 rounded-[28px] border border-linha bg-fundo p-6">
            <h2 className="text-xl font-extrabold text-tinta">Sobre a casa</h2>
            <p className="text-[16px] leading-relaxed text-texto-suave">
              {house.description || 'Os moradores ainda não adicionaram uma descrição.'}
            </p>
            {house.basicRules && (
              <div className="mt-4 rounded-2xl bg-superficie p-4">
                <h3 className="text-sm font-bold text-tinta">Regras básicas</h3>
                <p className="mt-1 text-[15px] text-texto-suave">{house.basicRules}</p>
              </div>
            )}
          </div>

          <div className="space-y-4 rounded-[28px] border border-linha bg-fundo p-6">
            <h2 className="text-xl font-extrabold text-tinta">Convivência</h2>
            <dl className="grid gap-3 sm:grid-cols-2">
              <div className="flex items-center gap-3 rounded-2xl bg-superficie p-3.5">
                <PawPrint aria-hidden="true" className="size-5 shrink-0 text-tomate" />
                <div>
                  <dt className="text-xs font-bold text-legenda">Pets</dt>
                  <dd className="text-[15px] font-bold text-tinta">
                    {house.allowsPets ? 'Aceita pets' : 'Não aceita pets'}
                  </dd>
                </div>
              </div>
              <div className="flex items-center gap-3 rounded-2xl bg-superficie p-3.5">
                <Cigarette aria-hidden="true" className="size-5 shrink-0 text-tomate" />
                <div>
                  <dt className="text-xs font-bold text-legenda">Cigarro</dt>
                  <dd className="text-[15px] font-bold text-tinta">
                    {house.allowsSmokers ? 'Permite fumar' : 'Não permite fumar'}
                  </dd>
                </div>
              </div>
              <div className="flex items-center gap-3 rounded-2xl bg-superficie p-3.5">
                <Sofa aria-hidden="true" className="size-5 shrink-0 text-tomate" />
                <div>
                  <dt className="text-xs font-bold text-legenda">Mobília</dt>
                  <dd className="text-[15px] font-bold text-tinta">
                    {house.hasFurniture ? 'Casa mobiliada' : 'Sem mobília'}
                  </dd>
                </div>
              </div>
              <div className="flex items-center gap-3 rounded-2xl bg-superficie p-3.5">
                <Sparkles aria-hidden="true" className="size-5 shrink-0 text-tomate" />
                <div>
                  <dt className="text-xs font-bold text-legenda">Organização esperada</dt>
                  <dd className="text-[15px] font-bold text-tinta">
                    Nível {house.expectedOrganizationLevel} de 5
                  </dd>
                </div>
              </div>
              <div className="flex items-center gap-3 rounded-2xl bg-superficie p-3.5 sm:col-span-2">
                <Users aria-hidden="true" className="size-5 shrink-0 text-tomate" />
                <div>
                  <dt className="text-xs font-bold text-legenda">Tolerância a visitas</dt>
                  <dd className="text-[15px] font-bold text-tinta">
                    Nível {house.visitorTolerance} de 5
                  </dd>
                </div>
              </div>
            </dl>
          </div>
        </div>

        <aside className="space-y-4 rounded-[28px] border border-linha bg-fundo p-6">
          <div className="flex items-center justify-between">
            <h2 className="text-xl font-extrabold text-tinta">Moradores</h2>
            <span className="text-sm font-bold text-legenda">
              {house.members.length}
            </span>
          </div>
          {house.members.length === 0 ? (
            <p className="text-sm text-texto-suave">Nenhum morador listado ainda.</p>
          ) : (
            <ul className="space-y-3">
              {house.members.map((member) => (
                <li
                  key={member.id}
                  className="flex items-center justify-between gap-3 rounded-2xl bg-superficie/70 p-3"
                >
                  <div className="flex min-w-0 items-center gap-3">
                    <Avatar name={member.name} size="sm" />
                    <span className="truncate text-[15px] font-bold text-tinta">
                      {member.name}
                    </span>
                  </div>
                  {member.isHouseAdmin && (
                    <StatusChip tone="pontos">
                      <ShieldCheck aria-hidden="true" className="size-3.5" />
                      Admin
                    </StatusChip>
                  )}
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>

      <Panel
        open={whyOpen}
        onOpenChange={setWhyOpen}
        title={displayScore !== null ? `Por que ${displayScore}%?` : 'Compatibilidade'}
        description={`Veja como os 5 critérios (20 pontos cada) fecharam a conta para ${house.name}.`}
      >
        {explanation.criteria.length > 0 ? (
          <ul className="space-y-3">
            {explanation.criteria.map((criterion) => {
              const badge = criterionBadge(criterion.status);
              return (
                <li
                  key={criterion.key}
                  className="rounded-2xl border border-linha bg-superficie/60 p-4"
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-[15px] font-bold text-tinta">
                      {criterion.label}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-extrabold text-tinta">
                        {criterion.points}/{criterion.maxPoints} pts
                      </span>
                      <StatusChip tone={badge.tone}>{badge.label}</StatusChip>
                    </div>
                  </div>
                  <p className="mt-1.5 text-sm text-texto-suave">{criterion.summary}</p>
                </li>
              );
            })}
          </ul>
        ) : (
          <p className="text-[15px] text-texto-suave">
            Complete as 5 perguntas da sua ficha para ver o cálculo detalhado.
          </p>
        )}
      </Panel>
    </section>
  );
}
