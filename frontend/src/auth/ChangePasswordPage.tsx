import { useState, type FormEvent } from 'react';
import { Alert, Button, PasswordInput, Stack } from '@mantine/core';
import { useNavigate } from 'react-router-dom';
import { ApiError, errorMessage, post } from '../api/client';
import { useAuth } from './AuthContext';
import { AuthCard } from './AuthCard';
import { NewPasswordFields, passwordReady } from './PasswordFields';

export function ChangePasswordPage() {
  const { user, refresh } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldError(undefined);
    try {
      await post('/auth/change-password', { currentPassword: current, newPassword: next });
      await refresh();
      navigate('/', { replace: true });
    } catch (err) {
      const fe = err instanceof ApiError ? err.fieldErrors.find((f) => f.field === 'newPassword') : undefined;
      if (fe) setFieldError(`New password ${fe.message}`);
      else setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard
      title="Choose a new password"
      intro={
        user?.mustChangePassword
          ? 'You signed in with a temporary password. Choose your own to continue.'
          : undefined
      }
    >
      {error && <Alert color="red">{error}</Alert>}
      <form onSubmit={submit}>
        <Stack>
          <PasswordInput
            label={user?.mustChangePassword ? 'Temporary password' : 'Current password'}
            autoComplete="current-password"
            required
            value={current}
            onChange={(e) => setCurrent(e.currentTarget.value)}
          />
          <NewPasswordFields
            value={next}
            confirm={confirm}
            onChange={setNext}
            onConfirm={setConfirm}
            serverError={fieldError}
          />
          <Button type="submit" loading={busy} disabled={!passwordReady(next, confirm)}>
            Change password
          </Button>
        </Stack>
      </form>
    </AuthCard>
  );
}
