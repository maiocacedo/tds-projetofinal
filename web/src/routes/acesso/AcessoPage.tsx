export interface AcessoPageProps {
  /** `/entrar` usa `entrar`; `/cadastro` usa `cadastro`. */
  mode?: 'entrar' | 'cadastro';
}

/** Placeholder da F1: a tela real chega na F4 mantendo este export nomeado. */
export function AcessoPage({ mode = 'entrar' }: AcessoPageProps) {
  return (
    <main className="min-h-screen bg-fundo px-6 py-12 text-tinta">
      <section className="mx-auto max-w-md space-y-2">
        <h1 className="text-2xl font-extrabold tracking-tight text-tinta">
          {mode === 'cadastro' ? 'Criar conta' : 'Entrar'}
        </h1>
        <p className="text-texto-suave">Acesse o Nosso Teto com seu e-mail e senha.</p>
      </section>
    </main>
  );
}
