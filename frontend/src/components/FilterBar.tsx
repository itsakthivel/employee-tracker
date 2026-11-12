import { useEffect, useState } from 'react';
import { Button, Group, MultiSelect, Select, Switch, TextInput } from '@mantine/core';
import { useDebouncedValue } from '@mantine/hooks';
import type { SetURLSearchParams } from 'react-router-dom';
import { useDepartments, useEmployeeOptions, useLocations } from '../api/hooks';
import { STATUS_LABEL, TYPE_LABEL } from '../lib/format';

export const FILTER_KEYS = [
  'q',
  'departmentId',
  'locationId',
  'managerId',
  'status',
  'employmentType',
  'includeTerminated',
] as const;

const options = (labels: Record<string, string>) =>
  Object.entries(labels).map(([value, label]) => ({ value, label }));

/** Directory filters, kept in the URL so they survive reloads and can be shared. */
export function FilterBar({
  params,
  setParams,
  isAdmin,
}: {
  params: URLSearchParams;
  setParams: SetURLSearchParams;
  isAdmin: boolean;
}) {
  const departments = useDepartments();
  const locations = useLocations();
  const [q, setQ] = useState(params.get('q') ?? '');
  const [debounced] = useDebouncedValue(q, 300);
  const [managerSearch, setManagerSearch] = useState('');
  const managers = useEmployeeOptions(managerSearch);

  useEffect(() => {
    if ((params.get('q') ?? '') !== debounced) update('q', debounced ? [debounced] : []);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced]);

  useEffect(() => {
    setQ(params.get('q') ?? '');
  }, [params]);

  function update(key: string, values: string[]) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete(key);
        values.forEach((v) => next.append(key, v));
        next.delete('page');
        return next;
      },
      { replace: true },
    );
  }

  const hasFilters = FILTER_KEYS.some((k) => params.has(k));
  const managerId = params.get('managerId');
  const managerOptions = (managers.data?.items ?? []).map((e) => ({ value: e.id, label: e.fullName }));
  if (managerId && !managerOptions.some((o) => o.value === managerId)) {
    managerOptions.push({ value: managerId, label: 'Selected manager' });
  }

  return (
    <Group align="flex-end" gap="sm" wrap="wrap">
      <TextInput
        label="Search"
        placeholder={isAdmin ? 'Name, email or employee ID' : 'Name or email'}
        value={q}
        onChange={(e) => setQ(e.currentTarget.value)}
        w={240}
      />
      <MultiSelect
        label="Department"
        data={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
        value={params.getAll('departmentId')}
        onChange={(v) => update('departmentId', v)}
        clearable
        searchable
        w={200}
      />
      <MultiSelect
        label="Location"
        data={(locations.data ?? []).map((l) => ({ value: l.id, label: l.name }))}
        value={params.getAll('locationId')}
        onChange={(v) => update('locationId', v)}
        clearable
        w={180}
      />
      <Select
        label="Manager"
        data={managerOptions}
        value={managerId}
        onChange={(v) => update('managerId', v ? [v] : [])}
        searchable
        onSearchChange={setManagerSearch}
        clearable
        w={200}
      />
      {isAdmin && (
        <>
          <MultiSelect
            label="Status"
            data={options(STATUS_LABEL)}
            value={params.getAll('status')}
            onChange={(v) => update('status', v)}
            clearable
            w={200}
          />
          <MultiSelect
            label="Employment type"
            data={options(TYPE_LABEL)}
            value={params.getAll('employmentType')}
            onChange={(v) => update('employmentType', v)}
            clearable
            w={200}
          />
          <Switch
            label="Include former employees"
            checked={params.get('includeTerminated') === 'true'}
            onChange={(e) => update('includeTerminated', e.currentTarget.checked ? ['true'] : [])}
            mb={8}
          />
        </>
      )}
      {hasFilters && (
        <Button
          variant="subtle"
          onClick={() => {
            setQ('');
            setParams(new URLSearchParams(), { replace: true });
          }}
        >
          Clear filters
        </Button>
      )}
    </Group>
  );
}
