import { Lock } from 'lucide-react';
import { useId, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  describeError,
  useManagedHouseQuery,
  useUpdateHouseMutation,
  type ManagedHouse,
  type UpdateHousePayload,
} from '../../api/houseManagement';
import { useSession } from '../../auth/SessionContext';
import { Button, Counter, EmptyState, Segmented, Switch, useToast } from '../../components';
import { paths } from '../paths';

type VotingRule = UpdateHousePayload['votingRule'];

const VOTING_OPTIONS: { value: VotingRule; label: string }[] = [
  { value: 'MAJORITY', label: 'Maioria' },
  { value: 'UNANIMITY', label: 'Unanimidade' },
];

const MAX_VACANCIES = 20;

const inputClass =
  'min-h-11 rounded-[14px] border border-linha bg-fundo px-3 text-[15px] text-tinta focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/25';

function parseRent(raw: string): number | null | 'invalid' {
  const text = raw.trim();
  // "1.250,50" (pt-BR) ou "1250.50": com vírgula, pontos são milhar.
  const trimmed = text.includes(',') ? text.replace(/\./g, '').replace(',', '.') : text;
  if (trimmed === '') return null;
  const value = Number(trimmed);
  if (!Number.isFinite(value) || value < 0) return 'invalid';
  return Math.round(value * 100) / 100;
}

function formatRent(value: number | null): string {
  if (value === null) return '';
  return value.toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
}

function HouseSettingsForm({ house }: { house: ManagedHouse }) {
  const toast = useToast();
  const update = useUpdateHouseMutation(house.id);
  const nameId = useId();
  const rentId = useId();
  const rulesId = useId();
  const descriptionId = useId();

  const [name, setName] = useState(house.name);
  const [vacancies, setVacancies] = useState(house.vacancies);
  const [accepting, setAccepting] = useState(house.isAcceptingMatches);
  const [votingRule, setVotingRule] = useState<VotingRule>(house.votingRule);
  const [rent, setRent] = useState(formatRent(house.rentAmount));
  const [basicRules, setBasicRules] = useState(house.basicRules ?? '');
  const [description, setDescription] = useState(house.description ?? '');
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    const rentAmount = parseRent(rent);
    if (rentAmount === 'invalid') {
      setError('Informe o aluguel em reais, por exemplo 850 ou 850,50.');
      return;
    }
    if (!name.trim()) {
      setError('Dê um nome para a casa.');
      return;
    }
    update.mutate(
      {
        name: name.trim(),
        address: house.address ?? null,
        description: description.trim() || null,
        basicRules: basicRules.trim() || null,
        rentAmount,
        allowsPets: house.allowsPets,
        allowsSmokers: house.allowsSmokers,
        expectedOrganizationLevel: house.expectedOrganizationLevel,
        visitorTolerance: house.visitorTolerance,
        hasFurniture: house.hasFurniture,
        votingRule,
        isAcceptingMatches: accepting,
        vacancies,
      },
      {
        onSuccess: () => toast.show({ message: 'Configurações da casa salvas.', tone: 'feito' }),
        onError: (err) => {
          const message = describeError(err);
          setError(message);
          toast.show({ message, tone: 'atrasado' });
        },
      },
    );
  };

  const searchHint = !accepting
    ? 'Pedidos pausados: a casa não aparece na busca, mas as vagas continuam guardadas.'
    : vacancies === 0
      ? 'Sem vagas livres: a casa sai da busca até abrir uma vaga.'
      : `A casa aparece na busca com ${vacancies} ${vacancies === 1 ? 'vaga' : 'vagas'}.`;

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-6" noValidate>
      <fieldset className="flex flex-col gap-4 rounded-[22px] border border-linha bg-fundo p-5">
        <legend className="px-1 text-lg font-extrabold text-tinta">Vagas e pedidos</legend>
        <Counter
          label="Vagas livres"
          value={vacancies}
          onChange={setVacancies}
          min={0}
          max={MAX_VACANCIES}
        />
        <Switch
          label="Receber pedidos"
          description="Pausa novos pedidos de vaga sem zerar as vagas."
          checked={accepting}
          onCheckedChange={setAccepting}
        />
        <p className="text-sm text-texto-suave">{searchHint}</p>
      </fieldset>

      <fieldset className="flex flex-col gap-3 rounded-[22px] border border-linha bg-fundo p-5">
        <legend className="px-1 text-lg font-extrabold text-tinta">Regra de votação</legend>
        <p className="text-sm text-texto-suave">
          Vale para aceitar candidatos e para remover moradores.
        </p>
        <Segmented
          aria-label="Regra de votação"
          options={VOTING_OPTIONS}
          value={votingRule}
          onChange={setVotingRule}
        />
      </fieldset>

      <fieldset className="flex flex-col gap-4 rounded-[22px] border border-linha bg-fundo p-5">
        <legend className="px-1 text-lg font-extrabold text-tinta">Dados da casa</legend>
        <div className="flex flex-col gap-1.5">
          <label htmlFor={nameId} className="text-sm font-semibold text-tinta">
            Nome da casa
          </label>
          <input
            id={nameId}
            value={name}
            onChange={(event) => setName(event.target.value)}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor={rentId} className="text-sm font-semibold text-tinta">
            Aluguel por pessoa (R$)
          </label>
          <input
            id={rentId}
            inputMode="decimal"
            value={rent}
            placeholder="Sem aluguel informado"
            onChange={(event) => setRent(event.target.value)}
            className={inputClass}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor={rulesId} className="text-sm font-semibold text-tinta">
            Regras da casa
          </label>
          <textarea
            id={rulesId}
            rows={3}
            value={basicRules}
            onChange={(event) => setBasicRules(event.target.value)}
            className={`${inputClass} py-2`}
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor={descriptionId} className="text-sm font-semibold text-tinta">
            Descrição
          </label>
          <textarea
            id={descriptionId}
            rows={3}
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            className={`${inputClass} py-2`}
          />
        </div>
      </fieldset>

      {error && (
        <p role="alert" className="text-sm font-semibold text-atrasado-texto">
          {error}
        </p>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button asChild variant="secondary">
          <Link to={paths.casa}>Voltar</Link>
        </Button>
        <Button type="submit" loading={update.isPending}>
          Salvar configurações
        </Button>
      </div>
    </form>
  );
}

