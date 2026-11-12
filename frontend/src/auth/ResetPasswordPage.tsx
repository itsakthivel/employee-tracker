import { useState, type FormEvent } from 'react';
import { Alert, Anchor, Button, Stack } from '@mantine/core';
import { Link, useSearchParams } from 'react-router-dom';
import { ApiError, errorMessage, post } from '../api/client';
import { AuthCard } from './AuthCard';
import { NewPasswordFields, passwordReady } from './PasswordFields';

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string>();
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setFieldError(undefined);
    try {
      await post('/auth/password-reset/confirm', { token, newPassword: next });
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'RESET_TOKEN_INVALID') {
        setError('This link is invalid or has expired. Ask for a new one.');
      } else {
        const fe = err instanceof ApiError ? err.fieldErrors.find((f) => f.field === 'newPassword') : undefined;
        if (fe) setFieldError(`New password ${fe.message}`);
        else setError(errorMessage(err));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Set a new password">
      {done ? (
        <Alert color="teal">Your password has been changed. You can sign in now.</Alert>
      ) : !token ? (
        <Alert color="red">This link is invalid or has expired. Ask for a new one.</Alert>
      ) : (
        <form onSubmit={submit}>
          <Stack>
            {error && <Alert color="red">{error}</Alert>}
            <NewPasswordFields
              value={next}
              confirm={confirm}
              onChange={setNext}
              onConfirm={setConfirm}
              serverError={fieldError}
            />
            <Button type="submit" loading={busy} disabled={!passwordReady(next, confirm)}>
              Set password
            </Button>
          </Stack>
        </form>
      )}
      <Anchor component={Link} to={done ? '/sign-in' : '/forgot-password'} size="sm">
        {done ? 'Go to sign in' : 'Request a new link'}
      </Anchor>
    </AuthCard>
  );
}
