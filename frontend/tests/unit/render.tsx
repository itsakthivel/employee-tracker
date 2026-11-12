import type { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { MantineProvider } from '@mantine/core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';

export function renderWithProviders(ui: ReactNode, { route = '/' } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    client,
    ...render(
      <MantineProvider>
        <QueryClientProvider client={client}>
          <MemoryRouter initialEntries={[route]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>{ui}</MemoryRouter>
        </QueryClientProvider>
      </MantineProvider>,
    ),
  };
}

/** Routes fetch calls to canned JSON responses by "METHOD path" (path without /api/v1). */
export function mockApi(routes: Record<string, unknown>) {
  const calls: string[] = [];
  const fetchMock = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    const key = `${init?.method ?? 'GET'} ${url.pathname.replace('/api/v1', '')}`;
    calls.push(`${key}${url.search}`);
    const match = Object.keys(routes).find((k) => k === key);
    if (!match) return new Response(JSON.stringify({ detail: 'not mocked' }), { status: 500 });
    return new Response(JSON.stringify(routes[match]), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  };
  globalThis.fetch = fetchMock as typeof fetch;
  return calls;
}
