import { useState } from 'react';
import { Alert, Anchor, Button, Group, List, Modal, Select, Stack, TextInput } from '@mantine/core';
import { Link } from 'react-router-dom';
import { ApiError, errorMessage, post, type EmployeeDetail, type Ref } from '../api/client';
import { STATUS_LABEL } from '../lib/format';

/** Admin: Active ↔ On leave, or Terminate (with the direct-reports warning, FR-014). */
export function StatusDialog({
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
  const choices = (['ACTIVE', 'ON_LEAVE', 'TERMINATED'] as const).filter((s) => s !== employee.status);
  const [status, setStatus] = useState<string>(choices[0]);
  const [endDate, setEndDate] = useState('');
  const [reports, setReports] = useState<Ref[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const terminating = status === 'TERMINATED';
  const endTooEarly = terminating && !!endDate && !!employee.startDate && endDate < employee.startDate;

  async function submit(acknowledge = false) {
    setBusy(true);
    setError(null);
    try {
      await post(`/employees/${employee.id}/status`, {
        version: employee.version,
        status,
        endDate: terminating ? endDate : undefined,
        acknowledgeDirectReports: acknowledge || undefined,
      });
      setReports(null);
      onDone();
      onClose();
    } catch (e) {
      if (e instanceof ApiError && e.code === 'DIRECT_REPORTS') {
        setReports((e.body.directReports as Ref[]) ?? []);
      } else if (e instanceof ApiError && e.code === 'STALE_VERSION') {
        setError('This record was changed by someone else. Close this dialog and reload.');
      } else {
        setError(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal opened={opened} onClose={onClose} title="Change status">
      <Stack>
        {error && <Alert color="red">{error}</Alert>}
        <Select
          label="New status"
          data={choices.map((s) => ({ value: s, label: STATUS_LABEL[s] }))}
          value={status}
          onChange={(v) => {
            setStatus(v ?? choices[0]);
            setReports(null);
          }}
        />
        {terminating && (
          <TextInput
            type="date"
            label="End date"
            required
            value={endDate}
            min={employee.startDate}
            onChange={(e) => setEndDate(e.currentTarget.value)}
            error={endTooEarly ? 'Must be on or after the start date' : undefined}
          />
        )}
        {terminating && (
          <Alert color="gray">Terminating ends this person's sign-in immediately.</Alert>
        )}
        {reports && (
          <Alert color="yellow" title={`${reports.length} direct report(s) will need a new manager`}>
            <List size="sm">
              {reports.map((r) => (
                <List.Item key={r.id}>
                  <Anchor component={Link} to={`/employees/${r.id}`} target="_blank">
                    {r.name}
                  </Anchor>
                </List.Item>
              ))}
            </List>
            They keep their current manager until you change it.
          </Alert>
        )}
        <Group justify="flex-end">
          <Button variant="default" onClick={onClose}>
            Cancel
          </Button>
          {reports ? (
            <Button color="red" loading={busy} onClick={() => submit(true)}>
              Terminate anyway
            </Button>
          ) : (
            <Button
              color={terminating ? 'red' : undefined}
              loading={busy}
              disabled={terminating && (!endDate || endTooEarly)}
              onClick={() => submit()}
            >
              {terminating ? 'Terminate' : 'Save'}
            </Button>
          )}
        </Group>
      </Stack>
    </Modal>
  );
}
