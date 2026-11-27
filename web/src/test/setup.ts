import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, beforeEach, vi } from 'vitest';
import { queryClient } from '../api/queryClient';
import { resetAuthClientState } from '../auth/apiClient';
import { clearAccessToken } from '../auth/tokenStore';
import { SPLASH_SEEN_KEY } from '../components/SplashOverlay';
import { resetMockDb } from '../mocks/db';
import { server } from '../mocks/server';
import { installMotionPolyfills, resetMotionTestState, setPrefersReducedMotion } from './motion';

// Polyfills jsdom (ResizeObserver, IntersectionObserver, matchMedia controlável,
// pointer capture, scrollIntoView, scrollTo) para todas as telas F3–F11.
installMotionPolyfills();

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' });
});

beforeEach(() => {
  // A splash só aparece em quem limpar a flag (components.test.tsx).
  sessionStorage.setItem(SPLASH_SEEN_KEY, '1');
});

afterEach(() => {
  cleanup();
  server.resetHandlers();
  resetMockDb();
  clearAccessToken();
  resetAuthClientState();
  queryClient.clear();
  sessionStorage.clear();
  setPrefersReducedMotion(false);
  resetMotionTestState();
  vi.useRealTimers();
});

afterAll(() => {
  server.close();
});
