import { Anchor, Stack, Text, Title } from '@mantine/core';
import { Link } from 'react-router-dom';

export function NotFoundPage({ what = 'Page' }: { what?: string }) {
  return (
    <Stack align="flex-start">
      <Title order={2}>{what} not found</Title>
      <Text c="dimmed">It may not exist, or you may not have access to it.</Text>
      <Anchor component={Link} to="/">
        Back to the directory
      </Anchor>
    </Stack>
  );
}
