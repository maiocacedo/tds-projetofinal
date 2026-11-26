import { Lock } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useSession } from '../auth/SessionContext';
import { getHomePathForUser, paths } from './paths';

/** Rota aberta pela URL sem a permissão correspondente (sem redirect). */
export function NoAccessPage() {
  const { user } = useSession();
  const home = user ? getHomePathForUser(user) : paths.landing;

  return (
    <section className="mx-auto flex max-w-md flex-col items-center gap-4 py-16 text-center">
      <Lock aria-hidden="true" className="size-10 text-texto-suave" />
      <h1 className="text-2xl font-extrabold tracking-tight text-tinta">Sem acesso</h1>
      <p className="text-texto-suave">
        Sua conta não tem permissão para abrir esta página. Se acha que é um engano,
        fale com quem administra o Nosso Teto.
      </p>
      <Link to={home} className="font-semibold text-tinta underline underline-offset-4">
        Voltar para o início
      </Link>
    </section>
  );
}
