/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', cookieDomainRewrite: 'localhost' },
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/unit/**/*.test.tsx'],
    setupFiles: ['tests/unit/setup.ts'],
  },
});
