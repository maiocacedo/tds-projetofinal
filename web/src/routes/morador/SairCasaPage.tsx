import { useId, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  describeError,
  needsHeir,
  useLeaveHouseMutation,
  useManagedHouseQuery,
  type ManagedMember,
} from '../../api/houseManagement';
import { useSession } from '../../auth/SessionContext';
import { Alert, Button, Dialog, useToast } from '../../components';
import { paths } from '../paths';

export interface HeirSelectProps {
  candidates: ManagedMember[];
  value: string;
  onChange: (id: string) => void;
  /** Texto de apoio abaixo do seletor. */
  hint?: string;
}

/** Seletor obrigatório de quem assume a administração da casa. */
export function HeirSelect({ candidates, value, onChange, hint }: HeirSelectProps) {
  const selectId = useId();
  const hintId = `${selectId}-hint`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={selectId} className="text-sm font-semibold text-tinta">
        Quem assume como admin
      </label>
      <select
        id={selectId}
        required
        value={value}
        aria-describedby={hint ? hintId : undefined}
        onChange={(event) => onChange(event.target.value)}
        className="min-h-11 rounded-[14px] border border-linha bg-fundo px-3 text-[15px] text-tinta focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/25"
      >
        <option value="">Escolha um morador</option>
        {candidates.map((member) => (
          <option key={member.id} value={member.id}>
            {member.name}
          </option>
        ))}
      </select>
      {hint && (
        <p id={hintId} className="text-[13px] text-legenda">
          {hint}
        </p>
      )}
    </div>
  );
}

export function SairCasaPage() {
  const { user } = useSession();
  const houseId = user?.houseId ?? '';
  const navigate = useNavigate();
  const toast = useToast();
  const houseQuery = useManagedHouseQuery(houseId);
  const leave = useLeaveHouseMutation(houseId);
  const [heirId, setHeirId] = useState('');
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!user) return null;

  const house = houseQuery.data;
  const members = house?.members ?? [];
  const others = members.filter((member) => member.id !== user.id);
  const heirRequired = needsHeir(user, members);
  const houseName = house?.name ?? user.houseName ?? 'a casa';
  const canConfirm = !houseQuery.isPending && (!heirRequired || heirId !== '');

  const handleLeave = async () => {
    setError(null);
    try {
      await leave.mutateAsync(heirRequired ? heirId : undefined);
    } catch (err) {
      setError(describeError(err));
      throw err;
    }
    toast.show({ message: `Você saiu de ${houseName}.`, tone: 'feito' });
    navigate(paths.explorar, { replace: true });
  };

  return (
    <section className="mx-auto flex max-w-xl flex-col gap-6">
      <header className="flex flex-col gap-2">
        <p className="text-sm font-bold uppercase tracking-wide text-tomate">Casa</p>
        <h1 className="text-3xl font-extrabold tracking-tight text-tinta">Sair da casa</h1>
        <p className="text-texto-suave">
          Você deixa de morar em {houseName}. Seus pontos e o seu tempo de moradia vão com você.
        </p>
      </header>

      <Alert
        tone="vence-logo"
        title="Contas em aberto continuam valendo"
        description="Se você tiver parcelas em aberto, elas continuam no resumo de contas da casa, com o seu nome, para quem fica. Combine o acerto com a casa."
      />

      <ul className="flex list-disc flex-col gap-1 pl-5 text-[15px] text-tinta">
        <li>Suas tarefas da roda passam para quem fica.</li>
        <li>Itens do mercado que você ia comprar voltam para a lista.</li>
        <li>Você volta a explorar casas e pode pedir vaga em outra.</li>
      </ul>

      {houseQuery.isError && (
        <p role="alert" className="text-atrasado-texto">
          {describeError(houseQuery.error)}
        </p>
      )}

      {heirRequired && (
        <div className="flex flex-col gap-3 rounded-[22px] border border-linha bg-superficie p-4">
          <p className="font-bold text-tinta">Você é o único admin da casa</p>
          <HeirSelect
            candidates={others}
            value={heirId}
            onChange={setHeirId}
            hint="Antes de sair, escolha quem passa a administrar a casa."
          />
        </div>
      )}

      {error && !confirmOpen && (
        <p role="alert" className="text-sm font-semibold text-atrasado-texto">
          {error}
        </p>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button asChild variant="secondary">
          <Link to={paths.casa}>Voltar</Link>
        </Button>
        <Button
          variant="danger"
          disabled={!canConfirm}
          onClick={() => {
            setError(null);
            setConfirmOpen(true);
          }}
        >
          Sair da casa
        </Button>
      </div>

      <Dialog
        open={confirmOpen}
        onOpenChange={setConfirmOpen}
        title={`Sair de ${houseName}?`}
        description="Contas em aberto continuam no resumo da casa. Para voltar, você precisará pedir vaga de novo."
        confirmLabel="Confirmar saída"
        tone="danger"
        loading={leave.isPending}
        onConfirm={handleLeave}
      >
        {heirRequired && heirId && (
          <p className="text-[15px] text-tinta">
            {others.find((member) => member.id === heirId)?.name} assume como admin.
          </p>
        )}
        {error && (
          <p role="alert" className="text-sm font-semibold text-atrasado-texto">
            {error}
          </p>
        )}
      </Dialog>
    </section>
  );
}
