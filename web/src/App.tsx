import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from './api/queryClient';

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <main className="min-h-screen bg-fundo px-6 py-12 text-tinta">
        <div className="mx-auto max-w-2xl space-y-4">
          <h1 className="text-3xl font-extrabold tracking-tight text-tinta">
            Nosso Teto
          </h1>
          <p className="text-base text-texto-suave">
            Convivência organizada para quem divide a casa.
          </p>
        </div>
      </main>
    </QueryClientProvider>
  );
}

export default App;
