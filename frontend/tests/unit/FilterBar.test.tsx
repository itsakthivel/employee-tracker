import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation, useSearchParams } from 'react-router-dom';
import { FilterBar } from '../../src/components/FilterBar';
import { mockApi, renderWithProviders } from './render';

function Harness({ isAdmin }: { isAdmin: boolean }) {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  return (
    <>
      <FilterBar params={params} setParams={setParams} isAdmin={isAdmin} />
      <output data-testid="url">{location.search}</output>
    </>
  );
}

function setup(isAdmin: boolean, route = '/') {
  mockApi({
    'GET /departments': [{ id: 'd1', name: 'Engineering' }],
    'GET /locations': [{ id: 'l1', name: 'Remote' }],
    'GET /employees': { items: [], page: 0, size: 20, totalItems: 0, totalPages: 0 },
  });
  return renderWithProviders(
    <Routes>
      <Route path="/" element={<Harness isAdmin={isAdmin} />} />
    </Routes>,
    { route },
  );
}

describe('FilterBar', () => {
  it('keeps the search in the URL after a short pause', async () => {
    setup(false);
    await userEvent.type(screen.getByLabelText('Search'), 'grace');
    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent('?q=grace'), { timeout: 2000 });
  });

  it('hides admin-only filters from employees', () => {
    setup(false);
    expect(screen.queryByText('Include former employees')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Status')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Search')).toHaveAttribute('placeholder', 'Name or email');
  });

  it('shows admin filters and clears everything', async () => {
    setup(true, '/?q=ada&includeTerminated=true');
    expect(screen.getByText('Include former employees')).toBeInTheDocument();
    expect(screen.getByLabelText('Search')).toHaveValue('ada');
    await userEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    await waitFor(() => expect(screen.getByTestId('url')).toHaveTextContent(''));
    expect(screen.getByLabelText('Search')).toHaveValue('');
  });
});
