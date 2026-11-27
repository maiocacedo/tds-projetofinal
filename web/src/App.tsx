import { QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { queryClient } from './api/queryClient';
import { SessionProvider } from './auth/SessionContext';
import { SplashOverlay } from './components/SplashOverlay';
import { ToastProvider } from './components/Toast';
import { AppRoutes } from './routes/router';

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <ToastProvider>
          <SessionProvider>
            <AppRoutes />
            <SplashOverlay />
          </SessionProvider>
        </ToastProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
