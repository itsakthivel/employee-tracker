import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Center, Loader } from '@mantine/core';
import { useAuth } from './AuthContext';

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <Center h="60vh">
        <Loader />
      </Center>
    );
  }
  if (!user) return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />;
  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  return <>{children}</>;
}

/** Hides admin pages in the UI. The server is the real enforcement. */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const { isAdmin } = useAuth();
  if (!isAdmin) return <Navigate to="/" replace />;
  return <>{children}</>;
}
