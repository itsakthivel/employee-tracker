import { useEffect, useState, type FormEvent } from 'react';
import { Alert, Anchor, Button, Card, Group, Loader, SimpleGrid, Stack, Text, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { ApiError, errorMessage, get, patch, type EmployeeDetail } from '../api/client';
import { HistoryList } from '../components/HistoryList';
import { formatDate, TYPE_LABEL } from '../lib/format';
import { Field } from './EmployeeProfilePage';

/** The signed-in employee's own record. Only phone and emergency contact are editable. */
export function MyProfilePage() {
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ['my-profile'], queryFn: () => get<EmployeeDetail>('/me/profile') });
  const [phone, setPhone] = useState('');
  const [contact, setContact] = useState({ name: '', relationship: '', phone: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (me.data) {
      setPhone(me.data.phone ?? '');
      setContact({
        name: me.data.emergencyContact?.name ?? '',
        relationship: me.data.emergencyContact?.relationship ?? '',
        phone: me.data.emergencyContact?.phone ?? '',
      });
    }
  }, [me.data]);

  if (me.isLoading) return <Loader />;
  if (me.error instanceof ApiError && me.error.status === 404) {
    return (
      <Stack>
        <Title order={2}>My profile</Title>
        <Text c="dimmed">Your sign-in isn't linked to an employee record.</Text>
      </Stack>
    );
  }
  if (me.isError) return <Alert color="red">{errorMessage(me.error)}</Alert>;
  const e = me.data!;

  async function save(ev: FormEvent) {
    ev.preventDefault();
    setBusy(true);
    setErrors({});
    setError(null);
    const anyContact = contact.name || contact.relationship || contact.phone;
    try {
      await patch('/me/profile', {
        version: e.version,
        phone: phone || null,
        emergencyContact: anyContact
          ? { name: contact.name || null, relationship: contact.relationship || null, phone: contact.phone || null }
          : null,
      });
      await queryClient.invalidateQueries({ queryKey: ['my-profile'] });
      queryClient.invalidateQueries({ queryKey: ['history', e.id] });
      notifications.show({ color: 'teal', message: 'Your details were saved' });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'STALE_VERSION') {
        setError('Your record was changed elsewhere. Reload the page and try again.');
      } else if (err instanceof ApiError && err.fieldErrors.length) {
        setErrors(Object.fromEntries(err.fieldErrors.map((f) => [f.field, f.message])));
      } else setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Stack maw={900}>
      <Title order={2}>My profile</Title>
      <Card withBorder>
        <SimpleGrid cols={{ base: 1, sm: 2, md: 3 }}>
          <Field label="Name">{e.fullName}</Field>
          <Field label="Work email">{e.workEmail}</Field>
          <Field label="Job title">{e.jobTitle}</Field>
          <Field label="Department">{e.department.name}</Field>
          <Field label="Location">{e.location?.name}</Field>
          <Field label="Manager">
            {e.manager && (
              <Anchor component={Link} to={`/employees/${e.manager.id}`}>
                {e.manager.name}
              </Anchor>
            )}
          </Field>
          <Field label="Employee ID">{e.employeeNumber}</Field>
          <Field label="Employment type">{e.employmentType && TYPE_LABEL[e.employmentType]}</Field>
          <Field label="Start date">{formatDate(e.startDate)}</Field>
          <Field label="Date of birth">{formatDate(e.dateOfBirth)}</Field>
          <Field label="Home address">{e.homeAddress}</Field>
        </SimpleGrid>
        <Text size="xs" c="dimmed" mt="sm">
          To change anything above, contact HR.
        </Text>
      </Card>
      <Card withBorder>
        <form onSubmit={save}>
          <Stack>
            <Title order={4}>Contact details you can change</Title>
            {error && <Alert color="red">{error}</Alert>}
            <SimpleGrid cols={{ base: 1, sm: 2 }}>
              <TextInput label="Phone" value={phone} onChange={(ev) => setPhone(ev.currentTarget.value)} error={errors.phone} />
              <TextInput
                label="Emergency contact name"
                value={contact.name}
                onChange={(ev) => setContact({ ...contact, name: ev.currentTarget.value })}
                error={errors['emergencyContact.name']}
              />
              <TextInput
                label="Relationship"
                value={contact.relationship}
                onChange={(ev) => setContact({ ...contact, relationship: ev.currentTarget.value })}
                error={errors['emergencyContact.relationship']}
              />
              <TextInput
                label="Emergency contact phone"
                value={contact.phone}
                onChange={(ev) => setContact({ ...contact, phone: ev.currentTarget.value })}
                error={errors['emergencyContact.phone']}
              />
            </SimpleGrid>
            <Group>
              <Button type="submit" loading={busy}>
                Save
              </Button>
              <Anchor component={Link} to="/change-password" size="sm">
                Change my password
              </Anchor>
            </Group>
          </Stack>
        </form>
      </Card>
      <Card withBorder>
        <Title order={4} mb="sm">
          My change history
        </Title>
        <HistoryList employeeId={e.id} />
      </Card>
    </Stack>
  );
}
