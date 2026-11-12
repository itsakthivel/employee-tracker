import { useState } from 'react';
import { Alert, Button, Group, Modal, Select, Stack, TextInput } from '@mantine/core';
import { errorMessage, post, type EmployeeDetail } from '../api/client';
import { useDepartments, useEmployeeOptions } from '../api/hooks';

/** Admin: rehire a terminated employee (FR-015). Their sign-in comes back with a password change. */
export function ReactivateDialog({
  employee,
  opened,
  onClose,
  onDone,
}: {
  employee: EmployeeDetail;
  opened: boolean;
  onClose: () => void;
  onDone: () => void;
}) {
  const departments = useDepartments();
  const [search, setSearch] = useState('');
  const managers = useEmployeeOptions(search);
  const [startDate, setStartDate] = useState('');
  const [jobTitle, setJobTitle] = useState(employee.jobTitle);
  const [departmentId, setDepartmentId] = useState<string | null>(employee.department.id);
  const [managerId, setManagerId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const tooEarly = !!startDate && !!employee.endDate && startDate < employee.endDate;

  async function submit() {
    setBusy(true);
    setError(null);
    try {
      await post(`/employees/${employee.id}/reactivate`, {
        version: employee.version,
        startDate,
        jobTitle,
        departmentId,
        managerId,
      });
      onDone();
      onClose();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal opened={opened} onClose={onClose} title="Reactivate (rehire)">
      <Stack>
        {error && <Alert color="red">{error}</Alert>}
        <TextInput
          type="date"
          label="New start date"
          required
          min={employee.endDate ?? undefined}
          value={startDate}
          onChange={(e) => setStartDate(e.currentTarget.value)}
          error={tooEarly ? 'Must be on or after the previous end date' : undefined}
        />
        <TextInput label="Job title" value={jobTitle} onChange={(e) => setJobTitle(e.currentTarget.value)} />
        <Select
          label="Department"
          data={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
          value={departmentId}
          onChange={setDepartmentId}
        />
        <Select
          label="Manager"
          searchable
          clearable
          onSearchChange={setSearch}
          data={(managers.data?.items ?? [])
            .filter((m) => m.id !== employee.id)
            .map((m) => ({ value: m.id, label: m.fullName }))}
          value={managerId}
          onChange={setManagerId}
        />
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={busy} disabled={!startDate || tooEarly || !jobTitle.trim()} onClick={submit}>
            Reactivate
          </Button>
        </Group>
      </Stack>
    </Modal>
  );
}
