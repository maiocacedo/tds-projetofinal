import { LogOut, Settings2, ShieldCheck, Users, Vote as VoteIcon } from 'lucide-react';
import { useId, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  adminsOf,
  describeError,
  formatPoints,
  formatResidence,
  myVoteText,
  useCancelRemovalMutation,
  useManagedHouseQuery,
  useProposeRemovalMutation,
  useRemovalsQuery,
  useSetMemberAdminMutation,
  useVoteRemovalMutation,
  type ManagedMember,
  type Removal,
  type Vote,
} from '../../api/houseManagement';
import { PERMISSIONS, type UserMe } from '../../api/me';
import type { UserRef } from '../../api/types';
import { useSession } from '../../auth/SessionContext';
import { Alert, Avatar, Button, Dialog, EmptyState, StatusChip, useToast } from '../../components';
import { paths } from '../paths';

// ---------------------------------------------------------------------------
// Peças de votação compartilhadas com Candidatos
// ---------------------------------------------------------------------------

export interface VoteToggleProps {
  /** Rótulo do grupo para leitor de tela, ex.: "Seu voto sobre Marina Costa". */
  label: string;
  value: Vote | null;
  onVote: (vote: Vote) => void;
  disabled?: boolean;
}

/** Aprovar / Rejeitar com o voto atual marcado; votar de novo troca o voto. */
export function VoteToggle({ label, value, onVote, disabled = false }: VoteToggleProps) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-2">
      <Button
        size="sm"
        variant={value === 'APPROVE' ? 'primary' : 'secondary'}
        aria-pressed={value === 'APPROVE'}
        disabled={disabled}
        onClick={() => value !== 'APPROVE' && onVote('APPROVE')}
      >
        Aprovar
      </Button>
      <Button
        size="sm"
        variant={value === 'REJECT' ? 'danger' : 'secondary'}
        aria-pressed={value === 'REJECT'}
        disabled={disabled}
        onClick={() => value !== 'REJECT' && onVote('REJECT')}
      >
        Rejeitar
      </Button>
    </div>
  );
}

/** Quem já votou, sem a direção do voto. */
export function VotersLine({ voters }: { voters: UserRef[] }) {
  if (voters.length === 0) {
    return <p className="text-sm text-texto-suave">Ninguém votou ainda.</p>;
  }
  return (
    <p className="text-sm text-texto-suave">
      <span className="font-semibold text-tinta">Já votaram:</span>{' '}
      {voters.map((voter) => voter.name).join(', ')}
    </p>
  );
}

// ---------------------------------------------------------------------------
// Moradores
// ---------------------------------------------------------------------------

interface MemberRowProps {
  member: ManagedMember;
  me: UserMe;
  canManage: boolean;
  adminCount: number;
  hasOpenRemoval: boolean;
  busy: boolean;
  onToggleAdmin: (member: ManagedMember) => void;
  onProposeRemoval: (member: ManagedMember) => void;
}

function MemberRow({
  member,
  me,
  canManage,
  adminCount,
  hasOpenRemoval,
  busy,
  onToggleAdmin,
  onProposeRemoval,
}: MemberRowProps) {
  const isMe = member.id === me.id;
  const isAdmin = member.isHouseAdmin === true;
  const isLastAdmin = isAdmin && adminCount <= 1;
  const points = isMe ? me.points.total : member.points;
  const residence = isMe ? me.residenceMonths : member.residenceMonths;
  const hintId = useId();

  return (
    <li
      aria-label={member.name}
      className="flex flex-col gap-3 rounded-[22px] border border-linha bg-fundo p-4 sm:flex-row sm:items-center"
    >
      <div className="flex min-w-0 flex-1 items-center gap-3">
        <Avatar name={member.name} />
        <div className="flex min-w-0 flex-col gap-0.5">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-bold text-tinta">
              {member.name}
              {isMe && <span className="font-semibold text-texto-suave"> (você)</span>}
            </span>
            {isAdmin && (
              <StatusChip tone="pontos">
                <ShieldCheck aria-hidden="true" className="size-3.5" />
                Admin
              </StatusChip>
            )}
            {hasOpenRemoval && <StatusChip tone="vence-logo">Saída em votação</StatusChip>}
          </div>
          {(points !== undefined || residence !== undefined) && (
            <span className="text-sm text-texto-suave">
              {points !== undefined && points !== null ? `${formatPoints(points)} · ` : ''}
              {formatResidence(residence)}
            </span>
          )}
        </div>
      </div>

      {canManage && (
        <div className="flex flex-col gap-1 sm:items-end">
          <div className="flex flex-wrap gap-2">
            <Button
              size="sm"
              variant="secondary"
              disabled={busy || isLastAdmin}
              aria-describedby={isLastAdmin ? hintId : undefined}
              onClick={() => onToggleAdmin(member)}
            >
              {isAdmin ? 'Remover admin' : 'Tornar admin'}
            </Button>
            {!isMe && !hasOpenRemoval && (
              <Button size="sm" variant="ghost" onClick={() => onProposeRemoval(member)}>
                Propor saída
              </Button>
            )}
          </div>
          {isLastAdmin && (
            <p id={hintId} className="text-[13px] text-legenda">
              A casa precisa de pelo menos um admin.
            </p>
          )}
        </div>
      )}
    </li>
  );
}

