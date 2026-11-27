import { Link } from 'react-router-dom';
import { paths } from '../paths';

/** Placeholder da F1: a landing real chega na F3 mantendo este export nomeado. */
export function LandingPage() {
  return (
    <main className="min-h-screen bg-fundo px-6 py-12 text-tinta">
      <div className="mx-auto max-w-2xl space-y-4">
        <h1 className="text-3xl font-extrabold tracking-tight text-tinta">Nosso Teto</h1>
        <p className="text-base text-texto-suave">
          Convivência organizada para quem divide a casa.
        </p>
        <nav aria-label="Acesso" className="flex gap-4 font-semibold">
          <Link to={paths.entrar}>Entrar</Link>
          <Link to={paths.cadastro}>Criar conta</Link>
          <Link to={paths.sobre}>Sobre</Link>
        </nav>
      </div>
    </main>
  );
}
