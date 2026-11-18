import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'jsdom', // service 401 分支触 window.location；cache 依赖 localStorage
    include: ['src/**/*.spec.ts'],
  },
});
