import { useQuery } from '@tanstack/react-query';
import { get, query, type EmployeePage, type Schemas } from './client';

export function useDepartments() {
  return useQuery({
    queryKey: ['departments'],
    queryFn: () => get<Schemas['Department'][]>('/departments'),
    staleTime: 60_000,
  });
}

export function useLocations() {
  return useQuery({
    queryKey: ['locations'],
    queryFn: () => get<Schemas['Location'][]>('/locations'),
    staleTime: 60_000,
  });
}

/** Employees for pickers (manager, account owner): current employees matching a search. */
export function useEmployeeOptions(search: string, includeTerminated = false) {
  return useQuery({
    queryKey: ['employee-options', search, includeTerminated],
    queryFn: () =>
      get<EmployeePage>(`/employees${query({ q: search, size: 20, includeTerminated })}`),
    staleTime: 30_000,
  });
}
