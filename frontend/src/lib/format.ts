export const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Active',
  ON_LEAVE: 'On leave',
  TERMINATED: 'Terminated',
};

export const STATUS_COLOR: Record<string, string> = {
  ACTIVE: 'teal',
  ON_LEAVE: 'yellow',
  TERMINATED: 'gray',
};

export const TYPE_LABEL: Record<string, string> = {
  FULL_TIME: 'Full-time',
  PART_TIME: 'Part-time',
  CONTRACTOR: 'Contractor',
  INTERN: 'Intern',
};

export function formatDate(value?: string | null): string {
  if (!value) return '—';
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
}

export function formatDateTime(value?: string | null): string {
  if (!value) return '—';
  return new Date(value).toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export const FIELD_LABEL: Record<string, string> = {
  employeeNumber: 'Employee ID',
  fullName: 'Full name',
  workEmail: 'Work email',
  phone: 'Phone',
  jobTitle: 'Job title',
  department: 'Department',
  location: 'Location',
  manager: 'Manager',
  employmentType: 'Employment type',
  status: 'Status',
  startDate: 'Start date',
  endDate: 'End date',
  dateOfBirth: 'Date of birth',
  homeAddress: 'Home address',
  emergencyContactName: 'Emergency contact name',
  emergencyContactRelationship: 'Emergency contact relationship',
  emergencyContactPhone: 'Emergency contact phone',
  notes: 'Notes',
  govIdType: 'Government ID type',
  govIdNumber: 'Government ID number',
  taxId: 'Tax ID',
  bankAccountHolder: 'Account holder',
  bankName: 'Bank name',
  bankAccountNumber: 'Account number',
  bankRoutingCode: 'Routing code',
  compensation: 'Compensation',
};