// ---------------------------------------------------------------------------
// Remoções
// ---------------------------------------------------------------------------

interface RemovalCardProps {
  removal: Removal;
  me: UserMe;
  members: ManagedMember[];
  busy: boolean;
  onVote: (removal: Removal, vote: Vote) => void;
  onCancel: (removal: Removal) => void;
}

function RemovalCard({ removal, me, members, busy, onVote, onCancel }: RemovalCardProps) {
  const nameOf = (id: string | null | undefined) =>
    members.find((member) => member.id === id)?.name;
  const targetName = removal.targetUserName || nameOf(removal.targetUserId) || 'Morador';
  const proposerName = removal.proposedByName || nameOf(removal.proposedById);
  const isTarget = removal.targetUserId === me.id;
  const isProposer = removal.proposedById === me.id;

  return (
    <li
      aria-label={`Saída de ${targetName}`}
      className="flex flex-col gap-3 rounded-[22px] border border-linha bg-fundo p-4"
    >
      <div className="flex flex-col gap-1">
        <p className="font-bold text-tinta">
          {isTarget ? 'Sua saída está em votação' : `Saída de ${targetName}`}
        </p>
        {proposerName && (
          <p className="text-sm text-texto-suave">
            Proposta por {removal.proposedById === me.id ? 'você' : proposerName}
          </p>
        )}
        {removal.reason && (
          <p className="text-[15px] text-tinta">
            <span className="font-semibold">Motivo:</span> {removal.reason}
          </p>
        )}
      </div>

      <div className="flex flex-col gap-0.5">
        <p className="text-sm font-medium text-texto-suave">
          {removal.votesCast} de {removal.votersTotal}{' '}
          {removal.votersTotal === 1 ? 'voto' : 'votos'}
        </p>
        <VotersLine voters={removal.voters} />
      </div>

      {isTarget ? (
        <p className="rounded-[14px] bg-superficie px-3 py-2 text-sm text-texto-suave">
          Você não vota na sua própria saída. A casa decide pela regra de votação dela.
        </p>
      ) : (
        <div className="flex flex-col gap-2">
          <p className="text-sm text-texto-suave">{myVoteText(removal.myVote)}</p>
          <VoteToggle
            label={`Seu voto sobre a saída de ${targetName}`}
            value={removal.myVote}
            disabled={busy}
            onVote={(vote) => onVote(removal, vote)}
          />
        </div>
      )}

      {isProposer && (
        <div>
          <Button size="sm" variant="ghost" disabled={busy} onClick={() => onCancel(removal)}>
            Cancelar proposta
          </Button>
        </div>
      )}
    </li>
  );
}

// ---------------------------------------------------------------------------
// Página
// ---------------------------------------------------------------------------

