import { expect, test } from '@playwright/test';
import { addEmployee, signInAsAdmin, unique } from './helpers';

test.beforeEach(async ({ page }) => signInAsAdmin(page));

test('US1: add an employee, edit the title, see history', async ({ page }) => {
  const name = `Ada ${unique('L')}`;
  await addEmployee(page, name);
  await expect(page.getByText('Engineer · Engineering')).toBeVisible();
  await expect(page.getByText('Created')).toBeVisible();

  await page.getByRole('link', { name: 'Edit' }).click();
  await page.getByLabel('Job title').fill('Lead Engineer');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Lead Engineer · Engineering')).toBeVisible();
  await expect(page.getByText('Engineer → Lead Engineer')).toBeVisible();
});

test('US1: per-field errors and duplicate employee ID', async ({ page }) => {
  await page.goto('/employees/new');
  await page.getByLabel('Work email').fill('not-an-email');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Enter a valid email address')).toBeVisible();
  await expect(page.getByText('1–20 letters, digits or dashes')).toBeVisible();

  await page.getByLabel('Employee ID').fill('E-1001');
  await page.getByLabel('Full name').fill('Duplicate Person');
  await page.getByLabel('Work email').fill(`${unique('dup')}@example.com`);
  await page.getByLabel('Job title').fill('Engineer');
  await page.getByRole('textbox', { name: 'Department' }).click();
  await page.getByRole('option', { name: 'Sales' }).click();
  await page.getByLabel('Start date').fill('2023-03-01');
  await page.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Employee ID already in use')).toBeVisible();
});

test('US1: sensitive data is masked, reveal is logged', async ({ page }) => {
  const name = `Sensitive ${unique('S')}`;
  await addEmployee(page, name);
  await page.getByRole('button', { name: 'Edit', exact: true }).click();
  const modal = page.getByRole('dialog', { name: 'Edit sensitive data' });
  await modal.getByLabel('Government ID type').fill('Passport');
  await modal.getByLabel('Government ID number').fill('P12344821');
  await modal.getByLabel('Account number').fill('GB29NWBK60161331926819');
  await modal.getByRole('button', { name: 'Save' }).click();

  await expect(page.getByTestId('sensitive-bankAccountNumber')).toHaveText('••••6819');
  await expect(page.getByText('GB29NWBK60161331926819')).toHaveCount(0);
  await page.getByRole('button', { name: 'Reveal' }).nth(1).click();
  await expect(page.getByTestId('sensitive-bankAccountNumber')).toHaveText('GB29NWBK60161331926819');

  await page.getByRole('link', { name: 'Sensitive access log' }).click();
  await expect(page.getByRole('row', { name: new RegExp(`reveal.*Account number.*${name}`, 'i') })).toBeVisible();
});
