import {
  Cigarette,
  Compass,
  HelpCircle,
  MapPin,
  PawPrint,
  Plus,
  Search,
  Sofa,
  Users,
} from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  explainCompatibility,
  resolveHouseCompatibilityScore,
  useHousesQuery,
  type CriterionStatus,
  type House,
  type HouseFilters,
} from '../../api/candidate';
import { PERMISSIONS } from '../../api/me';
import { useSession } from '../../auth/SessionContext';
import {
  Alert,
  Button,
  CompatibilityRing,
  EmptyState,
  Panel,
  Segmented,
  StatusChip,
  Switch,
  type StatusTone,
} from '../../components';
import { buildPath, paths } from '../paths';

type RentFilterValue = 'all' | '800' | '1000' | '1500';
type SortMode = 'compatibility' | 'rent';

const RENT_OPTIONS: { value: RentFilterValue; label: string }[] = [
  { value: 'all', label: 'Qualquer' },
  { value: '800', label: 'Até R$ 800' },
  { value: '1000', label: 'Até R$ 1.000' },
  { value: '1500', label: 'Até R$ 1.500' },
];

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: 'compatibility', label: 'Maior match' },
  { value: 'rent', label: 'Menor aluguel' },
];

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

export function ExplorarPage() {
  const { user, hasPermission } = useSession();
  const navigate = useNavigate();

  const [searchTerm, setSearchTerm] = useState('');
  const [rentFilter, setRentFilter] = useState<RentFilterValue>('all');
  const [onlyPets, setOnlyPets] = useState(false);
  const [onlySmokers, setOnlySmokers] = useState(false);
  const [onlyFurnished, setOnlyFurnished] = useState(false);
  const [sortBy, setSortBy] = useState<SortMode>('compatibility');
  const [explainedHouse, setExplainedHouse] = useState<House | null>(null);

  const apiFilters = useMemo<HouseFilters>(
    () => ({
      maxRent: rentFilter === 'all' ? undefined : Number(rentFilter),
      allowsPets: onlyPets ? true : undefined,
      allowsSmokers: onlySmokers ? true : undefined,
    }),
    [rentFilter, onlyPets, onlySmokers],
  );

  const { data: houses, isLoading, isError, error, refetch } = useHousesQuery(apiFilters);

  const profileComplete = Boolean(user?.profileComplete);
  const canCreateHouse = !user?.houseId && hasPermission(PERMISSIONS.MANAGE_HOUSE);

  const hasActiveFilters =
    searchTerm.trim().length > 0 ||
    rentFilter !== 'all' ||
    onlyPets ||
    onlySmokers ||
    onlyFurnished;

  const clearFilters = () => {
    setSearchTerm('');
    setRentFilter('all');
    setOnlyPets(false);
    setOnlySmokers(false);
    setOnlyFurnished(false);
  };

  const visibleHouses = useMemo(() => {
    const query = searchTerm.trim().toLocaleLowerCase('pt-BR');
    const filtered = (houses ?? []).filter((house) => {
      if (onlyFurnished && !house.hasFurniture) return false;
      if (!query) return true;
      const haystack = [house.name, house.address ?? '', house.description ?? '']
        .join(' ')
        .toLocaleLowerCase('pt-BR');
      return haystack.includes(query);
    });

    const scored = filtered.map((house) => ({
      house,
      score: resolveHouseCompatibilityScore(user?.profile, profileComplete, house),
    }));

    scored.sort((a, b) => {
      if (sortBy === 'rent') {
        const rentA = a.house.rentAmount ?? Number.POSITIVE_INFINITY;
        const rentB = b.house.rentAmount ?? Number.POSITIVE_INFINITY;
        if (rentA !== rentB) return rentA - rentB;
      }
      const scoreA = a.score ?? -1;
      const scoreB = b.score ?? -1;
      if (scoreA !== scoreB) return scoreB - scoreA;
      return a.house.name.localeCompare(b.house.name, 'pt-BR');
    });

    return scored;
  }, [houses, searchTerm, onlyFurnished, sortBy, user?.profile, profileComplete]);

  const panelExplanation = useMemo(
    () => (explainedHouse ? explainCompatibility(user?.profile, explainedHouse) : null),
    [explainedHouse, user?.profile],
  );
  const panelScore = explainedHouse
    ? resolveHouseCompatibilityScore(user?.profile, profileComplete, explainedHouse)
    : null;

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="space-y-1">
          <h1 className="text-[28px] font-extrabold tracking-tight text-tinta">Explorar</h1>
          <p className="text-[15px] text-texto-suave">
            Repúblicas com vaga aberta, ordenadas pelo que combina com a sua ficha.
          </p>
        </div>
        {canCreateHouse && (
          <Button asChild variant="primary" size="md">
            <Link to={paths.criarCasa}>
              <Plus aria-hidden="true" className="size-4" />
              Criar casa
            </Link>
          </Button>
        )}
      </div>

      {user?.houseId && (
        <Alert
          tone="neutro"
          title="Você já mora em uma casa"
          description="Você pode pedir vaga em outra república. Se for aceito, a vaga fica reservada por 72 h para você confirmar a mudança."
        />
      )}

      {user && !profileComplete && (
        <Alert
          tone="pontos"
          title="Complete sua ficha para ver a compatibilidade"
          description="Responda às 5 perguntas de convivência para calcular seu percentual com cada casa."
          action={{
            label: 'Completar ficha',
            onClick: () => navigate(paths.onboarding),
          }}
        />
      )}

      <section
        aria-label="Filtros de busca"
        className="space-y-4 rounded-[28px] border border-linha bg-superficie/65 p-5"
      >
        <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_280px]">
          <div className="space-y-1.5">
            <label htmlFor="explorar-busca" className="text-sm font-semibold text-tinta">
              Buscar por nome ou endereço
            </label>
            <div className="relative">
              <Search
                aria-hidden="true"
                className="pointer-events-none absolute left-3.5 top-1/2 size-4 -translate-y-1/2 text-legenda"
              />
              <input
                id="explorar-busca"
                type="search"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                placeholder="Ex.: Jabuticaba, Centro, perto da faculdade…"
                className="min-h-11 w-full rounded-full border border-linha bg-fundo pl-10 pr-4 text-[15px] text-tinta placeholder:text-legenda focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/20"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <span className="block text-sm font-semibold text-tinta">Ordenar por</span>
            <Segmented
              aria-label="Ordenar casas por"
              options={SORT_OPTIONS}
              value={sortBy}
              onChange={setSortBy}
            />
          </div>
        </div>

        <div className="space-y-1.5">
          <span className="block text-sm font-semibold text-tinta">Aluguel mensal</span>
          <Segmented
            aria-label="Filtrar por aluguel máximo"
            options={RENT_OPTIONS}
            value={rentFilter}
            onChange={setRentFilter}
          />
        </div>

        <div className="grid gap-3 pt-1 sm:grid-cols-3">
          <Switch
            label="Aceita pets"
            checked={onlyPets}
            onCheckedChange={setOnlyPets}
            className="rounded-2xl bg-fundo px-3.5 py-2 border border-linha"
          />
          <Switch
            label="Permite fumar"
            checked={onlySmokers}
            onCheckedChange={setOnlySmokers}
            className="rounded-2xl bg-fundo px-3.5 py-2 border border-linha"
          />
          <Switch
            label="Mobiliada"
            checked={onlyFurnished}
            onCheckedChange={setOnlyFurnished}
            className="rounded-2xl bg-fundo px-3.5 py-2 border border-linha"
          />
        </div>

        {hasActiveFilters && (
          <div className="flex justify-end">
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              Limpar filtros
            </Button>
          </div>
        )}
      </section>

      {isLoading && (
        <div
          role="status"
          aria-live="polite"
          className="grid gap-4 md:grid-cols-2"
        >
          <span className="sr-only">Carregando casas…</span>
          {[0, 1].map((idx) => (
            <div
              key={idx}
              className="h-52 rounded-[28px] border border-linha bg-superficie/70 p-6 animate-pulse"
            />
          ))}
        </div>
      )}

      {isError && (
        <Alert
          tone="atrasado"
          title="Não foi possível carregar as casas"
          description={
            error instanceof Error
              ? error.message
              : 'Confira sua conexão e tente novamente.'
          }
          action={{ label: 'Tentar de novo', onClick: () => void refetch() }}
        />
      )}

      {!isLoading && !isError && visibleHouses.length === 0 && (
        <EmptyState
          icon={<Compass />}
          title="Nenhuma casa encontrada com esses filtros"
          description={
            hasActiveFilters
              ? 'Tente ampliar o limite de aluguel ou desligar algum filtro para ver mais repúblicas.'
              : 'Ainda não há repúblicas com vagas abertas. Que tal abrir a sua?'
          }
          action={
            hasActiveFilters ? (
              <Button variant="secondary" onClick={clearFilters}>
                Limpar filtros
              </Button>
            ) : canCreateHouse ? (
              <Button asChild variant="primary">
                <Link to={paths.criarCasa}>Criar casa</Link>
              </Button>
            ) : undefined
          }
        />
      )}

      {!isLoading && !isError && visibleHouses.length > 0 && (
        <div className="grid gap-4 md:grid-cols-2">
          {visibleHouses.map(({ house, score }) => (
            <article
              key={house.id}
              className="flex flex-col justify-between gap-4 rounded-[28px] border border-linha bg-fundo p-6 transition-shadow hover:shadow-sm"
            >
              <div className="space-y-3">
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0 space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <StatusChip tone="feito">
                        {house.vacancies === 1
                          ? '1 vaga'
                          : `${house.vacancies} vagas`}
                      </StatusChip>
                      {user?.houseId === house.id && (
                        <StatusChip tone="pontos">Sua casa atual</StatusChip>
                      )}
                    </div>
                    <h2 className="text-xl font-extrabold leading-tight text-tinta">
                      <Link
                        to={buildPath.casaDetalhe(house.id)}
                        className="hover:text-tomate-escuro"
                      >
                        {house.name}
                      </Link>
                    </h2>
                    {house.address && (
                      <p className="flex items-center gap-1.5 text-[13px] font-medium text-legenda">
                        <MapPin aria-hidden="true" className="size-3.5 shrink-0" />
                        <span className="truncate">{house.address}</span>
                      </p>
                    )}
                  </div>

                  <div className="flex shrink-0 flex-col items-end gap-1">
                    <CompatibilityRing score={score} size="md" />
                    {score === null && (
                      <span className="text-[11px] font-semibold text-legenda">
                        Complete a ficha
                      </span>
                    )}
                  </div>
                </div>

                <p className="text-lg font-extrabold text-tinta">
                  {formatRent(house.rentAmount)}
                </p>

                {house.description && (
                  <p className="line-clamp-2 text-[15px] text-texto-suave">
                    {house.description}
                  </p>
                )}

                <ul className="flex flex-wrap gap-2 pt-1 text-xs font-semibold text-texto-suave">
                  <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                    <PawPrint aria-hidden="true" className="size-3.5" />
                    {house.allowsPets ? 'Aceita pets' : 'Sem pets'}
                  </li>
                  <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                    <Cigarette aria-hidden="true" className="size-3.5" />
                    {house.allowsSmokers ? 'Permite fumar' : 'Não fumantes'}
                  </li>
                  <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                    <Sofa aria-hidden="true" className="size-3.5" />
                    {house.hasFurniture ? 'Mobiliada' : 'Sem mobília'}
                  </li>
                  <li className="inline-flex items-center gap-1 rounded-full bg-superficie px-2.5 py-1">
                    <Users aria-hidden="true" className="size-3.5" />
                    {house.members.length === 1
                      ? '1 morador'
                      : `${house.members.length} moradores`}
                  </li>
                </ul>
              </div>

              <div className="flex flex-wrap items-center justify-between gap-2 border-t border-linha pt-4">
                {score !== null ? (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => setExplainedHouse(house)}
                  >
                    <HelpCircle aria-hidden="true" className="size-4" />
                    Por que {score}%?
                  </Button>
                ) : (
                  <span className="text-xs font-semibold text-legenda">
                    Ficha incompleta para o cálculo
                  </span>
                )}
                <Button asChild variant="secondary" size="sm">
                  <Link to={buildPath.casaDetalhe(house.id)}>Ver detalhes</Link>
                </Button>
              </div>
            </article>
          ))}
        </div>
      )}

      <Panel
        open={Boolean(explainedHouse)}
        onOpenChange={(open) => {
          if (!open) setExplainedHouse(null);
        }}
        title={panelScore !== null ? `Por que ${panelScore}%?` : 'Compatibilidade'}
        description={
          explainedHouse
            ? `Cada um dos 5 critérios vale até 20 pontos para ${explainedHouse.name}.`
            : undefined
        }
        footer={
          explainedHouse && (
            <Button asChild variant="primary" className="w-full">
              <Link to={buildPath.casaDetalhe(explainedHouse.id)}>
                Abrir detalhes da casa
              </Link>
            </Button>
          )
        }
      >
        {panelExplanation && panelExplanation.criteria.length > 0 ? (
          <ul className="space-y-3">
            {panelExplanation.criteria.map((criterion) => {
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
            Complete as 5 perguntas da sua ficha para ver o detalhamento critério por critério.
          </p>
        )}
      </Panel>
    </section>
  );
}
