import { Clock, Inbox } from 'lucide-react';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  describeError,
  formatDeadline,
  formatPoints,
  formatRemaining,
  formatResidence,
  myVoteText,
  parseUtcLocalDateTime,
  useHouseMatchesQuery,
  useManagedHouseQuery,
  useNow,
  useRetractReservationMutation,
  useVoteMatchMutation,
  type HouseMatch,
  type Vote,
} from '../../api/houseManagement';
import { useSession } from '../../auth/SessionContext';
import { Button, CompatibilityRing, Dialog, EmptyState, StatusChip, useToast } from '../../components';
import { paths } from '../paths';
import { VoteToggle, VotersLine } from './CasaPage';

interface CandidateCardProps {
  match: HouseMatch;
  now: number;
  viewerIsAdmin: boolean;
  busy: boolean;
  onVote: (match: HouseMatch, vote: Vote) => void;
  onRetract: (match: HouseMatch) => void;
}

function CandidateCard({ match, now, viewerIsAdmin, busy, onVote, onRetract }: CandidateCardProps) {
  const { candidate } = match;
  const deadline = match.moveDeadline ? parseUtcLocalDateTime(match.moveDeadline) : null;
  const remaining = deadline ? formatRemaining(deadline, now) : null;

  return (
    <li
      aria-label={candidate.name}
      className="flex flex-col gap-4 rounded-[22px] border border-linha bg-fundo p-4"
    >
      <div className="flex items-center gap-4">
        <CompatibilityRing score={candidate.compatibilityScore} />
        <div className="flex min-w-0 flex-col gap-0.5">
          <h3 className="text-lg font-extrabold text-tinta">{candidate.name}</h3>
          <p className="text-sm text-texto-suave">
            {formatPoints(candidate.points)} · {formatResidence(candidate.residenceMonths)}
          </p>
        </div>
      </div>

      {match.status === 'PENDING' && (
        <div className="flex flex-col gap-2">
          <VotersLine voters={match.voters} />
          <p className="text-sm text-texto-suave">{myVoteText(match.myVote)}</p>
          <VoteToggle
            label={`Seu voto sobre ${candidate.name}`}
            value={match.myVote}
            disabled={busy}
            onVote={(vote) => onVote(match, vote)}
          />
        </div>
      )}

      {match.status === 'AWAITING_MOVE' && (
        <div className="flex flex-col gap-3 rounded-[16px] bg-vence-logo-fundo p-3">
          <div className="flex items-start gap-2 text-vence-logo-texto">
            <Clock aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
            <div className="flex flex-col gap-0.5">
              <p className="text-[15px] font-bold">
                {remaining
                  ? `Vaga reservada: faltam ${remaining} para a mudança`
                  : 'O prazo de 72 horas terminou'}
              </p>
              {deadline && (
                <p className="text-[13px]">
                  {candidate.name} mora em outra casa e tem até {formatDeadline(deadline)} para
                  confirmar.
                </p>
              )}
            </div>
          </div>
          {viewerIsAdmin && (
            <div>
              <Button size="sm" variant="secondary" disabled={busy} onClick={() => onRetract(match)}>
                Retirar reserva
              </Button>
            </div>
          )}
        </div>
      )}
    </li>
  );
}

