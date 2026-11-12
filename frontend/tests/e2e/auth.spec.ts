import { expect, test } from '@playwright/test';
import { MAILPIT, PASSWORD, signIn, signInAsAdmin, unique } from './helpers';

test('wrong password shows a generic error', async ({ page }) => {
  await signIn(page, 'alice@example.com', 'not-the-password');
  await expect(page.getByText('Invalid username or password')).toBeVisible();
});

test('a disabled (former) employee cannot sign in', async ({ page }) => {
  await signIn(page, 'former@example.com');
  await expect(page.getByText('Invalid username or password')).toBeVisible();
});

test('forgot password sends a working one-time link', async ({ page, request }) => {
  await request.delete(`${MAILPIT}/api/v1/messages`);
  await page.goto('/forgot-password');
  await page.getByLabel('Work email').fill('alice@example.com');
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByText("we've sent a reset link")).toBeVisible();

  const messages = await (await request.get(`${MAILPIT}/api/v1/messages`)).json();
  const message = await (await request.get(`${MAILPIT}/api/v1/message/${messages.messages[0].ID}`)).json();
  const link = /http\S+reset-password\?token=\S+/.exec(message.Text)![0];

  // Reset to the same seeded password so other tests keep working.
  await page.goto(link);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(PASSWORD);
  await page.getByLabel('Confirm new password').fill(PASSWORD);
  await page.getByRole('button', { name: 'Set password' }).click();
  await expect(page.getByText('Your password has been changed')).toBeVisible();

  await page.goto(link);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(`${PASSWORD}x`);
  await page.getByLabel('Confirm new password').fill(`${PASSWORD}x`);
  await page.getByRole('button', { name: 'Set password' }).click();
  await expect(page.getByText('This link is invalid or has expired')).toBeVisible();
});

test('a temporary password must be changed before anything else', async ({ page }) => {
  // Admin issues a temporary password for the seeded new hire.
  await signInAsAdmin(page);
  await page.goto('/users');
  await page.getByPlaceholder('Search accounts').fill('newhire');
  await page.getByRole('button', { name: 'Actions' }).click();
  await page.getByRole('menuitem', { name: 'Issue temporary password' }).click();
  const temp = `Temp-${unique('pw')}-2026`;
  await page.getByRole('textbox', { name: 'Temporary password' }).fill(temp);
  await page.getByRole('button', { name: 'Set password' }).click();
  await page.getByRole('button', { name: 'Sign out' }).click();

  await signIn(page, 'newhire@example.com', temp);
  await expect(page.getByRole('heading', { name: 'Choose a new password' })).toBeVisible();
  await page.goto('/');
  await expect(page).toHaveURL(/change-password/);
  await page.getByRole('textbox', { name: 'Temporary password' }).fill(temp);
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill('Password1234');
  await page.getByLabel('Confirm new password').fill('Password1234');
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByText(/too common/)).toBeVisible();
  const mine = `Mine-${unique('pw')}-2026`;
  await page.getByRole('textbox', { name: 'New password', exact: true }).fill(mine);
  await page.getByLabel('Confirm new password').fill(mine);
  await page.getByRole('button', { name: 'Change password' }).click();
  await expect(page.getByRole('heading', { name: 'Directory' })).toBeVisible();
});
