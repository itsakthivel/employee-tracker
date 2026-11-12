import type { ReactNode } from 'react';
import { Center, Paper, Stack, Text, Title } from '@mantine/core';

export function AuthCard({ title, intro, children }: { title: string; intro?: string; children: ReactNode }) {
  return (
    <Center mih="100vh" p="md" bg="gray.0">
      <Paper withBorder shadow="sm" p="xl" radius="md" w="100%" maw={420}>
        <Stack gap="md">
          <div>
            <Text size="sm" c="dimmed" fw={600}>
              Employee Tracker
            </Text>
            <Title order={2}>{title}</Title>
            {intro && (
              <Text size="sm" c="dimmed" mt={4}>
                {intro}
              </Text>
            )}
          </div>
          {children}
        </Stack>
      </Paper>
    </Center>
  );
}
