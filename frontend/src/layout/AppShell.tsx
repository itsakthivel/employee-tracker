import { AppShell as Shell, Burger, Button, Group, NavLink, Stack, Text } from '@mantine/core';
import { useDisclosure } from '@mantine/hooks';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

const EVERYONE = [
  { to: '/', label: 'Directory' },
  { to: '/me', label: 'My profile' },
];
const ADMIN = [
  { to: '/overview', label: 'Overview' },
  { to: '/departments', label: 'Departments' },
  { to: '/locations', label: 'Locations' },
  { to: '/users', label: 'Sign-in accounts' },
  { to: '/access-log', label: 'Sensitive access log' },
];

export function AppShell() {
  const { user, isAdmin, signOut } = useAuth();
  const [opened, { toggle, close }] = useDisclosure();
  const { pathname } = useLocation();
  const links = isAdmin ? [...EVERYONE, ...ADMIN] : EVERYONE;
  const active = (to: string) => (to === '/' ? pathname === '/' : pathname.startsWith(to));

  return (
    <Shell header={{ height: 56 }} navbar={{ width: 240, breakpoint: 'sm', collapsed: { mobile: !opened } }} padding="md">
      <Shell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Group>
            <Burger opened={opened} onClick={toggle} hiddenFrom="sm" size="sm" aria-label="Menu" />
            <Text fw={700}>Employee Tracker</Text>
          </Group>
          <Group gap="xs">
            <Text size="sm" c="dimmed" visibleFrom="sm">
              {user?.username} · {isAdmin ? 'Administrator' : 'Employee'}
            </Text>
            <Button variant="subtle" size="xs" onClick={() => signOut()}>
              Sign out
            </Button>
          </Group>
        </Group>
      </Shell.Header>
      <Shell.Navbar p="xs">
        <Stack gap={2}>
          {links.map((l) => (
            <NavLink key={l.to} component={Link} to={l.to} label={l.label} active={active(l.to)} onClick={close} />
          ))}
        </Stack>
      </Shell.Navbar>
      <Shell.Main>
        <Outlet />
      </Shell.Main>
    </Shell>
  );
}
