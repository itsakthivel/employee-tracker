import { useState, type FormEvent } from 'react';
import { Alert, Anchor, Button, Stack, TextInput } from '@mantine/core';
import { Link } from 'react-router-dom';
import { errorMessage, post } from '../api/client';
import { AuthCard } from './AuthCard';

export function ForgotPasswordPage() {
  const [username, setUsername] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await post('/auth/password-reset', { username });
      setSent(true);
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthCard title="Reset your password" intro="We'll email you a link that works once, for 1 hour.">
      {sent ? (
        <Alert color="teal">If an account exists for that email, we've sent a reset link.</Alert>
      ) : (
        <form onSubmit={submit}>
          <Stack>
            {error && <Alert color="red">{error}</Alert>}
            <TextInput
              label="Work email"
              type="email"
              required
              value={username}
              onChange={(e) => setUsername(e.currentTarget.value)}
            />
            <Button type="submit" loading={busy}>
              Send reset link
            </Button>
          </Stack>
        </form>
      )}
      <Anchor component={Link} to="/sign-in" size="sm">
        Back to sign in
      </Anchor>
    </AuthCard>
  );
}
