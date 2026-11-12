import { expect, test } from '@playwright/test';
import { addEmployee, signIn, signInAsAdmin, unique } from './helpers';

test('US3: leave, terminate (ends their session), reactivate', async ({ page, browser }) => {
  await signInAsAdmin(page);
  const name = `Leaver ${unique('T')}`;
  const { email } = await addEmployee(page, name);

  // Give them a sign-in and sign them in from another browser.
  await page.goto('/users');
  await page.getByRole('button', { name: 'Create sign-in' }).click();
  await page.getByRole('textbox', { name: 'Employee' }).fill(name);
  await page.getByRole('option', { name: new RegExp(name) }).click();
  await page.getByLabel('Temporary password').fill('Temporary-Pass-2026');
  await page.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByText('Sign-in created')).toBeVisible();
  const other = await browser.newContext();
  const theirs = await other.newPage();
  await signIn(theirs, email, 'Temporary-Pass-2026');
  await expect(theirs.getByRole('heading', { name: 'Choose a new password' })).toBeVisible();

  await page.goto('/');
  await page.getByLabel('Search').fill(name);
  await page.getByRole('link', { name }).click();
  await page.getByRole('button', { name: 'Change status' }).click();
  await page.getByRole('textbox', { name: 'New status' }).click();
  await page.getByRole('option', { name: 'On leave' }).click();
  await page.getByRole('button', { name: 'Save' }).click();
  await expect(page.getByText('On leave', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Change status' }).click();
  await page.getByRole('textbox', { name: 'New status' }).click();
  await page.getByRole('option', { name: 'Terminated' }).click();
  await page.getByLabel('End date').fill('2025-01-31');
  await page.getByRole('button', { name: 'Terminate' }).click();
  await expect(page.getByText(/Terminated since/)).toBeVisible();

  await theirs.reload();
  await expect(theirs.getByRole('heading', { name: 'Sign in' })).toBeVisible();
  await other.close();

  await page.getByRole('button', { name: 'Reactivate' }).click();
  await page.getByLabel('New start date').fill('2025-03-01');
  await page.getByRole('dialog').getByRole('button', { name: 'Reactivate' }).click();
  await expect(page.getByText('Active', { exact: true })).toBeVisible();
});

test('US3: terminating a manager warns about direct reports', async ({ page }) => {
  await signInAsAdmin(page);
  await page.getByLabel('Search').fill('Head of Engineering');
  await page.goto('/');
  // Seeded department heads have direct reports; open the first "Head of" profile.
  await page.getByRole('cell', { name: /^Head of/ }).first().locator('xpath=..').getByRole('link').first().click();
  await page.getByRole('button', { name: 'Change status' }).click();
  await page.getByRole('textbox', { name: 'New status' }).click();
  await page.getByRole('option', { name: 'Terminated' }).click();
  await page.getByLabel('End date').fill('2025-12-31');
  await page.getByRole('button', { name: 'Terminate' }).click();
  await expect(page.getByText(/direct report\(s\) will need a new manager/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Terminate anyway' })).toBeVisible();
  await page.getByRole('button', { name: 'Cancel' }).click();
});
