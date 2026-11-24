import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Modo «celular» (npm run dev:celular): para probar desde el teléfono en la misma red Wi-Fi.
// El navegador solo permite la cámara en vivo y el GPS en páginas seguras, así que se sirve por HTTPS
// con un certificado local (carpeta .certs-dev/ en la raíz, que git ignora) y la API de Spring se
// alcanza por el mismo origen (/api y /uploads), sin CORS ni contenido mixto.
const CERTIFICADOS = fileURLToPath(new URL('../../.certs-dev/', import.meta.url));
const SPRING = process.env.FOUNDIA_SPRING || 'http://localhost:8081';

function sinOrigen(proxy) {
  proxy.on('proxyReq', (peticion) => peticion.removeHeader('origin'));
}

function servidorCelular() {
  const clave = `${CERTIFICADOS}clave.pem`;
  const certificado = `${CERTIFICADOS}certificado.pem`;
  if (!existsSync(clave) || !existsSync(certificado)) {
    throw new Error('Falta el certificado del modo celular en .certs-dev/ (clave.pem y certificado.pem). Ver docs/pruebas/celular.md');
  }
  return {
    host: true,
    https: { key: readFileSync(clave), cert: readFileSync(certificado) },
    // Para Spring es una llamada del mismo origen: se quita «Origin» para que no la trate como CORS
    proxy: {
      '/api': { target: SPRING, changeOrigin: true, configure: sinOrigen },
      '/uploads': { target: SPRING, changeOrigin: true, configure: sinOrigen },
    },
  };
}

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    ...(mode === 'celular' ? servidorCelular() : {}),
  },
  // Usar el mismo origen local que permite el backend al revisar la compilación.
  preview: { host: 'localhost', port: 5173, strictPort: true },
}));
