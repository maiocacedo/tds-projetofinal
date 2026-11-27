import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { updateMyProfile, type UpdateProfilePayload } from '../../api/profile';
import { queryKeys } from '../../api/queryKeys';
import { ApiError } from '../../auth/apiClient';
import { useSession } from '../../auth/SessionContext';
import { useToast } from '../../components';
import { getHomePathForUser, paths } from '../paths';
import { FichaForm } from './FichaForm';

export function OnboardingPage() {
  const { user, refreshMe } = useSession();
  const queryClient = useQueryClient();
  const toast = useToast();
  const navigate = useNavigate();
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  if (!user) return null;

  const handleSubmit = async (payload: UpdateProfilePayload) => {
    setServerError(null);
    setSubmitting(true);
    try {
      await updateMyProfile(payload);
      const updatedMe = await refreshMe();
      await queryClient.invalidateQueries({ queryKey: queryKeys.houses });
      toast.show({
        message: 'Ficha salva! Agora você já pode explorar casas.',
        tone: 'feito',
      });
      navigate(updatedMe ? getHomePathForUser(updatedMe) : paths.explorar, {
        replace: true,
      });
    } catch (error) {
      setServerError(
        error instanceof ApiError
          ? error.message
          : 'Não foi possível salvar sua ficha agora. Tente novamente.',
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="mx-auto max-w-4xl space-y-6">
      <header className="space-y-2">
        <h1 className="text-[30px] font-extrabold leading-tight tracking-tight text-tinta">
          Sua ficha de morador
        </h1>
        <p className="max-w-[65ch] text-[16px] text-texto-suave">
          A primeira parte decide sua compatibilidade com cada casa. O resto
          ajuda a casa a votar.
        </p>
      </header>

      <FichaForm
        userName={user.name}
        residenceMonths={user.residenceMonths}
        pointsTotal={user.points.total}
        initialProfile={user.profile}
        submitLabel="Salvar e explorar casas"
        submitting={submitting}
        serverError={serverError}
        onSubmit={handleSubmit}
      />
    </section>
  );
}
