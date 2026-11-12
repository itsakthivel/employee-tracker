import { Alert, Anchor, Card, Loader, SimpleGrid, Stack, Table, Text, Title } from '@mantine/core';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { errorMessage, get, type Schemas } from '../api/client';
import { STATUS_LABEL, TYPE_LABEL } from '../lib/format';

/** Admin: headcount (FR-020). Every count links to the matching directory filter. */
export function OverviewPage() {
  const data = useQuery({ queryKey: ['headcount'], queryFn: () => get<Schemas['Headcount']>('/reports/headcount') });
  if (data.isLoading) return <Loader />;
  if (data.isError) return <Alert color="red">{errorMessage(data.error)}</Alert>;
  const h = data.data!;

  const countTable = (title: string, rows: { label: string; count: number; to: string }[]) => (
    <Card withBorder>
      <Title order={4} mb="sm">
        {title}
      </Title>
      <Table>
        <Table.Tbody>
          {rows.map((r) => (
            <Table.Tr key={r.label}>
              <Table.Td>{r.label}</Table.Td>
              <Table.Td ta="right">
                <Anchor component={Link} to={r.to} ff="monospace">
                  {r.count}
                </Anchor>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Card>
  );

  return (
    <Stack>
      <Title order={2}>Overview</Title>
      <Card withBorder maw={280}>
        <Text size="sm" c="dimmed">
          Current headcount (active + on leave)
        </Text>
        <Text fz={40} fw={700} ff="monospace">
          {h.totalActive}
        </Text>
      </Card>
      <SimpleGrid cols={{ base: 1, md: 3 }}>
        {countTable(
          'By department',
          h.byDepartment.map((d) => ({ label: d.department.name, count: d.count, to: `/?departmentId=${d.department.id}` })),
        )}
        {countTable(
          'By status',
          Object.entries(h.byStatus).map(([s, c]) => ({ label: STATUS_LABEL[s] ?? s, count: c, to: `/?status=${s}` })),
        )}
        {countTable(
          'By employment type (current)',
          Object.entries(h.byEmploymentType).map(([t, c]) => ({
            label: TYPE_LABEL[t] ?? t,
            count: c,
            to: `/?employmentType=${t}`,
          })),
        )}
      </SimpleGrid>
    </Stack>
  );
}
