import { expect, type Page } from '@playwright/test';

export const PASSWORD = 'ChangeMe-12345';
export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

export async function signIn(page: Page, email: string, password = PASSWORD) {
  await page.goto('/sign-in');
  await page.getByLabel('Work email').fill(email);
  await page.getByRole('textbox', { name: 'Password', exact: true }).fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
}

export async function signInAsAdmin(page: Page) {
  await signIn(page, 'admin@example.com');
  await expect(page.getByRole('heading', { name: 'Directory' })).toBeVisible();
}

export function unique(prefix: string) {
  return `${prefix}${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
}

/** Creates an employee through the UI and lands on their profile. */
export async function addEmployee(page: Page, name: string, extra: { email?: string; dept?: string } = {}) {
  const tag = unique('E');
  const email = extra.email ?? `${tag.toLowerCase()}@example.com`;
  await page.goto('/employees/new');
  await page.getByLabel('Employee ID').fill(tag.slice(0, 20));
  await page.getByLabel('Full name').fill(name);
  await page.getByLabel('Work email').fill(email);
  await page.getByLabel('Job title').fill('Engineer');
  await page.getByRole('textbox', { name: 'Department' }).click();
  await page.getByRole('option', { name: extra.dept ?? 'Engineering' }).click();
  await page.getByLabel('Start date').fill('2023-03-01');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByRole('heading', { name })).toBeVisible();
  return { email, id: page.url().split('/').pop()! };
}