export function ConfigurarCasaPage() {
  const { user } = useSession();
  const houseQuery = useManagedHouseQuery(user?.houseId);

  if (!user) return null;

  const house = houseQuery.data;
  const viewerIsAdmin = house
    ? house.members.find((member) => member.id === user.id)?.isHouseAdmin === true
    : user.isHouseAdmin === true;

  return (
    <section className="mx-auto flex max-w-2xl flex-col gap-6">
      <header className="flex flex-col gap-2">
        <p className="text-sm font-bold uppercase tracking-wide text-tomate">Casa</p>
        <h1 className="text-3xl font-extrabold tracking-tight text-tinta">Configurar casa</h1>
        <p className="text-texto-suave">
          Vagas, pedidos, regra de votação e os dados que os candidatos veem.
        </p>
      </header>

      {!viewerIsAdmin ? (
        <EmptyState
          icon={<Lock />}
          title="Só admins configuram a casa"
          description="Peça a um admin da casa para mudar vagas, pedidos ou a regra de votação."
          action={
            <Button asChild variant="secondary">
              <Link to={paths.casa}>Voltar para a casa</Link>
            </Button>
          }
        />
      ) : houseQuery.isPending ? (
        <p role="status" className="text-texto-suave">
          Carregando configurações…
        </p>
      ) : houseQuery.isError || !house ? (
        <p role="alert" className="text-atrasado-texto">
          {describeError(houseQuery.error)}
        </p>
      ) : (
        <HouseSettingsForm key={house.id} house={house} />
      )}
    </section>
  );
}
