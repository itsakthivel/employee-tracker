import { useState, type FormEvent } from 'react';
import { Alert, Anchor, Button, PasswordInput, Stack, TextInput } from '@mantine/core';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { errorMessage } from '../api/client';
import { useAuth } from './AuthContext';
import { AuthCard } from './AuthCard';

export function SignInPage() {
  const { user, signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const state = location.state as { from?: string; expired?: boolean } | null;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={user.mustChangePassword ? '/change-password' : '/'} replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const u = await signIn(username, password);
      navigate(u.mustChangePassword ? '/change-password' : (state?.from ?? '/'), { replace: true });
    } catch (err) {
      setError(errorMessage(err));
      setPassword('');
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Sign in">
      {state?.expired && !error && (
        <Alert color="yellow">Your session ended. Please sign in again.</Alert>
      )}
      {error && <Alert color="red">{error}</Alert>}
      <form onSubmit={submit}>
        <Stack>
          <TextInput
            label="Work email"
            type="email"
            autoComplete="username"
            required
            value={username}
            onChange={(e) => setUsername(e.currentTarget.value)}
          />
          <PasswordInput
            label="Password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.currentTarget.value)}
          />
          <Button type="submit" loading={busy}>
            Sign in
          </Button>
          <Anchor component={Link} to="/forgot-password" size="sm">
            Forgot password?
          </Anchor>
        </Stack>
      </form>
    </AuthCard>
  );
}
