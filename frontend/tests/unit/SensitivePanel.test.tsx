import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SensitivePanel } from '../../src/components/SensitivePanel';
import { mockApi, renderWithProviders } from './render';

const ID = '11111111-1111-1111-1111-111111111111';

describe('SensitivePanel', () => {
  it('shows masked values until Reveal, and does not cache the revealed value', async () => {
    const calls = mockApi({
      [`GET /employees/${ID}/sensitive`]: {
        version: 1,
        govIdType: 'Passport',
        govIdNumber: '••••4821',
        bankAccountNumber: '••••6819',
        bankName: 'First Bank',
      },
      [`POST /employees/${ID}/sensitive/reveal`]: { field: 'bankAccountNumber', value: 'GB29NWBK60161331926819' },
    });
    const { client, unmount } = renderWithProviders(<SensitivePanel employeeId={ID} />);

    expect(await screen.findByTestId('sensitive-bankAccountNumber')).toHaveTextContent('••••6819');
    expect(screen.queryByText('GB29NWBK60161331926819')).not.toBeInTheDocument();

    const buttons = screen.getAllByRole('button', { name: 'Reveal' });
    await userEvent.click(buttons[1]);
    await waitFor(() =>
      expect(screen.getByTestId('sensitive-bankAccountNumber')).toHaveTextContent('GB29NWBK60161331926819'),
    );
    expect(calls).toContain(`POST /employees/${ID}/sensitive/reveal`);
    expect(screen.getByTestId('sensitive-govIdNumber')).toHaveTextContent('••••4821');

    unmount();
    const cached = JSON.stringify(client.getQueryCache().getAll().map((q) => q.state.data));
    expect(cached).not.toContain('GB29NWBK60161331926819');
  });
});
