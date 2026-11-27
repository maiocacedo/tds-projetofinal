import { ArrowRight } from 'lucide-react';
import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  useAcknowledgeWelcomeMutation,
  useCancelOtherMatchRequestsMutation,
  useMyMatchRequestsQuery,
} from '../../api/candidate';
import { PERMISSIONS } from '../../api/me';
import { useSession } from '../../auth/SessionContext';
import { Alert, Button, Celebration } from '../../components';
import { paths } from '../paths';

export function BemVindoPage() {
  const { user, hasPermission } = useSession();
  const navigate = useNavigate();
  const [actionError, setActionError] = useState<string | null>(null);

  const canReadMatches = hasPermission(PERMISSIONS.MATCH);
  const { data: requests, isLoading } = useMyMatchRequestsQuery({
    enabled: canReadMatches,
  });

  const cancelOthersMutation = useCancelOtherMatchRequestsMutation();
  const ackWelcomeMutation = useAcknowledgeWelcomeMutation();

  const otherPending = useMemo(() => {
    if (!requests) return [];
    return requests.filter(
      (req) => req.status === 'PENDING' && req.houseId !== user?.houseId,
    );
  }, [requests, user?.houseId]);

  const busy = cancelOthersMutation.isPending || ackWelcomeMutation.isPending;

  const title = user?.houseName
    ? `Boas-vindas à ${user.houseName}!`
    : 'Boas-vindas à sua nova casa!';

  const handleCancelOthersAndEnter = async () => {
    setActionError(null);
    try {
      await cancelOthersMutation.mutateAsync();
      navigate(paths.inicio, { replace: true });
    } catch (err) {
      setActionError(
        err instanceof Error
          ? err.message
          : 'Não foi possível cancelar as outras solicitações agora.',
      );
    }
  };

  const handleAcknowledgeAndEnter = async () => {
    setActionError(null);
    try {
      await ackWelcomeMutation.mutateAsync();
      navigate(paths.inicio, { replace: true });
    } catch (err) {
      setActionError(
        err instanceof Error
          ? err.message
          : 'Não foi possível entrar na casa agora. Tente novamente.',
      );
    }
  };

  return (
    <section className="mx-auto max-w-xl py-4">
      <Celebration title={title} subtitle="Seu pedido foi aceito" titleAs="h1">
        <p className="max-w-[46ch] text-[16px] text-texto-suave">
          Agora você já faz parte da república. Contas, tarefas e mercado da casa
          estão prontos para você.
        </p>

        {actionError && (
          <Alert
            tone="atrasado"
            title="Algo deu errado"
            description={actionError}
            className="mt-2 w-full text-left"
          />
        )}

        {canReadMatches && isLoading ? (
          <p role="status" className="mt-2 text-sm font-semibold text-legenda">
            Verificando suas outras solicitações…
          </p>
        ) : otherPending.length > 0 ? (
          <div className="mt-3 w-full space-y-4 rounded-[24px] border border-linha bg-superficie/70 p-5 text-left">
            <div className="space-y-1">
              <p className="text-[15px] font-bold text-tinta">
                Você ainda tem{' '}
                {otherPending.length === 1
                  ? '1 outra solicitação aberta'
                  : `${otherPending.length} outras solicitações abertas`}
              </p>
              <p className="text-sm text-texto-suave">
                Casas pendentes:{' '}
                <strong className="font-bold text-tinta">
                  {otherPending.map((req) => req.houseName).join(', ')}
                </strong>
                . O que prefere fazer com elas?
              </p>
            </div>

            <div className="flex flex-col gap-2.5">
              <Button
                variant="primary"
                size="lg"
                loading={cancelOthersMutation.isPending}
                disabled={busy}
                onClick={() => void handleCancelOthersAndEnter()}
                className="w-full"
              >
                Cancelar outras solicitações e ir para a casa
              </Button>
              <Button
                variant="secondary"
                size="md"
                loading={ackWelcomeMutation.isPending}
                disabled={busy}
                onClick={() => void handleAcknowledgeAndEnter()}
                className="w-full"
              >
                Manter outras abertas e ir para a casa
              </Button>
            </div>
          </div>
        ) : (
          <div className="mt-3 flex w-full justify-center">
            <Button
              variant="primary"
              size="lg"
              loading={ackWelcomeMutation.isPending}
              disabled={busy}
              onClick={() => void handleAcknowledgeAndEnter()}
            >
              Ir para a casa
              <ArrowRight aria-hidden="true" className="size-4" />
            </Button>
          </div>
        )}
      </Celebration>
    </section>
  );
}
