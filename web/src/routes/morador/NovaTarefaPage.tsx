import { ArrowDown, ArrowUp } from 'lucide-react';
import { useId, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  CHORE_FREQUENCIES,
  CHORE_FREQUENCY_LABELS,
  useCreateChoreMutation,
  type ChoreFrequency,
  type ChoreType,
} from '../../api/chores';
import { translateApiError } from '../../api/errors';
import { useHouseDetailQuery } from '../../api/houses';
import { ApiError } from '../../auth/apiClient';
import { useSession } from '../../auth/SessionContext';
import { Alert, Button, Segmented, Switch, useToast } from '../../components';
import { paths } from '../paths';

export interface RotationOrderEditorProps {
  order: string[];
  onChange: (order: string[]) => void;
  /** Nome de cada morador por id. */
  names: ReadonlyMap<string, string>;
  disabled?: boolean;
}

/** Ordem da roda com setas ↑/↓; cada seta diz quem ela move e para onde. */
export function RotationOrderEditor({ order, onChange, names, disabled = false }: RotationOrderEditorProps) {
  const move = (index: number, delta: -1 | 1) => {
    const target = index + delta;
    if (target < 0 || target >= order.length) return;
    const next = [...order];
    const [item] = next.splice(index, 1);
    if (item === undefined) return;
    next.splice(target, 0, item);
    onChange(next);
  };

  return (
    <ol aria-label="Ordem da roda" className="flex flex-col gap-2">
      {order.map((id, index) => {
        const name = names.get(id) ?? 'Morador';
        return (
          <li
            key={id}
            className="flex min-h-11 items-center gap-3 rounded-[18px] bg-superficie py-1.5 pl-4 pr-1.5"
          >
            <span
              aria-hidden="true"
              className="flex size-7 shrink-0 items-center justify-center rounded-full bg-fundo text-sm font-extrabold text-tinta"
            >
              {index + 1}
            </span>
            <span className="min-w-0 flex-1 truncate text-[15px] font-semibold text-tinta">
              {name}
            </span>
            <button
              type="button"
              aria-label={`Mover ${name} para cima`}
              disabled={disabled || index === 0}
              onClick={() => move(index, -1)}
              className="nt-press inline-flex size-11 shrink-0 items-center justify-center rounded-full text-tinta hover:bg-linha cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
            >
              <ArrowUp aria-hidden="true" className="h-4 w-4" />
            </button>
            <button
              type="button"
              aria-label={`Mover ${name} para baixo`}
              disabled={disabled || index === order.length - 1}
              onClick={() => move(index, 1)}
              className="nt-press inline-flex size-11 shrink-0 items-center justify-center rounded-full text-tinta hover:bg-linha cursor-pointer disabled:cursor-not-allowed disabled:opacity-40"
            >
              <ArrowDown aria-hidden="true" className="h-4 w-4" />
            </button>
          </li>
        );
      })}
    </ol>
  );
}

const TYPE_OPTIONS: { value: ChoreType; label: string }[] = [
  { value: 'ROTATIONAL', label: 'Rotativa' },
  { value: 'FIXED', label: 'Fixa' },
];

const DESCRIPTION_MAX = 120;

