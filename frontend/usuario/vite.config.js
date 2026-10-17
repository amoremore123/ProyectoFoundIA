import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
  // Usar el mismo origen local que permite el backend al revisar la compilación.
  preview: { host: 'localhost', port: 5173, strictPort: true },
});
