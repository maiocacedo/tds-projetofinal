import { CheckCircle2, Clock, Hourglass, Inbox, Undo2, XCircle, type LucideIcon } from 'lucide-react';
import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  describeError,
  formatDeadline,
  formatRemaining,
  needsHeir,
  parseUtcLocalDateTime,
  useConfirmMoveMutation,
  useDeclineMoveMutation,
  useManagedHouseQuery,
  useMyMoveRequestQuery,
  useNow,
  type MatchStatus,
  type MyMatchRequest,
} from '../../api/houseManagement';
import { useSession } from '../../auth/SessionContext';
import { Alert, Button, Dialog, useToast } from '../../components';
import { paths } from '../paths';
import { HeirSelect } from './SairCasaPage';

interface TerminalCopy {
  icon: LucideIcon;
  title: string;
  description: (houseName: string) => string;
  cta: { label: string; to: string };
}

function terminalCopy(status: MatchStatus, hasHouse: boolean): TerminalCopy | null {
  const backHome = hasHouse
    ? { label: 'Voltar para o Início', to: paths.inicio }
    : { label: 'Explorar casas', to: paths.explorar };
  switch (status) {
    case 'ACCEPTED':
      return {
        icon: CheckCircle2,
        title: 'Mudança concluída',
        description: (house) =>
          `Você agora mora em ${house}. Seus pontos e o seu tempo de moradia vieram junto.`,
        cta: { label: 'Ir para o Início', to: paths.inicio },
      };
    case 'WITHDRAWN':
      return {
        icon: Undo2,
        title: 'Você recusou a mudança',
        description: (house) =>
          `A vaga em ${house} voltou para a casa. Você continua onde está.`,
        cta: backHome,
      };
    case 'EXPIRED':
      return {
        icon: Hourglass,
        title: 'O prazo de 72 horas expirou',
        description: (house) =>
          `A reserva em ${house} venceu sem confirmação e a vaga voltou para a casa. Você pode pedir vaga de novo.`,
        cta: { label: 'Explorar casas', to: paths.explorar },
      };
    case 'CANCELLED':
      return {
        icon: XCircle,
        title: 'A reserva foi retirada pela casa',
        description: (house) =>
          `Um admin de ${house} retirou a reserva antes da mudança. Você continua onde está.`,
        cta: { label: 'Explorar outras casas', to: paths.explorar },
      };
    case 'REJECTED':
      return {
        icon: XCircle,
        title: 'A casa não aprovou o pedido',
        description: (house) => `${house} votou e não aprovou a sua entrada desta vez.`,
        cta: { label: 'Explorar outras casas', to: paths.explorar },
      };
    case 'PENDING':
      return {
        icon: Inbox,
        title: 'Pedido ainda em votação',
        description: (house) =>
          `${house} ainda está votando. Quando aprovarem, a vaga fica reservada por 72 horas.`,
        cta: { label: 'Ver minhas solicitações', to: paths.solicitacoes },
      };
    default:
      return null;
  }
}

function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <header className="flex flex-col gap-2">
      <p className="text-sm font-bold uppercase tracking-wide text-tomate">Mudança de casa</p>
      <h1 className="text-3xl font-extrabold tracking-tight text-tinta">{title}</h1>
      {children}
    </header>
  );
}

function TerminalState({ copy, houseName }: { copy: TerminalCopy; houseName: string }) {
  const Icon = copy.icon;
  return (
    <section className="mx-auto flex max-w-xl flex-col items-center gap-5 py-10 text-center">
      <span
        aria-hidden="true"
        className="flex size-16 items-center justify-center rounded-full bg-superficie text-tomate"
      >
        <Icon className="size-8" />
      </span>
      <h1 className="text-3xl font-extrabold tracking-tight text-tinta">{copy.title}</h1>
      <p className="max-w-[46ch] text-texto-suave">{copy.description(houseName)}</p>
      <Button asChild>
        <Link to={copy.cta.to}>{copy.cta.label}</Link>
      </Button>
    </section>
  );
}

