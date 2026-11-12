import { Alert, Button, Group, Loader, Stack, Text, Title } from '@mantine/core';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { errorMessage, get, type EmployeePage } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { EmployeeTable } from '../components/EmployeeTable';
import { FILTER_KEYS, FilterBar } from '../components/FilterBar';

export function DirectoryPage() {
  const { isAdmin } = useAuth();
  const [params, setParams] = useSearchParams();
  const sort = params.get('sort') ?? 'fullName';
  const page = Number(params.get('page') ?? 0);

  const filterQuery = new URLSearchParams();
  FILTER_KEYS.forEach((k) => params.getAll(k).forEach((v) => filterQuery.append(k, v)));
  const listQuery = new URLSearchParams(filterQuery);
  listQuery.set('sort', sort);
  listQuery.set('page', String(page));

  const result = useQuery({
    queryKey: ['employees', listQuery.toString()],
    queryFn: () => get<EmployeePage>(`/employees?${listQuery}`),
    placeholderData: keepPreviousData,
  });

  const set = (key: string, value: string) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set(key, value);
        if (key !== 'page') next.delete('page');
        return next;
      },
      { replace: true },
    );

  return (
    <Stack>
      <Group justify="space-between">
        <div>
          <Title order={2}>Directory</Title>
          {result.data && (
            <Text c="dimmed" size="sm">
              {result.data.totalItems} {result.data.totalItems === 1 ? 'employee' : 'employees'}
            </Text>
          )}
        </div>
        {isAdmin && (
          <Group>
            <Button
              variant="default"
              component="a"
              href={`/api/v1/employees/export${filterQuery.size ? `?${filterQuery}` : ''}`}
            >
              Export CSV
            </Button>
            <Button component={Link} to="/employees/new">
              Add employee
            </Button>
          </Group>
        )}
      </Group>
      <FilterBar params={params} setParams={setParams} isAdmin={isAdmin} />
      {result.isError && <Alert color="red">{errorMessage(result.error)}</Alert>}
      {result.isLoading && <Loader />}
      {result.data && (
        <EmployeeTable
          page={result.data}
          isAdmin={isAdmin}
          sort={sort}
          onSort={(s) => set('sort', s)}
          onPage={(p) => set('page', String(p))}
          onClearFilters={() => setParams(new URLSearchParams(), { replace: true })}
        />
      )}
    </Stack>
  );
}
