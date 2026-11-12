import type { components } from './schema';

export type Schemas = components['schemas'];
export type EmployeeDetail = Schemas['EmployeeDetail'];
export type EmployeeSummary = Schemas['EmployeeSummary'];
export type EmployeePage = Schemas['EmployeePage'];
export type Ref = Schemas['Ref'];
export type CurrentUser = Schemas['CurrentUser'];

export interface FieldError {
  field: string;
  message: string;
}

/** A Problem Details response from the API. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    readonly detail: string,
    readonly fieldErrors: FieldError[] = [],
    readonly body: Record<string, unknown> = {},
  ) {
    super(detail);
  }
}

export const SESSION_EXPIRED = 'session-expired';

function csrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : undefined;
}

export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const token = csrfToken();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  const res = await fetch(`/api/v1${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204 || res.status === 202) return undefined as T;
  const text = await res.text();
  const json = text ? JSON.parse(text) : {};
  if (!res.ok) {
    if (res.status === 401 && !path.startsWith('/auth/')) {
      window.dispatchEvent(new Event(SESSION_EXPIRED));
    }
    throw new ApiError(
      res.status,
      json.code,
      json.detail ?? res.statusText,
      json.fieldErrors ?? [],
      json,
    );
  }
  return json as T;
}

export const get = <T>(path: string) => api<T>('GET', path);
export const post = <T>(path: string, body?: unknown) => api<T>('POST', path, body ?? {});
export const put = <T>(path: string, body: unknown) => api<T>('PUT', path, body);
export const patch = <T>(path: string, body: unknown) => api<T>('PATCH', path, body);
export const del = <T>(path: string) => api<T>('DELETE', path);

/** Builds a query string, repeating array values (?a=1&a=2) and skipping empty ones. */
export function query(params: Record<string, string | string[] | boolean | number | undefined>) {
  const qs = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === '' || value === false) continue;
    if (Array.isArray(value)) value.forEach((v) => qs.append(key, v));
    else qs.append(key, String(value));
  }
  const s = qs.toString();
  return s ? `?${s}` : '';
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.detail;
  return 'Something went wrong. Please try again.';
}
