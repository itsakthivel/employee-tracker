import { useEffect, useState } from 'react';
import { Alert, Button, Card, Group, Loader, Select, SimpleGrid, Stack, Textarea, TextInput, Title } from '@mantine/core';
import { notifications } from '@mantine/notifications';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { ApiError, errorMessage, get, patch, post, type EmployeeDetail } from '../api/client';
import { useDepartments, useEmployeeOptions, useLocations } from '../api/hooks';
import { STATUS_LABEL, TYPE_LABEL } from '../lib/format';
import { employeeSchema, type EmployeeFormValues } from '../lib/schemas';

const EMPTY: EmployeeFormValues = {
  employeeNumber: '',
  fullName: '',
  workEmail: '',
  phone: '',
  jobTitle: '',
  departmentId: '',
  locationId: '',
  managerId: '',
  employmentType: 'FULL_TIME',
  status: 'ACTIVE',
  startDate: '',
  dateOfBirth: '',
  homeAddress: '',
  emergencyContactName: '',
  emergencyContactRelationship: '',
  emergencyContactPhone: '',
  notes: '',
};

const SERVER_FIELD: Record<string, keyof EmployeeFormValues> = {
  'emergencyContact.name': 'emergencyContactName',
  'emergencyContact.relationship': 'emergencyContactRelationship',
  'emergencyContact.phone': 'emergencyContactPhone',
};

function toForm(e: EmployeeDetail): EmployeeFormValues {
  return {
    employeeNumber: e.employeeNumber ?? '',
    fullName: e.fullName,
    workEmail: e.workEmail,
    phone: e.phone ?? '',
    jobTitle: e.jobTitle,
    departmentId: e.department.id,
    locationId: e.location?.id ?? '',
    managerId: e.manager?.id ?? '',
    employmentType: e.employmentType ?? 'FULL_TIME',
    status: e.status === 'ON_LEAVE' ? 'ON_LEAVE' : 'ACTIVE',
    startDate: e.startDate ?? '',
    dateOfBirth: e.dateOfBirth ?? '',
    homeAddress: e.homeAddress ?? '',
    emergencyContactName: e.emergencyContact?.name ?? '',
    emergencyContactRelationship: e.emergencyContact?.relationship ?? '',
    emergencyContactPhone: e.emergencyContact?.phone ?? '',
    notes: e.notes ?? '',
  };
}

const orNull = (v?: string) => (v ? v : null);

