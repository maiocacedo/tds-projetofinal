import { render, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { mergeErrorModules, translateApiError } from '../api/errors';
import { server } from '../mocks/server';

describe('Scaffold smoke tests', () => {
  it('renders App with Nosso Teto heading', () => {
    render(<App />);
    expect(
      screen.getByRole('heading', { name: /nosso teto/i }),
    ).toBeInTheDocument();
  });

  it('translates mapped API errors to pt-BR and supports fallback', () => {
    expect(translateApiError('invalid_grant')).toBe(
      'E-mail ou senha não conferem. Confira e tente de novo.',
    );
    expect(translateApiError('Authentication required')).toMatch(/sessão/i);
    expect(translateApiError('Insufficient permission')).toMatch(/permissão/i);

    expect(
      translateApiError('unknown_error_code', 'Algo deu errado por aqui.'),
    ).toBe('Algo deu errado por aqui.');

    const defaultTranslated = translateApiError('unknown_error_code');
    expect(defaultTranslated).not.toBe('unknown_error_code');
    expect(defaultTranslated.length).toBeGreaterThan(0);
  });

  it('rejects duplicate API error keys across modules', () => {
    expect(() =>
      mergeErrorModules({
        './core.ts': { messages: { invalid_grant: 'Erro 1' } },
        './auth.ts': { messages: { invalid_grant: 'Erro 2' } },
      }),
    ).toThrow(/invalid_grant/);
  });

  it('intercepts HTTP requests through the MSW server', async () => {
    server.use(
      http.get('/api/health', () =>
        HttpResponse.json({ status: 'ok', app: 'nosso-teto' }),
      ),
    );

    const response = await fetch('/api/health');
    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      status: 'ok',
      app: 'nosso-teto',
    });
  });
});
