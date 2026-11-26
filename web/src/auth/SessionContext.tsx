import { useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { fetchMe, type UserMe } from '../api/me';
import { queryKeys } from '../api/queryKeys';
import {
  invalidateSessionEpoch,
  loginWithPassword,
  onSessionExpired,
  refreshAccessToken,
  request,
  revokeSession,
  SessionSupersededError,
} from './apiClient';
import { clearAccessToken, getAccessToken } from './tokenStore';

export type SessionStatus = 'loading' | 'authenticated' | 'anonymous';

export interface SessionValue {
  status: SessionStatus;
  user: UserMe | null;
  hasPermission: (permission: string) => boolean;
  /**
   * Recarrega `/api/users/me` (ex.: depois de salvar a ficha ou o welcome-ack).
   * Os guards leem `user` daqui, não do cache: invalidar só `queryKeys.me`
   * deixaria o guard com o estado antigo.
   */
  refreshMe: () => Promise<UserMe | null>;
  login: (email: string, password: string) => Promise<UserMe>;
  logout: () => Promise<void>;
  request: typeof request;
}

const SessionContext = createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<SessionStatus>('loading');
  const [user, setUser] = useState<UserMe | null>(null);
  // Geração da sessão local: login, logout e expiração a avançam. Boot e
  // refreshMe que resolvem numa geração antiga são ignorados (não ressuscitam
  // a sessão encerrada nem derrubam a nova).
  const generationRef = useRef(0);

  const applyUser = useCallback(
    (me: UserMe) => {
      queryClient.setQueryData(queryKeys.me, me);
      setUser(me);
      setStatus('authenticated');
    },
    [queryClient],
  );

  const resetSession = useCallback(() => {
    generationRef.current += 1;
    clearAccessToken();
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  // Refresh forçado falhou ou segundo 401: a sessão acabou.
  useEffect(() => onSessionExpired(resetSession), [resetSession]);

  // Boot: refresh silencioso. No StrictMode este efeito roda 2×; as duas
  // montagens aguardam a MESMA promise single-flight (1 refresh na rede).
  useEffect(() => {
    let cancelled = false;
    const generation = generationRef.current;
    const stale = () => cancelled || generation !== generationRef.current;
    (async () => {
      const token = getAccessToken() ?? (await refreshAccessToken());
      if (stale()) return;
      if (!token) {
        setUser(null);
        setStatus('anonymous');
        return;
      }
      try {
        const me = await fetchMe();
        if (!stale()) applyUser(me);
      } catch {
        if (!stale()) resetSession();
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [applyUser, resetSession]);

  const refreshMe = useCallback(async () => {
    const generation = generationRef.current;
    try {
      const me = await fetchMe();
      if (generation !== generationRef.current) return null;
      applyUser(me);
      return me;
    } catch {
      return null;
    }
  }, [applyUser]);

  const login = useCallback(
    async (email: string, password: string) => {
      const generation = ++generationRef.current;
      let me: UserMe;
      try {
        await loginWithPassword(email, password);
        // Logout/novo login no meio: não chama /me com token de outra sessão.
        if (generation !== generationRef.current) throw new SessionSupersededError();
        me = await fetchMe();
      } catch (error) {
        // Token emitido sem /me (ou login recusado): nada de sessão "meio
        // logada" no tokenStore; a geração que este login abriu termina anônima.
        if (generation === generationRef.current) {
          clearAccessToken();
          invalidateSessionEpoch();
          setUser(null);
          setStatus('anonymous');
        }
        throw error;
      }
      if (generation !== generationRef.current) throw new SessionSupersededError();
      applyUser(me);
      return me;
    },
    [applyUser],
  );

  const logout = useCallback(async () => {
    generationRef.current += 1;
    invalidateSessionEpoch();
    try {
      await revokeSession();
    } catch {
      // revoke falhou (rede/500): a sessão local é encerrada mesmo assim
    } finally {
      // Refresh disparado enquanto o revoke voava também é descartado.
      invalidateSessionEpoch();
      resetSession();
    }
  }, [resetSession]);

  const hasPermission = useCallback(
    (permission: string) => user?.permissions.includes(permission) ?? false,
    [user],
  );

  const value = useMemo<SessionValue>(
    () => ({ status, user, hasPermission, refreshMe, login, logout, request }),
    [status, user, hasPermission, refreshMe, login, logout],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

// eslint-disable-next-line react-refresh/only-export-components
export function useSession(): SessionValue {
  const value = useContext(SessionContext);
  if (!value) throw new Error('useSession must be used inside <SessionProvider>');
  return value;
}
