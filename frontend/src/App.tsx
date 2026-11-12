import { Route, Routes } from 'react-router-dom';
import { RequireAdmin, RequireAuth } from './auth/guards';
import { SignInPage } from './auth/SignInPage';
import { ChangePasswordPage } from './auth/ChangePasswordPage';
import { ForgotPasswordPage } from './auth/ForgotPasswordPage';
import { ResetPasswordPage } from './auth/ResetPasswordPage';
import { AppShell } from './layout/AppShell';
import { DirectoryPage } from './pages/DirectoryPage';
import { EmployeeProfilePage } from './pages/EmployeeProfilePage';
import { EmployeeFormPage } from './pages/EmployeeFormPage';
import { MyProfilePage } from './pages/MyProfilePage';
import { OverviewPage } from './pages/OverviewPage';
import { DepartmentsPage, LocationsPage } from './pages/ReferenceDataPages';
import { UsersPage } from './pages/UsersPage';
import { AccessLogPage } from './pages/AccessLogPage';
import { NotFoundPage } from './pages/NotFoundPage';

export function App() {
  const admin = (el: JSX.Element) => <RequireAdmin>{el}</RequireAdmin>;
  return (
    <Routes>
      <Route path="/sign-in" element={<SignInPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route
        path="/change-password"
        element={
          <RequireAuth>
            <ChangePasswordPage />
          </RequireAuth>
        }
      />
      <Route
        element={
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        }
      >
        <Route index element={<DirectoryPage />} />
        <Route path="me" element={<MyProfilePage />} />
        <Route path="employees/new" element={admin(<EmployeeFormPage />)} />
        <Route path="employees/:id" element={<EmployeeProfilePage />} />
        <Route path="employees/:id/edit" element={admin(<EmployeeFormPage />)} />
        <Route path="overview" element={admin(<OverviewPage />)} />
        <Route path="departments" element={admin(<DepartmentsPage />)} />
        <Route path="locations" element={admin(<LocationsPage />)} />
        <Route path="users" element={admin(<UsersPage />)} />
        <Route path="access-log" element={admin(<AccessLogPage />)} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
