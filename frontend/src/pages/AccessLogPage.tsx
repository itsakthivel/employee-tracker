import { useState } from 'react';
import { Alert, Anchor, Badge, Group, Loader, Pagination, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { errorMessage, get, query, type Schemas } from '../api/client';
import { useEmployeeOptions } from '../api/hooks';
import { FIELD_LABEL, formatDateTime } from '../lib/format';

type Page = { items: Schemas['AccessLogEntry'][]; totalItems: number };
const SIZE = 50;

/** Admin: who revealed or changed sensitive data, and when (FR-006d). */
export function AccessLogPage() {
  const [search, setSearch] = useState('');
  const employees = useEmployeeOptions(search, true);
  const [employeeId, setEmployeeId] = useState<string | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const params = {
    employeeId: employeeId ?? undefined,
    from: from ? new Date(`${from}T00:00:00`).toISOString() : undefined,
    to: to ? new Date(`${to}T23:59:59.999`).toISOString() : undefined,
    page,
    size: SIZE,
  };
  const log = useQuery({
    queryKey: ['access-log', params],
    queryFn: () => get<Page>(`/sensitive-access-log${query(params)}`),
    placeholderData: keepPreviousData,
  });

  return (
    <Stack>
      <Title order={2}>Sensitive access log</Title>
      <Group align="flex-end">
        <Select
          label="Employee"
          searchable
          clearable
          onSearchChange={setSearch}
          data={(employees.data?.items ?? []).map((e) => ({ value: e.id, label: e.fullName }))}
          value={employeeId}
          onChange={(v) => {
            setEmployeeId(v);
            setPage(0);
          }}
          w={240}
        />
        <TextInput type="date" label="From" value={from} onChange={(e) => { setFrom(e.currentTarget.value); setPage(0); }} />
        <TextInput type="date" label="To" value={to} onChange={(e) => { setTo(e.currentTarget.value); setPage(0); }} />
      </Group>
      {log.isError && <Alert color="red">{errorMessage(log.error)}</Alert>}
      {log.isLoading && <Loader />}
      {log.data && log.data.items.length === 0 && <Text c="dimmed">No entries.</Text>}
      {log.data && log.data.items.length > 0 && (
        <>
          <Table.ScrollContainer minWidth={640}>
            <Table striped>
              <Table.Thead>
                <Table.Tr>
                  <Table.Th>When</Table.Th>
                  <Table.Th>Who</Table.Th>
                  <Table.Th>Action</Table.Th>
                  <Table.Th>Field</Table.Th>
                  <Table.Th>Employee</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {log.data.items.map((e) => (
                  <Table.Tr key={e.id}>
                    <Table.Td>{formatDateTime(e.occurredAt)}</Table.Td>
                    <Table.Td>{e.actor.name}</Table.Td>
                    <Table.Td>
                      <Badge variant="light" color={e.action === 'REVEAL' ? 'orange' : 'blue'}>
                        {e.action.toLowerCase()}
                      </Badge>
                    </Table.Td>
                    <Table.Td>{FIELD_LABEL[e.field] ?? e.field}</Table.Td>
                    <Table.Td>
                      <Anchor component={Link} to={`/employees/${e.employee.id}`}>
                        {e.employee.name}
                      </Anchor>
                    </Table.Td>
                  </Table.Tr>
                ))}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
          {log.data.totalItems > SIZE && (
            <Pagination total={Math.ceil(log.data.totalItems / SIZE)} value={page + 1} onChange={(p) => setPage(p - 1)} />
          )}
        </>
      )}
    </Stack>
  );
}
