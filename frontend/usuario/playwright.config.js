import { defineConfig } from '@playwright/test';

// C2: pruebas de punta a punta contra los servicios reales de QA (sin mocks).
// Requisitos: Docker con MySQL, Spring (:8081), Django (:8000) y Mailpit (:8025) en marcha.
// La web se levanta sola si no está corriendo. El panel debe estar abierto en E2E_PANEL.
//   npm run e2e                      → todas las pruebas
//   E2E_PANEL=http://localhost:5175 npm run e2e   → si el panel usa otro puerto
// Usa el Chrome instalado (no descarga navegadores). Las pruebas crean cuentas y
// publicaciones con el prefijo «e2e» y correos @prueba.invalid: no usar sobre datos reales.
export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.js',
  // Las pruebas comparten la misma base de datos: una a la vez para que no se pisen
  workers: 1,
  fullyParallel: false,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'e2e-informe', open: 'never' }]],
  outputDir: 'e2e-resultados',
  use: {
    baseURL: process.env.E2E_WEB || 'http://localhost:5173',
    channel: 'chrome',
    locale: 'es-PE',
    timezoneId: 'America/Lima',
    viewport: { width: 1280, height: 800 },
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run dev',
    url: process.env.E2E_WEB || 'http://localhost:5173',
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
