import { useEffect, useState } from 'react';
import { Alert, Button, Card, Group, Loader, Modal, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, errorMessage, get, post, put, type Schemas } from '../api/client';
import { FIELD_LABEL } from '../lib/format';
import { sensitiveSchema } from '../lib/schemas';

type Masked = Schemas['SensitiveMasked'];
type RevealField = 'govIdNumber' | 'taxId' | 'bankAccountHolder' | 'bankAccountNumber' | 'bankRoutingCode';

const ROWS: { field: keyof Masked; revealable: boolean }[] = [
  { field: 'govIdType', revealable: false },
  { field: 'govIdNumber', revealable: true },
  { field: 'taxId', revealable: true },
  { field: 'bankAccountHolder', revealable: true },
  { field: 'bankName', revealable: false },
  { field: 'bankAccountNumber', revealable: true },
  { field: 'bankRoutingCode', revealable: true },
];

/**
 * Admin-only. Shows masked values; Reveal fetches one full value, which is kept only in this
 * component's state (never in the query cache) and is dropped when the page is left.
 */
export function SensitivePanel({ employeeId }: { employeeId: string }) {
  const queryClient = useQueryClient();
  const masked = useQuery({
    queryKey: ['sensitive', employeeId],
    queryFn: () => get<Masked>(`/employees/${employeeId}/sensitive`),
    gcTime: 0,
  });
  const [revealed, setRevealed] = useState<Partial<Record<RevealField, string | null>>>({});
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => () => setRevealed({}), [employeeId]);

  const reveal = useMutation({
    mutationFn: (field: RevealField) =>
      post<{ field: string; value: string | null }>(`/employees/${employeeId}/sensitive/reveal`, { field }),
    onSuccess: (r) => setRevealed((prev) => ({ ...prev, [r.field]: r.value })),
    onError: (e) => setError(errorMessage(e)),
  });

  if (masked.isLoading) return <Loader size="sm" />;
  if (masked.isError) return <Alert color="red">{errorMessage(masked.error)}</Alert>;
  const data = masked.data!;

  return (
    <Card withBorder>
      <Stack>
        <Group justify="space-between">
          <div>
            <Title order={4}>Sensitive data</Title>
            <Text size="xs" c="dimmed">
              Administrators only. Every reveal and change is logged.
            </Text>
          </div>
          <Button size="xs" variant="light" onClick={() => setEditing(true)}>
            Edit
          </Button>
        </Group>
        {error && (
          <Alert color="red" withCloseButton onClose={() => setError(null)}>
            {error}
          </Alert>
        )}
        <SimpleGrid cols={{ base: 1, sm: 2 }}>
          {ROWS.map(({ field, revealable }) => {
            const shown =
              revealable && field in revealed ? revealed[field as RevealField] : (data[field] as string | null);
            return (
              <Group key={field} justify="space-between" wrap="nowrap">
                <div>
                  <Text size="xs" c="dimmed">
                    {FIELD_LABEL[field]}
                  </Text>
                  <Text ff={revealable ? 'monospace' : undefined} data-testid={`sensitive-${field}`}>
                    {shown ?? '—'}
                  </Text>
                </div>
                {revealable && data[field] && !(field in revealed) && (
                  <Button
                    size="compact-xs"
                    variant="subtle"
                    loading={reveal.isPending && reveal.variables === field}
                    onClick={() => reveal.mutate(field as RevealField)}
                  >
                    Reveal
                  </Button>
                )}
              </Group>
            );
          })}
        </SimpleGrid>
      </Stack>
      <SensitiveEditModal
        opened={editing}
        onClose={() => setEditing(false)}
        employeeId={employeeId}
        version={data.version}
        current={data}
        onSaved={() => {
          setRevealed({});
          queryClient.invalidateQueries({ queryKey: ['sensitive', employeeId] });
          queryClient.invalidateQueries({ queryKey: ['history', employeeId] });
        }}
      />
    </Card>
  );
}

type SensitiveForm = z.input<typeof sensitiveSchema>;

function SensitiveEditModal({
  opened,
  onClose,
  employeeId,
  version,
  current,
  onSaved,
}: {
  opened: boolean;
  onClose: () => void;
  employeeId: string;
  version: number;
  current: Masked;
  onSaved: () => void;
}) {
  const form = useForm<SensitiveForm>({
    resolver: zodResolver(sensitiveSchema),
    defaultValues: {
      govIdType: '',
      govIdNumber: '',
      taxId: '',
      bankAccountHolder: '',
      bankName: '',
      bankAccountNumber: '',
      bankRoutingCode: '',
    },
  });
  const [error, setError] = useState<string | null>(null);
  const hasExisting = Object.entries(current).some(([k, v]) => k !== 'version' && v);

  useEffect(() => {
    if (opened) {
      form.reset({
        govIdType: current.govIdType ?? '',
        govIdNumber: '',
        taxId: '',
        bankAccountHolder: '',
        bankName: current.bankName ?? '',
        bankAccountNumber: '',
        bankRoutingCode: '',
      });
      setError(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [opened]);

  const save = form.handleSubmit(async (values) => {
    const body = Object.fromEntries(
      Object.entries(values).map(([k, v]) => [k, v === '' || v === undefined ? null : v]),
    );
    try {
      await put(`/employees/${employeeId}/sensitive`, { version, ...body });
      onSaved();
      onClose();
    } catch (e) {
      if (e instanceof ApiError) {
        e.fieldErrors.forEach((f) => form.setError(f.field as keyof SensitiveForm, { message: f.message }));
        if (e.code === 'STALE_VERSION') setError('Someone else changed this data. Close and reopen to reload.');
        else if (!e.fieldErrors.length) setError(e.detail);
      } else setError(errorMessage(e));
    }
  });

  const field = (name: keyof SensitiveForm, placeholder?: string) => (
    <TextInput
      label={FIELD_LABEL[name]}
      placeholder={placeholder}
      autoComplete="off"
      {...form.register(name)}
      error={form.formState.errors[name]?.message}
    />
  );

  return (
    <Modal opened={opened} onClose={onClose} title="Edit sensitive data" size="lg">
      <form onSubmit={save}>
        <Stack>
          {hasExisting && (
            <Alert color="yellow">
              Saving replaces all of these fields. Re-enter every value you want to keep; empty fields are
              cleared.
            </Alert>
          )}
          {error && <Alert color="red">{error}</Alert>}
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            {field('govIdType', 'e.g. Passport')}
            {field('govIdNumber', current.govIdNumber ?? undefined)}
            {field('taxId', current.taxId ?? undefined)}
            {field('bankAccountHolder')}
            {field('bankName')}
            {field('bankAccountNumber', current.bankAccountNumber ?? undefined)}
            {field('bankRoutingCode', current.bankRoutingCode ?? undefined)}
          </SimpleGrid>
          <Group justify="flex-end">
            <Button variant="default" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" loading={form.formState.isSubmitting}>
              Save
            </Button>
          </Group>
        </Stack>
      </form>
    </Modal>
  );
}
