import { useState } from 'react';
import { Alert, Button, Group, Loader, Modal, Stack, Table, TextInput, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, del, errorMessage, get, post, put } from '../api/client';

interface Item {
  id: string;
  name: string;
  employeeCount?: number;
}

/** Admin: departments and locations (FR-022/FR-023). */
function ReferenceDataPage({ kind, title }: { kind: 'departments' | 'locations'; title: string }) {
  const queryClient = useQueryClient();
  const items = useQuery({ queryKey: [kind], queryFn: () => get<Item[]>(`/${kind}`) });
  const [editing, setEditing] = useState<Item | 'new' | null>(null);
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = () => queryClient.invalidateQueries({ queryKey: [kind] });

  async function save() {
    setBusy(true);
    setError(null);
    try {
      if (editing === 'new') await post(`/${kind}`, { name });
      else if (editing) await put(`/${kind}/${editing.id}`, { name });
      setEditing(null);
      refresh();
    } catch (e) {
      setError(e instanceof ApiError && e.fieldErrors.length ? `Name ${e.fieldErrors[0].message}` : errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function remove(item: Item) {
    setError(null);
    try {
      await del(`/${kind}/${item.id}`);
      refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <Stack maw={700}>
      <Group justify="space-between">
        <Title order={2}>{title}</Title>
        <Button
          onClick={() => {
            setName('');
            setError(null);
            setEditing('new');
          }}
        >
          Add
        </Button>
      </Group>
      {error && !editing && (
        <Alert color="red" withCloseButton onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {items.isLoading && <Loader />}
      {items.data && (
        <Table>
          <Table.Thead>
            <Table.Tr>
              <Table.Th>Name</Table.Th>
              <Table.Th ta="right">Employees</Table.Th>
              <Table.Th />
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {items.data.map((item) => (
              <Table.Tr key={item.id}>
                <Table.Td>{item.name}</Table.Td>
                <Table.Td ta="right">{item.employeeCount}</Table.Td>
                <Table.Td>
                  <Group gap="xs" justify="flex-end">
                    <Button
                      size="compact-sm"
                      variant="subtle"
                      onClick={() => {
                        setName(item.name);
                        setError(null);
                        setEditing(item);
                      }}
                    >
                      Rename
                    </Button>
                    <Button size="compact-sm" variant="subtle" color="red" onClick={() => remove(item)}>
                      Delete
                    </Button>
                  </Group>
                </Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      )}
      <Modal opened={!!editing} onClose={() => setEditing(null)} title={editing === 'new' ? `Add` : 'Rename'}>
        <Stack>
          {error && <Alert color="red">{error}</Alert>}
          <TextInput label="Name" value={name} onChange={(e) => setName(e.currentTarget.value)} maxLength={100} data-autofocus />
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setEditing(null)}>
              Cancel
            </Button>
            <Button loading={busy} disabled={!name.trim()} onClick={save}>
              Save
            </Button>
          </Group>
        </Stack>
      </Modal>
    </Stack>
  );
}

export const DepartmentsPage = () => <ReferenceDataPage kind="departments" title="Departments" />;
export const LocationsPage = () => <ReferenceDataPage kind="locations" title="Locations" />;