function AwaitingMove({ request, deadline }: { request: MyMatchRequest; deadline: Date | null }) {
  const { user } = useSession();
  const navigate = useNavigate();
  const toast = useToast();
  const now = useNow();
  const currentHouse = useManagedHouseQuery(user?.houseId);
  const confirm = useConfirmMoveMutation();
  const decline = useDeclineMoveMutation();
  const [heirId, setHeirId] = useState('');
  const [declineOpen, setDeclineOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [declineError, setDeclineError] = useState<string | null>(null);

  const houseName = request.houseName ?? 'a nova casa';
  const members = currentHouse.data?.members ?? [];
  const others = members.filter((member) => member.id !== user?.id);
  const heirRequired = needsHeir(user, members);
  const remaining = deadline ? formatRemaining(deadline, now) : null;
  const loadingHouse = Boolean(user?.houseId) && currentHouse.isPending;
  const canConfirm = !loadingHouse && (!heirRequired || heirId !== '');

  const handleConfirm = () => {
    setError(null);
    confirm.mutate(
      { requestId: request.id, newAdminId: heirRequired ? heirId : undefined },
      {
        onSuccess: () => {
          toast.show({ message: `Boas-vindas a ${houseName}!`, tone: 'feito' });
          navigate(paths.inicio, { replace: true });
        },
        onError: (err) => setError(describeError(err)),
      },
    );
  };

  const handleDecline = async () => {
    setDeclineError(null);
    try {
      await decline.mutateAsync(request.id);
    } catch (err) {
      setDeclineError(describeError(err));
      throw err;
    }
  };

  return (
    <section className="mx-auto flex max-w-xl flex-col gap-6">
      <PageHeader title={`Sua vaga em ${houseName} está reservada`}>
        <p className="text-texto-suave">
          A casa aprovou o seu pedido. Confirme a mudança dentro do prazo ou recuse para liberar
          a vaga.
        </p>
      </PageHeader>

      <div
        role="timer"
        aria-label="Tempo para confirmar a mudança"
        className="flex items-center gap-4 rounded-[22px] bg-vence-logo-fundo p-5 text-vence-logo-texto"
      >
        <Clock aria-hidden="true" className="size-8 shrink-0" />
        <div className="flex flex-col gap-0.5">
          <p className="text-2xl font-extrabold">
            {remaining ? `Faltam ${remaining}` : 'Prazo encerrado'}
          </p>
          {deadline && (
            <p className="text-sm">Prazo: {formatDeadline(deadline)} (horário de Brasília)</p>
          )}
        </div>
      </div>

      <ul className="flex list-disc flex-col gap-1 pl-5 text-[15px] text-tinta">
        <li>Você sai da casa atual e entra em {houseName} numa etapa só.</li>
        <li>Contas em aberto na casa atual continuam no resumo de lá, com o seu nome.</li>
        <li>Seus pontos e o seu tempo de moradia vão com você.</li>
      </ul>

      {heirRequired && (
        <div className="flex flex-col gap-3 rounded-[22px] border border-linha bg-superficie p-4">
          <p className="font-bold text-tinta">Você é o único admin da casa atual</p>
          <HeirSelect
            candidates={others}
            value={heirId}
            onChange={setHeirId}
            hint="Escolha quem passa a administrar a casa atual antes de se mudar."
          />
        </div>
      )}

      {error && (
        <p role="alert" className="text-sm font-semibold text-atrasado-texto">
          {error}
        </p>
      )}

      <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
        <Button
          variant="secondary"
          disabled={confirm.isPending}
          onClick={() => {
            setError(null);
            setDeclineError(null);
            setDeclineOpen(true);
          }}
        >
          Recusar mudança
        </Button>
        <Button
          loading={confirm.isPending}
          disabled={!canConfirm || !remaining}
          onClick={handleConfirm}
        >
          Confirmar mudança
        </Button>
      </div>

      <Dialog
        open={declineOpen}
        onOpenChange={(open) => {
          setDeclineOpen(open);
          if (!open) setDeclineError(null);
        }}
        title={`Recusar a vaga em ${houseName}?`}
        description="A vaga volta para a casa e você continua onde está. Para tentar de novo, será preciso um novo pedido."
        confirmLabel="Recusar mudança"
        tone="danger"
        loading={decline.isPending}
        onConfirm={handleDecline}
      >
        {declineError && <Alert tone="atrasado" title={declineError} />}
      </Dialog>
    </section>
  );
}

export function MudancaPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const { user } = useSession();
  const query = useMyMoveRequestQuery(requestId);

  if (query.isPending) {
    return (
      <section className="flex flex-col gap-4">
        <PageHeader title="Mudança de casa" />
        <p role="status" className="text-texto-suave">
          Carregando a sua reserva…
        </p>
      </section>
    );
  }

  if (query.isError) {
    return (
      <section className="flex flex-col gap-4">
        <PageHeader title="Mudança de casa" />
        <p role="alert" className="text-atrasado-texto">
          {describeError(query.error)}
        </p>
      </section>
    );
  }

  const request = query.data;
  if (!request) {
    return (
      <TerminalState
        houseName=""
        copy={{
          icon: Inbox,
          title: 'Solicitação não encontrada',
          description: () => 'Não achamos esta solicitação entre os seus pedidos de vaga.',
          cta: { label: 'Ver minhas solicitações', to: paths.solicitacoes },
        }}
      />
    );
  }

  const houseName = request.houseName ?? 'a casa';
  const deadline = request.moveDeadline ? parseUtcLocalDateTime(request.moveDeadline) : null;

  if (request.status === 'AWAITING_MOVE') {
    return <AwaitingMove request={request} deadline={deadline} />;
  }

  const copy = terminalCopy(request.status, Boolean(user?.houseId));
  if (!copy) return null;
  return <TerminalState copy={copy} houseName={houseName} />;
}
