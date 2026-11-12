import { createContext, useCallback, useContext, useEffect, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError, get, post, SESSION_EXPIRED, type CurrentUser } from '../api/client';

interface AuthState {
  user: CurrentUser | null;
  loading: boolean;
  isAdmin: boolean;
  signIn: (username: string, password: string) => Promise<CurrentUser>;
  signOut: () => Promise<void>;
  refresh: () => Promise<unknown>;
}

const AuthContext = createContext<AuthState | null>(null);

async function loadMe(): Promise<CurrentUser | null> {
  try {
    return await get<CurrentUser>('/auth/me');
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) return null;
    throw e;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const me = useQuery({ queryKey: ['me'], queryFn: loadMe, staleTime: 60_000 });

  useEffect(() => {
    const onExpired = () => {
      queryClient.clear();
      queryClient.setQueryData(['me'], null);
      navigate('/sign-in', { replace: true, state: { expired: true } });
    };
    window.addEventListener(SESSION_EXPIRED, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED, onExpired);
  }, [queryClient, navigate]);

  const signIn = useCallback(
    async (username: string, password: string) => {
      const user = await post<CurrentUser>('/auth/login', { username, password });
      queryClient.clear();
      queryClient.setQueryData(['me'], user);
      return user;
    },
    [queryClient],
  );

  const signOut = useCallback(async () => {
    try {
      await post('/auth/logout');
    } finally {
      queryClient.clear();
      queryClient.setQueryData(['me'], null);
      navigate('/sign-in', { replace: true });
    }
  }, [queryClient, navigate]);

  const user = me.data ?? null;
  const value: AuthState = {
    user,
    loading: me.isLoading,
    isAdmin: user?.role === 'ADMIN',
    signIn,
    signOut,
    refresh: me.refetch,
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