export function CasaPage() {
  const { user, hasPermission } = useSession();
  const houseId = user?.houseId ?? '';
  const toast = useToast();
  const houseQuery = useManagedHouseQuery(houseId);
  const removalsQuery = useRemovalsQuery(houseId);
  const setAdmin = useSetMemberAdminMutation(houseId);
  const propose = useProposeRemovalMutation(houseId);
  const voteRemoval = useVoteRemovalMutation(houseId);
  const cancelRemoval = useCancelRemovalMutation(houseId);

  const [proposeTarget, setProposeTarget] = useState<ManagedMember | null>(null);
  const [reason, setReason] = useState('');
  const [proposeError, setProposeError] = useState<string | null>(null);
  const reasonId = useId();

  if (!user) return null;

  const house = houseQuery.data;
  const members = house?.members ?? [];
  const removals = removalsQuery.data ?? [];
  const viewerIsAdmin =
    members.find((member) => member.id === user.id)?.isHouseAdmin === true ||
    (members.length === 0 && user.isHouseAdmin === true);
  const canManageHouse = viewerIsAdmin && hasPermission(PERMISSIONS.MANAGE_HOUSE);
  const adminCount = adminsOf(members);
  const openRemovalFor = new Set(removals.map((removal) => removal.targetUserId));

  const failToast = (error: unknown) =>
    toast.show({ message: describeError(error), tone: 'atrasado' });

  const handleToggleAdmin = (member: ManagedMember) => {
    const isAdmin = member.isHouseAdmin !== true;
    setAdmin.mutate(
      { memberId: member.id, isAdmin },
      {
        onSuccess: () =>
          toast.show({
            message: isAdmin
              ? `${member.name} agora é admin da casa.`
              : `${member.name} deixou de ser admin.`,
            tone: 'feito',
          }),
        onError: failToast,
      },
    );
  };

  const openPropose = (member: ManagedMember) => {
    setReason('');
    setProposeError(null);
    setProposeTarget(member);
  };

  const confirmPropose = async () => {
    if (!proposeTarget) return;
    setProposeError(null);
    try {
      await propose.mutateAsync({ memberId: proposeTarget.id, reason });
    } catch (error) {
      setProposeError(describeError(error));
      throw error;
    }
    toast.show({ message: `Saída de ${proposeTarget.name} aberta para votação.`, tone: 'feito' });
  };

  const handleVote = (removal: Removal, vote: Vote) => {
    voteRemoval.mutate(
      { removalId: removal.id, vote },
      {
        onSuccess: () => toast.show({ message: 'Voto registrado.', tone: 'feito' }),
        onError: failToast,
      },
    );
  };

  const handleCancel = (removal: Removal) => {
    cancelRemoval.mutate(removal.id, {
      onSuccess: () => toast.show({ message: 'Proposta de saída cancelada.' }),
      onError: failToast,
    });
  };

  return (
    <section className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <p className="text-sm font-bold uppercase tracking-wide text-tomate">Casa</p>
        <h1 className="text-3xl font-extrabold tracking-tight text-tinta">
          {house?.name ?? user.houseName ?? 'Sua casa'}
        </h1>
        {house?.address && <p className="text-texto-suave">{house.address}</p>}
      </header>

      <nav aria-label="Atalhos da casa" className="flex flex-wrap gap-2">
        {canManageHouse && (
          <Button asChild variant="secondary" size="sm">
            <Link to={paths.configurarCasa}>
              <Settings2 aria-hidden="true" className="size-4" />
              Configurar casa
            </Link>
          </Button>
        )}
        {hasPermission(PERMISSIONS.MATCH) && (
          <Button asChild variant="secondary" size="sm">
            <Link to={paths.candidatos}>
              <VoteIcon aria-hidden="true" className="size-4" />
              Candidatos
            </Link>
          </Button>
        )}
        <Button asChild variant="ghost" size="sm">
          <Link to={paths.sairCasa}>
            <LogOut aria-hidden="true" className="size-4" />
            Sair da casa
          </Link>
        </Button>
      </nav>

      <section aria-labelledby="moradores-titulo" className="flex flex-col gap-3">
        <h2 id="moradores-titulo" className="text-xl font-extrabold text-tinta">
          Moradores
        </h2>
        {houseQuery.isPending ? (
          <p role="status" className="text-texto-suave">
            Carregando moradores…
          </p>
        ) : houseQuery.isError ? (
          <p role="alert" className="text-atrasado-texto">
            {describeError(houseQuery.error)}
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {members.map((member) => (
              <MemberRow
                key={member.id}
                member={member}
                me={user}
                canManage={canManageHouse}
                adminCount={adminCount}
                hasOpenRemoval={openRemovalFor.has(member.id)}
                busy={setAdmin.isPending}
                onToggleAdmin={handleToggleAdmin}
                onProposeRemoval={openPropose}
              />
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="remocoes-titulo" className="flex flex-col gap-3">
        <h2 id="remocoes-titulo" className="text-xl font-extrabold text-tinta">
          Remoções em votação
        </h2>
        {removalsQuery.isPending ? (
          <p role="status" className="text-texto-suave">
            Carregando votações…
          </p>
        ) : removalsQuery.isError ? (
          <Alert
            tone="atrasado"
            title="Não foi possível carregar as votações"
            description={describeError(removalsQuery.error)}
          />
        ) : removals.length === 0 ? (
          <EmptyState
            icon={<Users />}
            title="Nenhuma saída em votação"
            description="Quando um admin propõe a saída de alguém, a casa vota aqui. Quem votou aparece; o voto, não."
          />
        ) : (
          <ul className="flex flex-col gap-2">
            {removals.map((removal) => (
              <RemovalCard
                key={removal.id}
                removal={removal}
                me={user}
                members={members}
                busy={voteRemoval.isPending || cancelRemoval.isPending}
                onVote={handleVote}
                onCancel={handleCancel}
              />
            ))}
          </ul>
        )}
      </section>

      <Dialog
        open={proposeTarget !== null}
        onOpenChange={(open) => {
          if (!open) setProposeTarget(null);
        }}
        title={proposeTarget ? `Propor a saída de ${proposeTarget.name}?` : 'Propor saída'}
        description="A casa vota pela regra dela, sem o voto da pessoa. Contas em aberto continuam valendo."
        confirmLabel="Abrir votação"
        tone="danger"
        loading={propose.isPending}
        onConfirm={confirmPropose}
      >
        <div className="flex flex-col gap-1.5">
          <label htmlFor={reasonId} className="text-sm font-semibold text-tinta">
            Motivo (opcional)
          </label>
          <textarea
            id={reasonId}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            rows={3}
            maxLength={500}
            className="rounded-[14px] border border-linha bg-fundo px-3 py-2 text-[15px] text-tinta focus:border-tomate focus:outline-none focus:ring-2 focus:ring-tomate/25"
          />
        </div>
        {proposeError && (
          <p role="alert" className="text-sm font-semibold text-atrasado-texto">
            {proposeError}
          </p>
        )}
      </Dialog>
    </section>
  );
}
