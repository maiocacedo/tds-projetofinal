import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { queryClient } from '../api/queryClient';
import { resetAuthClientState } from '../auth/apiClient';
import { clearAccessToken } from '../auth/tokenStore';
import { resetMockDb } from '../mocks/db';
import { server } from '../mocks/server';

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetMockDb();
  clearAccessToken();
  resetAuthClientState();
  queryClient.clear();
});

afterAll(() => {
  server.close();
});
