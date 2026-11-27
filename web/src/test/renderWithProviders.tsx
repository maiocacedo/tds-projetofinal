/* eslint-disable react-refresh/only-export-components -- helper de teste, sem HMR */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderResult } from '@testing-library/react';
import { StrictMode, type ReactElement, type ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { createQueryClient } from '../api/queryClient';
import { SessionProvider } from '../auth/SessionContext';
import { AppRoutes } from '../routes/router';

export interface RenderWithProvidersOptions {
  /** Rota inicial do MemoryRouter (padrão `/`). */
  route?: string;
  /** QueryClient próprio; por padrão um novo e limpo por teste. */
  queryClient?: QueryClient;
  /** Envolve em <StrictMode> (montagem dupla dos efeitos). */
  strict?: boolean;
}

export interface RenderWithProvidersResult extends RenderResult {
  queryClient: QueryClient;
}

function LocationDisplay() {
  const location = useLocation();
  return (
    <div data-testid="location" hidden>
      {location.pathname}
    </div>
  );
}

/**
 * Renderiza `ui` (ou a tabela completa de rotas, quando omitido) com
 * QueryClient limpo + MemoryRouter + SessionProvider.
 */
export function renderWithProviders(
  ui?: ReactElement,
  { route = '/', queryClient = createQueryClient(), strict = false }: RenderWithProvidersOptions = {},
): RenderWithProvidersResult {
  const tree: ReactNode = (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <SessionProvider>
          {ui ?? <AppRoutes />}
          <LocationDisplay />
        </SessionProvider>
      </MemoryRouter>
    </QueryClientProvider>
  );

  const result = render(strict ? <StrictMode>{tree}</StrictMode> : tree);
  return { ...result, queryClient };
}
