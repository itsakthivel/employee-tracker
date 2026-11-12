import { useState } from 'react';
import { Alert, Badge, Button, Group, Loader, Menu, Modal, PasswordInput, Select, Stack, Table, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, errorMessage, get, patch, post, type Schemas } from '../api/client';
import { useEmployeeOptions } from '../api/hooks';
import { useAuth } from '../auth/AuthContext';
import { formatDateTime } from '../lib/format';
import { PASSWORD_MIN } from '../lib/schemas';

type Account = Schemas['UserAccount'];

/** Admin: sign-in accounts (FR-003c, FR-003e). */
export function UsersPage() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const accounts = useQuery({ queryKey: ['users'], queryFn: () => get<Account[]>('/users') });
  const [creating, setCreating] = useState(false);
  const [tempFor, setTempFor] = useState<Account | null>(null);
  const [error, setError] = useState<string | null>(null);
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['users'] });

  async function update(a: Account, body: { role?: string; enabled?: boolean }) {
    setError(null);
    try {
      await patch(`/users/${a.id}`, body);
      refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  const term = q.trim().toLowerCase();
  const rows = (accounts.data ?? []).filter(
    (a) => !term || a.username.includes(term) || a.employee?.name.toLowerCase().includes(term),
  );

  return (
    <Stack>
      <Group justify="space-between">
        <Title order={2}>Sign-in accounts</Title>
        <Button onClick={() => setCreating(true)}>Create sign-in</Button>
      </Group>
      <TextInput placeholder="Search accounts" value={q} onChange={(e) => setQ(e.currentTarget.value)} maw={320} />
      {error && (
        <Alert color="red" withCloseButton onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {accounts.isLoading && <Loader />}
      {accounts.data && (
        <Table.ScrollContainer minWidth={720}>
          <Table striped>
            <Table.Thead>
              <Table.Tr>
                <Table.Th>Username</Table.Th>
                <Table.Th>Employee</Table.Th>
                <Table.Th>Role</Table.Th>
                <Table.Th>State</Table.Th>
                <Table.Th>Last sign-in</Table.Th>
                <Table.Th />
              </Table.Tr>
            </Table.Thead>
            <Table.Tbody>
              {rows.map((a) => (
                <Table.Tr key={a.id}>
                  <Table.Td>{a.username}</Table.Td>
                  <Table.Td>{a.employee?.name ?? '—'}</Table.Td>
                  <Table.Td>{a.role === 'ADMIN' ? 'Administrator' : 'Employee'}</Table.Td>
                  <Table.Td>
                    <Group gap={4}>
                      <Badge variant="light" color={a.enabled ? 'teal' : 'gray'}>
                        {a.enabled ? 'Enabled' : 'Disabled'}
                      </Badge>
                      {a.locked && <Badge color="red">Locked</Badge>}
                      {a.mustChangePassword && <Badge color="yellow">Temporary password</Badge>}
                    </Group>
                  </Table.Td>
                  <Table.Td>{formatDateTime(a.lastLoginAt)}</Table.Td>
                  <Table.Td>
                    <Menu position="bottom-end">
                      <Menu.Target>
                        <Button size="compact-sm" variant="subtle">
                          Actions
                        </Button>
                      </Menu.Target>
                      <Menu.Dropdown>
                        <Menu.Item onClick={() => setTempFor(a)}>Issue temporary password</Menu.Item>
                        {a.employee && (
                          <Menu.Item onClick={() => update(a, { role: a.role === 'ADMIN' ? 'EMPLOYEE' : 'ADMIN' })}>
                            Make {a.role === 'ADMIN' ? 'employee' : 'administrator'}
                          </Menu.Item>
                        )}
                        <Menu.Item
                          color={a.enabled ? 'red' : undefined}
                          disabled={a.id === user?.id}
                          onClick={() => update(a, { enabled: !a.enabled })}
                        >
                          {a.enabled ? 'Disable' : 'Enable'}
                        </Menu.Item>
                      </Menu.Dropdown>
                    </Menu>
                  </Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        </Table.ScrollContainer>
      )}
      {creating && <CreateAccountModal onClose={() => setCreating(false)} onDone={refresh} />}
      {tempFor && <TemporaryPasswordModal account={tempFor} onClose={() => setTempFor(null)} onDone={refresh} />}
    </Stack>
  );
}

function CreateAccountModal({ onClose, onDone }: { onClose: () => void; onDone: () => void }) {
  const [search, setSearch] = useState('');
  const employees = useEmployeeOptions(search);
  const [employeeId, setEmployeeId] = useState<string | null>(null);
  const [role, setRole] = useState<string>('EMPLOYEE');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await post('/users', { employeeId, role, temporaryPassword: password });
      notifications.show({ color: 'teal', message: 'Sign-in created. Share the temporary password securely.' });
      onDone();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError && e.fieldErrors.length ? `Temporary password ${e.fieldErrors[0].message}` : errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal opened onClose={onClose} title="Create sign-in">
      <Stack>
        {error && <Alert color="red">{error}</Alert>}
        <Select
          label="Employee"
          searchable
          onSearchChange={setSearch}
          data={(employees.data?.items ?? []).map((e) => ({ value: e.id, label: `${e.fullName} (${e.workEmail})` }))}
          value={employeeId}
          onChange={setEmployeeId}
          description="The username will be their work email."
        />
        <Select
          label="Role"
          data={[
            { value: 'EMPLOYEE', label: 'Employee' },
            { value: 'ADMIN', label: 'Administrator' },
          ]}
          value={role}
          onChange={(v) => setRole(v ?? 'EMPLOYEE')}
        />
        <PasswordInput
          label="Temporary password"
          description={`At least ${PASSWORD_MIN} characters. They must change it at first sign-in.`}
          value={password}
          onChange={(e) => setPassword(e.currentTarget.value)}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={busy} disabled={!employeeId || password.length < PASSWORD_MIN} onClick={submit}>
            Create
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}

function TemporaryPasswordModal({ account, onClose, onDone }: { account: Account; onClose: () => void; onDone: () => void }) {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await post(`/users/${account.id}/temporary-password`, { temporaryPassword: password });
      notifications.show({ color: 'teal', message: 'Temporary password set. Their other sessions were ended.' });
      onDone();
      onClose();
    } catch (e) {
      setError(e instanceof ApiError && e.fieldErrors.length ? `Temporary password ${e.fieldErrors[0].message}` : errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal opened onClose={onClose} title="Issue temporary password">
      <Stack>
        <Text size="sm">
          For <b>{account.username}</b>. This also clears a lockout and signs them out everywhere.
        </Text>
        {error && <Alert color="red">{error}</Alert>}
        <PasswordInput label="Temporary password" value={password} onChange={(e) => setPassword(e.currentTarget.value)} />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={busy} disabled={password.length < PASSWORD_MIN} onClick={submit}>
            Set password
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