export function NovaTarefaPage() {
  const { user } = useSession();
  const houseId = user?.houseId ?? '';
  const navigate = useNavigate();
  const toast = useToast();
  const house = useHouseDetailQuery(houseId || undefined);
  const createChore = useCreateChoreMutation(houseId);

  const descriptionId = useId();
  const descriptionErrorId = useId();
  const assigneeId = useId();

  const [description, setDescription] = useState('');
  const [descriptionError, setDescriptionError] = useState<string | null>(null);
  const [choreType, setChoreType] = useState<ChoreType>('ROTATIONAL');
  const [frequency, setFrequency] = useState<ChoreFrequency>('ONCE');
  const [linkedToGroceries, setLinkedToGroceries] = useState(false);
  const [assignee, setAssignee] = useState<string | null>(null);
  const [order, setOrder] = useState<string[] | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const members = house.data?.members ?? [];
  const names = new Map(members.map((m) => [m.id, m.name]));
  const memberIds = members.map((m) => m.id);
  // Padrão da roda: a ordem dos moradores da casa (ordem de entrada).
  const rotation = order ?? memberIds;
  const selectedAssignee = assignee ?? user?.id ?? memberIds[0] ?? '';

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const trimmed = description.trim();
    if (!trimmed) {
      setDescriptionError('Diga qual é a tarefa.');
      return;
    }
    setDescriptionError(null);
    setSubmitError(null);
    try {
      await createChore.mutateAsync({
        description: trimmed,
        choreType,
        frequency,
        linkedToGroceries,
        ...(choreType === 'ROTATIONAL'
          ? { rotationOrder: rotation }
          : { assigneeId: selectedAssignee }),
      });
      toast.show({ message: 'Tarefa criada.', tone: 'feito' });
      navigate(paths.tarefas);
    } catch (error) {
      setSubmitError(
        error instanceof ApiError ? error.message : translateApiError('unknown_error'),
      );
    }
  };

  return (
    <section className="mx-auto flex w-full max-w-xl flex-col gap-6">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-extrabold tracking-tight text-tinta">Nova tarefa</h1>
        <p className="text-texto-suave">Crie uma tarefa e a frequência dela.</p>
      </header>

      <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-6">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={descriptionId} className="text-sm font-semibold text-tinta">
            Descrição
          </label>
          <input
            id={descriptionId}
            type="text"
            value={description}
            maxLength={DESCRIPTION_MAX}
            placeholder="Ex.: Lavar a louça"
            aria-invalid={descriptionError ? true : undefined}
            aria-describedby={descriptionError ? descriptionErrorId : undefined}
            onChange={(event) => setDescription(event.target.value)}
            className="min-h-11 rounded-xl border border-linha bg-fundo px-3 text-base text-tinta focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/25"
          />
          {descriptionError && (
            <p id={descriptionErrorId} className="text-[13px] font-semibold text-atrasado-texto">
              {descriptionError}
            </p>
          )}
        </div>

        <div className="flex flex-col gap-1.5">
          <span className="text-sm font-semibold text-tinta">Tipo</span>
          <Segmented
            aria-label="Tipo da tarefa"
            options={TYPE_OPTIONS}
            value={choreType}
            onChange={setChoreType}
          />
          <p className="text-[13px] text-legenda">
            {choreType === 'ROTATIONAL'
              ? 'Gira entre os moradores a cada ciclo, na ordem da roda.'
              : 'Fica sempre com a mesma pessoa.'}
          </p>
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1.5 text-sm font-semibold text-tinta">Frequência</legend>
          <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
            {CHORE_FREQUENCIES.map((value) => (
              <label
                key={value}
                className={[
                  'flex min-h-11 cursor-pointer items-center gap-3 rounded-[18px] border-2 px-4 py-2 text-[15px] font-semibold transition-colors',
                  'has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-tomate',
                  frequency === value
                    ? 'border-tinta bg-superficie text-tinta'
                    : 'border-linha text-tinta hover:bg-superficie',
                ].join(' ')}
              >
                <input
                  type="radio"
                  name="frequency"
                  value={value}
                  checked={frequency === value}
                  onChange={() => setFrequency(value)}
                  className="size-4 accent-tomate"
                />
                {CHORE_FREQUENCY_LABELS[value]}
              </label>
            ))}
          </div>
        </fieldset>

        <Switch
          label="Ligada à lista do mercado"
          description="Mostra o atalho para a lista do mercado no cartão da tarefa."
          checked={linkedToGroceries}
          onCheckedChange={setLinkedToGroceries}
        />

        {choreType === 'FIXED' ? (
          <div className="flex flex-col gap-1.5">
            <label htmlFor={assigneeId} className="text-sm font-semibold text-tinta">
              Responsável
            </label>
            <select
              id={assigneeId}
              value={selectedAssignee}
              onChange={(event) => setAssignee(event.target.value)}
              disabled={members.length === 0}
              className="min-h-11 rounded-xl border border-linha bg-fundo px-3 text-base text-tinta focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/25"
            >
              {members.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <div className="flex flex-col gap-2">
            <span className="text-sm font-semibold text-tinta">Ordem da roda</span>
            <p className="text-[13px] text-legenda">
              Quem está em 1º começa. Use as setas para mudar a ordem.
            </p>
            {house.isPending ? (
              <p role="status" className="text-[15px] text-texto-suave">
                Carregando moradores…
              </p>
            ) : house.isError ? (
              <Alert
                tone="atrasado"
                title="Não foi possível carregar os moradores."
                action={{ label: 'Tentar de novo', onClick: () => void house.refetch() }}
              />
            ) : (
              <RotationOrderEditor order={rotation} onChange={setOrder} names={names} />
            )}
          </div>
        )}

        {submitError && <Alert tone="atrasado" title={submitError} />}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button asChild variant="secondary">
            <Link to={paths.tarefas}>Cancelar</Link>
          </Button>
          <Button
            type="submit"
            loading={createChore.isPending}
            disabled={house.isPending || members.length === 0}
          >
            Criar tarefa
          </Button>
        </div>
      </form>
    </section>
  );
}
