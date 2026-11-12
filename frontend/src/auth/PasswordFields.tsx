import { PasswordInput, Stack } from '@mantine/core';
import { PASSWORD_MIN } from '../lib/schemas';

export function NewPasswordFields({
  value,
  confirm,
  onChange,
  onConfirm,
  serverError,
}: {
  value: string;
  confirm: string;
  onChange: (v: string) => void;
  onConfirm: (v: string) => void;
  serverError?: string;
}) {
  const tooShort = value.length > 0 && value.length < PASSWORD_MIN;
  const mismatch = confirm.length > 0 && confirm !== value;
  return (
    <Stack>
      <PasswordInput
        label="New password"
        description={`At least ${PASSWORD_MIN} characters. Avoid common passwords.`}
        autoComplete="new-password"
        required
        value={value}
        onChange={(e) => onChange(e.currentTarget.value)}
        error={tooShort ? `Must be at least ${PASSWORD_MIN} characters` : serverError}
      />
      <PasswordInput
        label="Confirm new password"
        autoComplete="new-password"
        required
        value={confirm}
        onChange={(e) => onConfirm(e.currentTarget.value)}
        error={mismatch ? 'Passwords do not match' : undefined}
      />
    </Stack>
  );
}

export function passwordReady(value: string, confirm: string) {
  return value.length >= PASSWORD_MIN && value === confirm;
}
