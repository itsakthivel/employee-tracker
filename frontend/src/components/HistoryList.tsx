import { useState } from 'react';
import { Alert, Badge, Group, Loader, Pagination, Stack, Table, Text } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { errorMessage, get, type Schemas } from '../api/client';
import { FIELD_LABEL, formatDateTime } from '../lib/format';

type HistoryPage = { items: Schemas['ChangeEntry'][]; totalItems: number };
const PAGE_SIZE = 20;

const TYPE_LABEL: Record<string, string> = {
  CREATED: 'Created',
  UPDATED: 'Updated',
  STATUS_CHANGED: 'Status',
  REACTIVATED: 'Reactivated',
  SELF_UPDATED: 'Self-service',
};

export function HistoryList({ employeeId }: { employeeId: string }) {
  const [page, setPage] = useState(0);
  const history = useQuery({
    queryKey: ['history', employeeId, page],
    queryFn: () => get<HistoryPage>(`/employees/${employeeId}/history?page=${page}&size=${PAGE_SIZE}`),
  });
  if (history.isLoading) return <Loader size="sm" />;
  if (history.isError) return <Alert color="red">{errorMessage(history.error)}</Alert>;
  const data = history.data!;
  if (data.items.length === 0) return <Text c="dimmed">No changes recorded.</Text>;
  const pages = Math.ceil(data.totalItems / PAGE_SIZE);
  return (
    <Stack>
      <Table.ScrollContainer minWidth={560}>
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>When</Table.Th>
              <Table.Th>Change</Table.Th>
              <Table.Th>From → to</Table.Th>
              <Table.Th>By</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {data.items.map((c) => (
              <Table.Tr key={c.id}>
                <Table.Td>{formatDateTime(c.changedAt)}</Table.Td>
                <Table.Td>
                  <Group gap={6}>
                    <Badge size="sm" variant="light">
                      {TYPE_LABEL[c.changeType]}
                    </Badge>
                    {c.fieldName && <Text size="sm">{FIELD_LABEL[c.fieldName] ?? c.fieldName}</Text>}
                  </Group>
                </Table.Td>
                <Table.Td>
                  {c.changeType === 'CREATED' ? (
                    '—'
                  ) : (
                    <Text size="sm">
                      {c.oldValue ?? '(empty)'} → {c.newValue ?? '(empty)'}
                    </Text>
                  )}
                </Table.Td>
                <Table.Td>{c.changedBy.name}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {pages > 1 && <Pagination total={pages} value={page + 1} onChange={(p) => setPage(p - 1)} />}
    </Stack>
  );
}
