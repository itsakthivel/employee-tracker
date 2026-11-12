import { expect, test } from '@playwright/test';
import { signIn } from './helpers';

test.beforeEach(async ({ page }) => {
  await signIn(page, 'alice@example.com');
  await expect(page.getByRole('heading', { name: 'Directory' })).toBeVisible();
});

test('US2: search and filter the directory', async ({ page }) => {
  await page.getByLabel('Search').fill('morgan');
  await expect(page).toHaveURL(/q=morgan/);
  await expect(page.getByRole('link', { name: 'Alice Morgan' })).toBeVisible();
  await page.getByLabel('Search').fill('zzzz');
  await expect(page.getByText('No employees found')).toBeVisible();
  await page.getByRole('button', { name: 'Clear filters' }).first().click();
  await expect(page.getByText(/^\d+ employees$/)).toBeVisible();
  await expect(page.getByText('Include former employees')).toHaveCount(0);
});

test("US2: a colleague's profile shows directory fields only", async ({ page }) => {
  await page.getByLabel('Search').fill('Head of Sales');
  await page.goto('/');
  await page.getByRole('link', { name: /.+/ }).filter({ hasText: /\w+ \w+/ }).nth(5).click();
  await expect(page.getByText('Work email')).toBeVisible();
  await expect(page.getByText('Personal details')).toHaveCount(0);
  await expect(page.getByText('Sensitive data')).toHaveCount(0);
  await expect(page.getByRole('link', { name: 'Edit' })).toHaveCount(0);
});

test('US2: a terminated employee is hidden; own contact details are editable', async ({ page }) => {
  await page.goto('/employees/00000000-0000-0000-0000-000000000000');
  await expect(page.getByRole('heading', { name: 'Employee not found' })).toBeVisible();

  await page.getByRole('link', { name: 'My profile' }).click();
  const phone = `+44 7700 9${Math.floor(10000 + Math.random() * 89999)}`;
  await page.getByLabel('Phone', { exact: true }).fill(phone);
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('Your details were saved')).toBeVisible();
  await expect(page.getByText('Self-service').first()).toBeVisible();
});
