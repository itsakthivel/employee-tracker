import { Anchor, Badge, Button, Group, Pagination, Stack, Table, Text, UnstyledButton } from '@mantine/core';
import { Link } from 'react-router-dom';
import type { EmployeePage } from '../api/client';
import { formatDate, STATUS_COLOR, STATUS_LABEL } from '../lib/format';

interface Column {
  key: string;
  label: string;
  adminOnly?: boolean;
}

const SORTABLE: Column[] = [
  { key: 'fullName', label: 'Name' },
  { key: 'department', label: 'Department' },
  { key: 'startDate', label: 'Start date', adminOnly: true },
  { key: 'status', label: 'Status', adminOnly: true },
];

export function EmployeeTable({
  page,
  isAdmin,
  sort,
  onSort,
  onPage,
  onClearFilters,
}: {
  page: EmployeePage;
  isAdmin: boolean;
  sort: string;
  onSort: (sort: string) => void;
  onPage: (page: number) => void;
  onClearFilters: () => void;
}) {
  if (page.items.length === 0) {
    return (
      <Stack align="center" py="xl" gap="xs">
        <Text fw={600}>No employees found</Text>
        <Text c="dimmed" size="sm">
          Try a different search, or clear the filters.
        </Text>
        <Button variant="light" onClick={onClearFilters}>
          Clear filters
        </Button>
      </Stack>
    );
  }

  const header = (c: Column) => {
    const current = sort.replace(/^-/, '') === c.key;
    const desc = sort.startsWith('-');
    return (
      <Table.Th key={c.key}>
        <UnstyledButton
          onClick={() => onSort(current && !desc ? `-${c.key}` : c.key)}
          fw={600}
          aria-sort={current ? (desc ? 'descending' : 'ascending') : 'none'}
        >
          {c.label} {current ? (desc ? '↓' : '↑') : ''}
        </UnstyledButton>
      </Table.Th>
    );
  };
  const columns = SORTABLE.filter((c) => isAdmin || !c.adminOnly);

  return (
    <Stack>
      <Table.ScrollContainer minWidth={640}>
        <Table striped highlightOnHover>
          <Table.Thead>
            <Table.Tr>
              {header(columns[0])}
              <Table.Th>Job title</Table.Th>
              {header(columns[1])}
              <Table.Th>Location</Table.Th>
              <Table.Th>Work email</Table.Th>
              {columns.slice(2).map(header)}
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {page.items.map((e) => (
              <Table.Tr key={e.id}>
                <Table.Td>
                  <Anchor component={Link} to={`/employees/${e.id}`}>
                    {e.fullName}
                  </Anchor>
                  {e.isPreHire && (
                    <Badge ml="xs" size="sm" variant="light">
                      Starting on {formatDate(e.startDate)}
                    </Badge>
                  )}
                </Table.Td>
                <Table.Td>{e.jobTitle}</Table.Td>
                <Table.Td>{e.department.name}</Table.Td>
                <Table.Td>{e.location?.name ?? '—'}</Table.Td>
                <Table.Td>{e.workEmail}</Table.Td>
                {isAdmin && (
                  <>
                    <Table.Td>{formatDate(e.startDate)}</Table.Td>
                    <Table.Td>
                      <Badge color={STATUS_COLOR[e.status ?? '']} variant="light">
                        {STATUS_LABEL[e.status ?? '']}
                      </Badge>
                    </Table.Td>
                  </>
                )}
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Table.ScrollContainer>
      {page.totalPages > 1 && (
        <Group justify="center">
          <Pagination total={page.totalPages} value={page.page + 1} onChange={(p) => onPage(p - 1)} />
        </Group>
      )}
    </Stack>
  );
}
