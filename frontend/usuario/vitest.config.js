import { defineConfig, mergeConfig } from 'vitest/config';
import viteConfig from './vite.config';

// vite.config.js depende del modo (el modo «celular» usa HTTPS); las pruebas usan el modo normal
export default mergeConfig(viteConfig({ mode: 'test', command: 'serve' }), defineConfig({
  test: {
    environment: 'jsdom',
    clearMocks: true,
    setupFiles: ['./src/test/setup.js'],
  },
}));