export function CandidatosPage() {
  const { user } = useSession();
  const houseId = user?.houseId ?? '';
  const toast = useToast();
  const now = useNow();
  const houseQuery = useManagedHouseQuery(houseId);
  const matchesQuery = useHouseMatchesQuery(houseId);
  const vote = useVoteMatchMutation(houseId);
  const retract = useRetractReservationMutation(houseId);
  const [retractTarget, setRetractTarget] = useState<HouseMatch | null>(null);
  const [retractError, setRetractError] = useState<string | null>(null);

  if (!user) return null;

  const members = houseQuery.data?.members ?? [];
  const viewerIsAdmin = houseQuery.data
    ? members.find((member) => member.id === user.id)?.isHouseAdmin === true
    : user.isHouseAdmin === true;
  const matches = matchesQuery.data ?? [];
  const pending = matches.filter((match) => match.status === 'PENDING');
  const awaiting = matches.filter((match) => match.status === 'AWAITING_MOVE');

  const handleVote = (match: HouseMatch, choice: Vote) => {
    vote.mutate(
      { requestId: match.id, vote: choice },
      {
        onSuccess: () => toast.show({ message: 'Voto registrado.', tone: 'feito' }),
        onError: (error) => toast.show({ message: describeError(error), tone: 'atrasado' }),
      },
    );
  };

  const confirmRetract = async () => {
    if (!retractTarget) return;
    setRetractError(null);
    try {
      await retract.mutateAsync(retractTarget.id);
    } catch (error) {
      setRetractError(describeError(error));
      throw error;
    }
    toast.show({ message: `Reserva de ${retractTarget.candidate.name} retirada.` });
  };

  const renderList = (list: HouseMatch[]) => (
    <ul className="flex flex-col gap-3">
      {list.map((match) => (
        <CandidateCard
          key={match.id}
          match={match}
          now={now}
          viewerIsAdmin={viewerIsAdmin}
          busy={vote.isPending || retract.isPending}
          onVote={handleVote}
          onRetract={(target) => {
            setRetractError(null);
            setRetractTarget(target);
          }}
        />
      ))}
    </ul>
  );

  return (
    <section className="flex flex-col gap-8">
      <header className="flex flex-col gap-2">
        <p className="text-sm font-bold uppercase tracking-wide text-tomate">Casa</p>
        <h1 className="text-3xl font-extrabold tracking-tight text-tinta">Candidatos</h1>
        <p className="text-texto-suave">
          Quem pediu vaga na casa. Quem já votou aparece; o voto de cada um, não.
        </p>
      </header>

      {matchesQuery.isPending ? (
        <p role="status" className="text-texto-suave">
          Carregando candidatos…
        </p>
      ) : matchesQuery.isError ? (
        <p role="alert" className="text-atrasado-texto">
          {describeError(matchesQuery.error)}
        </p>
      ) : pending.length === 0 && awaiting.length === 0 ? (
        <EmptyState
          icon={<Inbox />}
          title="Nenhum pedido de vaga agora"
          description="Quando alguém pedir vaga, a casa vota aqui."
          action={
            <Button asChild variant="secondary">
              <Link to={paths.casa}>Voltar para a casa</Link>
            </Button>
          }
        />
      ) : (
        <>
          {pending.length > 0 && (
            <section aria-labelledby="em-votacao" className="flex flex-col gap-3">
              <div className="flex items-center gap-2">
                <h2 id="em-votacao" className="text-xl font-extrabold text-tinta">
                  Em votação
                </h2>
                <StatusChip tone="neutro">{pending.length}</StatusChip>
              </div>
              {renderList(pending)}
            </section>
          )}
          {awaiting.length > 0 && (
            <section aria-labelledby="aguardando-mudanca" className="flex flex-col gap-3">
              <h2 id="aguardando-mudanca" className="text-xl font-extrabold text-tinta">
                Aguardando mudança
              </h2>
              {renderList(awaiting)}
            </section>
          )}
        </>
      )}

      <Dialog
        open={retractTarget !== null}
        onOpenChange={(open) => {
          if (!open) setRetractTarget(null);
        }}
        title={retractTarget ? `Retirar a reserva de ${retractTarget.candidate.name}?` : 'Retirar reserva'}
        description="A vaga volta para a casa e a pessoa é avisada de que a reserva foi retirada."
        confirmLabel="Retirar reserva"
        tone="danger"
        loading={retract.isPending}
        onConfirm={confirmRetract}
      >
        {retractError && (
          <p role="alert" className="text-sm font-semibold text-atrasado-texto">
            {retractError}
          </p>
        )}
      </Dialog>
    </section>
  );
}
