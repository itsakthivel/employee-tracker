import { useState } from 'react';
import { Alert, Badge, Button, Card, Group, Loader, Select, SimpleGrid, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, errorMessage, get, post, type Schemas } from '../api/client';
import { formatDate } from '../lib/format';
import { compensationSchema } from '../lib/schemas';

type CompensationList = { current?: Schemas['Compensation'] | null; items: Schemas['Compensation'][] };
type Form = z.input<typeof compensationSchema>;

/** Admin-only. Hidden until asked for, because each viewing is logged. */
export function CompensationTable({ employeeId }: { employeeId: string }) {
  const [shown, setShown] = useState(false);
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: ['compensation', employeeId],
    queryFn: () => get<CompensationList>(`/employees/${employeeId}/compensation`),
    enabled: shown,
    gcTime: 0,
  });
  const form = useForm<Form>({
    resolver: zodResolver(compensationSchema),
    defaultValues: { amount: '', currency: 'GBP', payFrequency: 'ANNUAL', effectiveDate: '', reason: '' },
  });
  const [error, setError] = useState<string | null>(null);
  const add = useMutation({
    mutationFn: (values: Form) =>
      post(`/employees/${employeeId}/compensation`, { ...values, reason: values.reason || undefined }),
    onSuccess: () => {
      form.reset({ amount: '', currency: form.getValues('currency'), payFrequency: 'ANNUAL', effectiveDate: '', reason: '' });
      queryClient.invalidateQueries({ queryKey: ['compensation', employeeId] });
      queryClient.invalidateQueries({ queryKey: ['history', employeeId] });
    },
    onError: (e) => {
      if (e instanceof ApiError && e.fieldErrors.length) {
        e.fieldErrors.forEach((f) => form.setError(f.field as keyof Form, { message: f.message }));
      } else setError(errorMessage(e));
    },
  });

  return (
    <Card withBorder>
      <Stack>
        <Group justify="space-between">
          <div>
            <Title order={4}>Compensation</Title>
            <Text size="xs" c="dimmed">
              Administrators only. Viewing is logged.
            </Text>
          </div>
          {!shown && (
            <Button size="xs" variant="light" onClick={() => setShown(true)}>
              Show compensation
            </Button>
          )}
        </Group>
        {shown && list.isLoading && <Loader size="sm" />}
        {shown && list.isError && <Alert color="red">{errorMessage(list.error)}</Alert>}
        {shown && list.data && (
          <>
            {list.data.items.length === 0 ? (
              <Text c="dimmed">No compensation recorded.</Text>
            ) : (
              <Table.ScrollContainer minWidth={480}>
                <Table>
                  <Table.Thead>
                    <Table.Tr>
                      <Table.Th>Effective</Table.Th>
                      <Table.Th>Amount</Table.Th>
                      <Table.Th>Frequency</Table.Th>
                      <Table.Th>Reason</Table.Th>
                    </Table.Tr>
                  </Table.Thead>
                  <Table.Tbody>
                    {list.data.items.map((c) => (
                      <Table.Tr key={c.id}>
                        <Table.Td>
                          {formatDate(c.effectiveDate)}{' '}
                          {list.data.current?.id === c.id && (
                            <Badge size="xs" color="teal">
                              Current
                            </Badge>
                          )}
                        </Table.Td>
                        <Table.Td ff="monospace">
                          {c.currency} {Number(c.amount).toLocaleString(undefined, { minimumFractionDigits: 2 })}
                        </Table.Td>
                        <Table.Td>{c.payFrequency.toLowerCase()}</Table.Td>
                        <Table.Td>{c.reason ?? '—'}</Table.Td>
                      </Table.Tr>
                    ))}
                  </Table.Tbody>
                </Table>
              </Table.ScrollContainer>
            )}
            <form onSubmit={form.handleSubmit((v) => add.mutate(v))}>
              <Stack gap="xs">
                <Text fw={600} size="sm">
                  Add a record (records can't be edited; enter a correction as a new record)
                </Text>
                {error && <Alert color="red">{error}</Alert>}
                <SimpleGrid cols={{ base: 1, sm: 3 }}>
                  <TextInput label="Amount" {...form.register('amount')} error={form.formState.errors.amount?.message} />
                  <TextInput label="Currency" {...form.register('currency')} error={form.formState.errors.currency?.message} />
                  <Controller
                    control={form.control}
                    name="payFrequency"
                    render={({ field }) => (
                      <Select
                        label="Frequency"
                        data={[
                          { value: 'ANNUAL', label: 'Annual' },
                          { value: 'MONTHLY', label: 'Monthly' },
                          { value: 'HOURLY', label: 'Hourly' },
                        ]}
                        value={field.value}
                        onChange={(v) => field.onChange(v ?? 'ANNUAL')}
                      />
                    )}
                  />
                  <TextInput
                    type="date"
                    label="Effective date"
                    {...form.register('effectiveDate')}
                    error={form.formState.errors.effectiveDate?.message}
                  />
                  <TextInput label="Reason" {...form.register('reason')} error={form.formState.errors.reason?.message} />
                </SimpleGrid>
                <Group>
                  <Button type="submit" size="xs" loading={add.isPending}>
                    Add record
                  </Button>
                </Group>
              </Stack>
            </form>
          </>
        )}
      </Stack>
    </Card>
  );
}
