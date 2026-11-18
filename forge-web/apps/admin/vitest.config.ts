import vue from '@vitejs/plugin-vue';
import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vitest/config';

// 独立于 vite.config.ts（测速/不需 proxy）：@ 别名 + vue 插件（SFC 传递依赖编译）+ jsdom。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom', // 守卫/cache/指令触 DOM 与 localStorage
    include: ['src/**/*.spec.ts'],
  },
});
