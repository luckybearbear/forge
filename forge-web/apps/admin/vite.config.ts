import { fileURLToPath, URL } from 'node:url';

import vue from '@vitejs/plugin-vue';
import { defineConfig, loadEnv } from 'vite';

// dev 代理模式（spec §5.3）：/admin-api → demo 后端 8080，不做 CORS；生产同源反代。
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: Number(env.VITE_PORT) || 5173,
      proxy: {
        '/admin-api': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
  };
});
