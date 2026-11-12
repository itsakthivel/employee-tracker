import { expect, test } from '@playwright/test';
import { signIn, signInAsAdmin } from './helpers';

test('US4: overview totals and CSV export', async ({ page }) => {
  await signInAsAdmin(page);
  await page.getByRole('link', { name: 'Overview' }).click();
  await expect(page.getByText('Current headcount (active + on leave)')).toBeVisible();
  const salesCount = await page.getByRole('row', { name: /^Sales/ }).getByRole('link').innerText();
  await page.getByRole('row', { name: /^Sales/ }).getByRole('link').click();
  await expect(page.getByText(new RegExp(`^${salesCount} employees?$`))).toBeVisible();

  const download = page.waitForEvent('download');
  await page.getByRole('link', { name: 'Export CSV' }).click();
  const file = await download;
  expect(file.suggestedFilename()).toMatch(/^employees-\d{4}-\d{2}-\d{2}\.csv$/);
  const text = (await (await file.createReadStream()).toArray()).join('');
  const lines = text.trim().split('\r\n');
  expect(lines.length - 1).toBe(Number(salesCount));
  expect(lines[0]).not.toMatch(/bank|salary|tax/i);
});

test('US4: employees cannot open reports', async ({ page }) => {
  await signIn(page, 'alice@example.com');
  await expect(page.getByRole('heading', { name: 'Directory' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Overview' })).toHaveCount(0);
  const res = await page.request.get('/api/v1/reports/headcount');
  expect(res.status()).toBe(403);
  const csv = await page.request.get('/api/v1/employees/export');
  expect(csv.status()).toBe(403);
});