/** Admin: add (/employees/new) or edit (/employees/:id/edit) an employee. */
export function EmployeeFormPage() {
  const { id } = useParams();
  const editing = !!id;
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const departments = useDepartments();
  const locations = useLocations();
  const [managerSearch, setManagerSearch] = useState('');
  const managers = useEmployeeOptions(managerSearch);
  const [error, setError] = useState<string | null>(null);
  const [stale, setStale] = useState(false);

  const existing = useQuery({
    queryKey: ['employee', id],
    queryFn: () => get<EmployeeDetail>(`/employees/${id}`),
    enabled: editing,
  });
  const form = useForm<EmployeeFormValues>({ resolver: zodResolver(employeeSchema), defaultValues: EMPTY });
  const { register, control, formState, handleSubmit, reset, setError: setFieldError } = form;

  useEffect(() => {
    if (existing.data) reset(toForm(existing.data));
  }, [existing.data, reset]);

  async function save(values: EmployeeFormValues, addAnother: boolean) {
    setError(null);
    setStale(false);
    const v = employeeSchema.parse(values);
    const contact =
      v.emergencyContactName || v.emergencyContactRelationship || v.emergencyContactPhone
        ? {
            name: orNull(v.emergencyContactName),
            relationship: orNull(v.emergencyContactRelationship),
            phone: orNull(v.emergencyContactPhone),
          }
        : null;
    const common = {
      employeeNumber: v.employeeNumber,
      fullName: v.fullName,
      workEmail: v.workEmail,
      phone: orNull(v.phone),
      jobTitle: v.jobTitle,
      departmentId: v.departmentId,
      locationId: orNull(v.locationId),
      managerId: orNull(v.managerId),
      employmentType: v.employmentType,
      startDate: v.startDate,
      dateOfBirth: orNull(v.dateOfBirth),
      homeAddress: orNull(v.homeAddress),
      emergencyContact: contact,
      notes: orNull(v.notes),
    };
    try {
      const saved = editing
        ? await patch<EmployeeDetail>(`/employees/${id}`, { version: existing.data!.version, ...common })
        : await post<EmployeeDetail>('/employees', { ...common, status: v.status });
      queryClient.invalidateQueries({ queryKey: ['employees'] });
      queryClient.invalidateQueries({ queryKey: ['employee', saved.id] });
      queryClient.invalidateQueries({ queryKey: ['history', saved.id] });
      notifications.show({ color: 'teal', message: `${saved.fullName} saved` });
      if (addAnother) {
        reset({ ...EMPTY, departmentId: v.departmentId, locationId: v.locationId ?? '', startDate: v.startDate });
      } else {
        navigate(`/employees/${saved.id}`);
      }
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'STALE_VERSION') setStale(true);
        else if (e.code === 'DUPLICATE_EMPLOYEE_NUMBER') setFieldError('employeeNumber', { message: e.detail });
        else if (e.code === 'DUPLICATE_EMAIL') setFieldError('workEmail', { message: e.detail });
        else if (e.code === 'REPORTING_CYCLE') setFieldError('managerId', { message: e.detail });
        else if (e.fieldErrors.length) {
          e.fieldErrors.forEach((f) => {
            const name = SERVER_FIELD[f.field] ?? (f.field as keyof EmployeeFormValues);
            setFieldError(name, { message: f.message });
          });
        } else setError(e.detail);
      } else setError(errorMessage(e));
    }
  }

  if (editing && existing.isLoading) return <Loader />;
  if (editing && existing.isError) return <Alert color="red">{errorMessage(existing.error)}</Alert>;

  const err = (name: keyof EmployeeFormValues) => formState.errors[name]?.message;
  const managerOptions = (managers.data?.items ?? [])
    .filter((m) => m.id !== id)
    .map((m) => ({ value: m.id, label: m.fullName }));
  const currentManager = existing.data?.manager;
  if (currentManager && !managerOptions.some((o) => o.value === currentManager.id)) {
    managerOptions.push({ value: currentManager.id, label: currentManager.name });
  }

  return (
    <form noValidate onSubmit={handleSubmit((v) => save(v, false))}>
      <Stack maw={900}>
        <Title order={2}>{editing ? `Edit ${existing.data?.fullName}` : 'Add employee'}</Title>
        {error && <Alert color="red">{error}</Alert>}
        {stale && (
          <Alert color="yellow" title="This record was changed by someone else">
            <Button size="xs" mt="xs" onClick={() => existing.refetch().then(() => setStale(false))}>
              Reload the latest version
            </Button>
          </Alert>
        )}
        <Card withBorder>
          <Title order={4} mb="sm">
            Identity
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <TextInput label="Employee ID" required {...register('employeeNumber')} error={err('employeeNumber')} />
            <TextInput label="Full name" required {...register('fullName')} error={err('fullName')} />
            <TextInput label="Work email" type="email" required {...register('workEmail')} error={err('workEmail')} />
            <TextInput label="Phone" placeholder="+44 20 7946 0000" {...register('phone')} error={err('phone')} />
          </SimpleGrid>
        </Card>
        <Card withBorder>
          <Title order={4} mb="sm">
            Job
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <TextInput label="Job title" required {...register('jobTitle')} error={err('jobTitle')} />
            <Controller
              control={control}
              name="departmentId"
              render={({ field }) => (
                <Select
                  label="Department"
                  required
                  searchable
                  data={(departments.data ?? []).map((d) => ({ value: d.id, label: d.name }))}
                  value={field.value || null}
                  onChange={(v) => field.onChange(v ?? '')}
                  error={err('departmentId')}
                />
              )}
            />
            <Controller
              control={control}
              name="locationId"
              render={({ field }) => (
                <Select
                  label="Location"
                  clearable
                  data={(locations.data ?? []).map((l) => ({ value: l.id, label: l.name }))}
                  value={field.value || null}
                  onChange={(v) => field.onChange(v ?? '')}
                />
              )}
            />
            <Controller
              control={control}
              name="managerId"
              render={({ field }) => (
                <Select
                  label="Manager"
                  clearable
                  searchable
                  onSearchChange={setManagerSearch}
                  data={managerOptions}
                  value={field.value || null}
                  onChange={(v) => field.onChange(v ?? '')}
                  error={err('managerId')}
                />
              )}
            />
            <Controller
              control={control}
              name="employmentType"
              render={({ field }) => (
                <Select
                  label="Employment type"
                  required
                  data={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
                  value={field.value}
                  onChange={(v) => field.onChange(v ?? 'FULL_TIME')}
                />
              )}
            />
            {!editing && (
              <Controller
                control={control}
                name="status"
                render={({ field }) => (
                  <Select
                    label="Status"
                    required
                    data={(['ACTIVE', 'ON_LEAVE'] as const).map((s) => ({ value: s, label: STATUS_LABEL[s] }))}
                    value={field.value}
                    onChange={(v) => field.onChange(v ?? 'ACTIVE')}
                  />
                )}
              />
            )}
            <TextInput label="Start date" type="date" required {...register('startDate')} error={err('startDate')} />
          </SimpleGrid>
        </Card>
        <Card withBorder>
          <Title order={4} mb="sm">
            Personal
          </Title>
          <SimpleGrid cols={{ base: 1, sm: 2 }}>
            <TextInput label="Date of birth" type="date" {...register('dateOfBirth')} error={err('dateOfBirth')} />
            <TextInput label="Home address" {...register('homeAddress')} error={err('homeAddress')} />
            <TextInput label="Emergency contact name" {...register('emergencyContactName')} error={err('emergencyContactName')} />
            <TextInput
              label="Emergency contact relationship"
              {...register('emergencyContactRelationship')}
              error={err('emergencyContactRelationship')}
            />
            <TextInput label="Emergency contact phone" {...register('emergencyContactPhone')} error={err('emergencyContactPhone')} />
          </SimpleGrid>
        </Card>
        <Card withBorder>
          <Textarea label="Notes (administrators only)" autosize minRows={2} {...register('notes')} error={err('notes')} />
        </Card>
        <Group>
          <Button type="submit" loading={formState.isSubmitting}>
            Save
          </Button>
          {!editing && (
            <Button
              variant="light"
              loading={formState.isSubmitting}
              onClick={handleSubmit((v) => save(v, true))}
            >
              Save and add another
            </Button>
          )}
          <Button variant="default" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </Group>
      </Stack>
    </form>
  );
}
