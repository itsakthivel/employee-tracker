import { z } from 'zod';

// Mirrors the server rules in specs/001-employee-tracker/data-model.md.
const optional = (schema: z.ZodString) => schema.optional().or(z.literal(''));
const phone = z
  .string()
  .transform((v) => v.replace(/[\s\-().]/g, ''))
  .refine((v) => v === '' || /^\+\d{7,15}$/.test(v), 'Must start with + and have 7–15 digits');

export const employeeSchema = z
  .object({
    employeeNumber: z
      .string()
      .trim()
      .regex(/^[A-Za-z0-9-]{1,20}$/, '1–20 letters, digits or dashes'),
    fullName: z.string().trim().min(1, 'Required').max(150),
    workEmail: z.string().trim().email('Enter a valid email address'),
    phone,
    jobTitle: z.string().trim().min(1, 'Required').max(100),
    departmentId: z.string().min(1, 'Required'),
    locationId: z.string(),
    managerId: z.string(),
    employmentType: z.enum(['FULL_TIME', 'PART_TIME', 'CONTRACTOR', 'INTERN']),
    status: z.enum(['ACTIVE', 'ON_LEAVE']),
    startDate: z.string().min(1, 'Required'),
    dateOfBirth: z.string(),
    homeAddress: optional(z.string().max(500)),
    emergencyContactName: optional(z.string().max(150)),
    emergencyContactRelationship: optional(z.string().max(50)),
    emergencyContactPhone: phone,
    notes: optional(z.string().max(2000)),
  })
  .superRefine((v, ctx) => {
    if (!v.dateOfBirth) return;
    const dob = new Date(v.dateOfBirth);
    if (dob >= new Date()) {
      ctx.addIssue({ code: 'custom', path: ['dateOfBirth'], message: 'Must be in the past' });
    } else if (v.startDate) {
      const fourteen = new Date(dob);
      fourteen.setFullYear(dob.getFullYear() + 14);
      if (fourteen > new Date(v.startDate)) {
        ctx.addIssue({
          code: 'custom',
          path: ['dateOfBirth'],
          message: 'Employee must be at least 14 on their start date',
        });
      }
    }
  });

export type EmployeeFormValues = z.input<typeof employeeSchema>;

export const sensitiveSchema = z
  .object({
    govIdType: optional(z.string().max(50)),
    govIdNumber: z
      .string()
      .refine((v) => v === '' || /^[A-Za-z0-9-]{4,30}$/.test(v), '4–30 letters, digits or dashes'),
    taxId: z.string().refine((v) => v === '' || (v.length >= 4 && v.length <= 30), '4–30 characters'),
    bankAccountHolder: optional(z.string().max(150)),
    bankName: optional(z.string().max(150)),
    bankAccountNumber: z
      .string()
      .refine((v) => v === '' || (v.length >= 4 && v.length <= 34), '4–34 characters'),
    bankRoutingCode: optional(z.string().max(20)),
  })
  .refine((v) => !v.govIdNumber || !!v.govIdType, {
    path: ['govIdType'],
    message: 'Required when a government ID number is given',
  });

export const compensationSchema = z.object({
  amount: z.string().regex(/^\d{1,12}(\.\d{1,2})?$/, 'A positive amount with up to 2 decimals'),
  currency: z.string().regex(/^[A-Z]{3}$/, '3-letter code, e.g. GBP'),
  payFrequency: z.enum(['ANNUAL', 'MONTHLY', 'HOURLY']),
  effectiveDate: z.string().min(1, 'Required'),
  reason: optional(z.string().max(200)),
});

export const PASSWORD_MIN = 12;
