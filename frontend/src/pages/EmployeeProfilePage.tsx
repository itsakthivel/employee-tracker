import { useState } from 'react';
import { Alert, Anchor, Badge, Button, Card, Group, Loader, SimpleGrid, Stack, Text, Title } from '@mantine/core';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { ApiError, errorMessage, get, type EmployeeDetail } from '../api/client';
import { HistoryList } from '../components/HistoryList';
import { SensitivePanel } from '../components/SensitivePanel';
import { CompensationTable } from '../components/CompensationTable';
import { StatusDialog } from '../components/StatusDialog';
import { ReactivateDialog } from '../components/ReactivateDialog';
import { formatDate, STATUS_COLOR, STATUS_LABEL, TYPE_LABEL } from '../lib/format';
import { NotFoundPage } from './NotFoundPage';

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <Text size="xs" c="dimmed">
        {label}
      </Text>
      <Text component="div">{children || '—'}</Text>
    </div>
  );
}

export function EmployeeProfilePage() {
  const { id = '' } = useParams();
  const queryClient = useQueryClient();
  const [statusOpen, setStatusOpen] = useState(false);
  const [reactivateOpen, setReactivateOpen] = useState(false);
  const employee = useQuery({
    queryKey: ['employee', id],
    queryFn: () => get<EmployeeDetail>(`/employees/${id}`),
  });

  if (employee.isLoading) return <Loader />;
  if (employee.error instanceof ApiError && employee.error.status === 404) return <NotFoundPage what="Employee" />;
  if (employee.isError) return <Alert color="red">{errorMessage(employee.error)}</Alert>;
  const e = employee.data!;
  const admin = e.view === 'ADMIN';
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['employee', id] });
    queryClient.invalidateQueries({ queryKey: ['history', id] });
    queryClient.invalidateQueries({ queryKey: ['employees'] });
  };

  return (
    <Stack>
      <Group justify="space-between" align="flex-start">
        <div>
          <Title order={2}>{e.fullName}</Title>
          <Text c="dimmed">
            {e.jobTitle} · {e.department.name}
          </Text>
          <Group gap="xs" mt={4}>
            {e.status && (
              <Badge color={STATUS_COLOR[e.status]} variant="light">
                {STATUS_LABEL[e.status]}
                {e.endDate ? ` since ${formatDate(e.endDate)}` : ''}
              </Badge>
            )}
            {e.isPreHire && <Badge variant="light">Starting on {formatDate(e.startDate)}</Badge>}
          </Group>
        </div>
        {admin && (
          <Group>
            <Button variant="default" component={Link} to={`/employees/${e.id}/edit`}>
              Edit
            </Button>
            {e.status === 'TERMINATED' ? (
              <Button onClick={() => setReactivateOpen(true)}>Reactivate</Button>
            ) : (
              <Button variant="light" onClick={() => setStatusOpen(true)}>
                Change status
              </Button>
            )}
          </Group>
        )}
        {e.view === 'SELF' && (
          <Button variant="light" component={Link} to="/me">
            Edit my contact details
          </Button>
        )}
      </Group>

      <Card withBorder>
        <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }}>
          <Field label="Work email">
            <Anchor href={`mailto:${e.workEmail}`}>{e.workEmail}</Anchor>
          </Field>
          <Field label="Phone">{e.phone}</Field>
          <Field label="Location">{e.location?.name}</Field>
          <Field label="Manager">
            {e.manager && (
              <Anchor component={Link} to={`/employees/${e.manager.id}`}>
                {e.manager.name}
              </Anchor>
            )}
          </Field>
          {e.employeeNumber && <Field label="Employee ID">{e.employeeNumber}</Field>}
          {e.employmentType && <Field label="Employment type">{TYPE_LABEL[e.employmentType]}</Field>}
          {e.startDate && <Field label="Start date">{formatDate(e.startDate)}</Field>}
        </SimpleGrid>
      </Card>

      {(admin || e.view === 'SELF') && (
        <Card withBorder>
          <Title order={4} mb="sm">
            Personal details
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }}>
            <Field label="Date of birth">{formatDate(e.dateOfBirth)}</Field>
            <Field label="Home address">{e.homeAddress}</Field>
            <Field label="Emergency contact">
              {e.emergencyContact &&
                [e.emergencyContact.name, e.emergencyContact.relationship, e.emergencyContact.phone]
                  .filter(Boolean)
                  .join(' · ')}
            </Field>
            {admin && <Field label="Notes">{e.notes}</Field>}
          </SimpleGrid>
        </Card>
      )}

      <Card withBorder>
        <Title order={4} mb="sm">
          Direct reports
        </Title>
        {e.directReports.length === 0 ? (
          <Text c="dimmed">None</Text>
        ) : (
          <Group gap="sm">
            {e.directReports.map((r) => (
              <Anchor key={r.id} component={Link} to={`/employees/${r.id}`}>
                {r.name}
              </Anchor>
            ))}
          </Group>
        )}
      </Card>

      {admin && <SensitivePanel employeeId={e.id} />}
      {admin && <CompensationTable employeeId={e.id} />}

      {(admin || e.view === 'SELF') && (
        <Card withBorder>
          <Title order={4} mb="sm">
            Change history
          </Title>
          <HistoryList employeeId={e.id} />
        </Card>
      )}

      {admin && statusOpen && (
        <StatusDialog employee={e} opened onClose={() => setStatusOpen(false)} onDone={refresh} />
      )}
      {admin && reactivateOpen && (
        <ReactivateDialog employee={e} opened onClose={() => setReactivateOpen(false)} onDone={refresh} />
      )}
    </Stack>
  );
}
