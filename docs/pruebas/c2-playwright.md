# C2 — Pruebas de punta a punta de la web con Playwright

Responsable: Yajaira · Rama: `frontend/web` · Sprint 4 (cierre del proyecto)

## Qué cubren

Recorridos completos en un navegador real (Chrome), contra los **servicios reales de QA**: la web,
Spring, Django, MySQL y Mailpit. **No hay mocks**: el código de verificación se lee del correo que
llega a Mailpit, las publicaciones y reclamos se guardan en la base y el panel acepta el reclamo
con su propia pantalla.

| Archivo | Recorrido | Historias |
| --- | --- | --- |
| `e2e/cuenta.e2e.js` | Registrarse (sin aceptar términos se rechaza), verificar el correo con el código real e iniciar sesión | H11, H12, H33 |
| | El mismo correo no se registra dos veces: aviso general y error junto al campo | H11 |
| | Contraseña incorrecta muestra el error; la correcta entra | H12 |
| `e2e/publicacion.e2e.js` | Publicar un objeto perdido, ver la coincidencia con un hallazgo y contactar; quien encontró recibe el aviso sin el correo de quien perdió | H1, H3, H4, coincidencias y H18 |
| | Sin sesión, Publicar lleva al inicio de sesión | Rutas protegidas |
| | Detalle de un objeto en custodia: sin coincidencias ni errores 404 | M1 (Sprint 4) |
| `e2e/custodia.e2e.js` | Etiqueta escaneada → reclamo con respuesta → la biblioteca acepta **en el panel** → QR de retiro → otra persona ya no puede reclamar | H41, H42, H43, H44 |
| | Una etiqueta falsa se rechaza | H41 |
| `e2e/panel-y-perfil.e2e.js` | Panel: respaldos de la base y de las fotos con el procedimiento de recuperación; crear una campaña con su enlace de origen | H58, H59, H60 |
| | Una cuenta de usuario no entra al panel | H36 |
| | Subir la foto de perfil (archivo real), verla tras recargar y quitarla; un archivo que no es imagen se rechaza | H64 |
| | `?embebido=1`: Términos y Privacidad sin menú, recordado al navegar; `?embebido=0` lo quita | H73 |

Quedan fuera hasta que exista su backend: chat (H55, H56, H61 a H63), push (H57) y restaurar desde
el panel (H70). Se agregan aquí cuando Leonel suba esas APIs.

## Resultado del 21/11/2026

**13 de 13 pruebas en verde** (1,4 minutos), con Playwright 1.65.1 y Chrome, contra Spring y
Django de `dev/leonel` (incluye `main`) y MySQL 8.0 con las migraciones H59, H60 y H64.

| # | Prueba | Resultado |
| --- | --- | --- |
| 1 | Registro, verificación con código de Mailpit e ingreso | ✅ |
| 2 | Correo repetido rechazado | ✅ |
| 3 | Contraseña incorrecta y luego correcta | ✅ |
| 4 | Custodia completa con aceptación en el panel y QR | ✅ |
| 5 | Etiqueta falsa | ✅ |
| 6 | Panel: respaldos y campaña nueva | ✅ |
| 7 | Usuario sin acceso al panel | ✅ |
| 8 | Foto de perfil: subir, recargar y quitar | ✅ |
| 9 | Foto de perfil: archivo no válido | ✅ |
| 10 | Páginas embebidas para la app | ✅ |
| 11 | Publicar, coincidencia y contacto | ✅ |
| 12 | Publicar sin sesión | ✅ |
| 13 | Detalle en custodia sin errores | ✅ |

### Hallazgo corregido durante la prueba

El campo del código en `/verificar` tenía `aria-label="Código de 6 dígitos"`, distinto del texto
visible «Código de verificación». Un lector de pantalla lo anunciaba con otro nombre y el control
por voz no lo encontraba por lo que se ve (WCAG 2.5.3). Ahora el nombre es el texto visible y la
cantidad de dígitos va como ayuda (`aria-describedby`). Corregido en `src/pages/Verificar.jsx`.

## Cómo ejecutarlas

1. Levantar los servicios: `docker compose up -d mysql mailpit api admin-api` (sin `down -v`).
2. Levantar el panel: `npm --prefix frontend/admin run dev` (puerto 5174).
3. Desde `frontend/usuario`:

   ```bash
   npm run e2e
   ```

   La web se levanta sola si no está corriendo. Si el panel usa otro puerto:
   `E2E_PANEL=http://localhost:5175 npm run e2e` (en PowerShell: `$env:E2E_PANEL="http://localhost:5175"; npm run e2e`).

4. Informe con capturas de los fallos: `npx playwright show-report e2e-informe`.

Variables opcionales: `E2E_WEB`, `E2E_API`, `E2E_ADMIN_API`, `E2E_MAILPIT`, `E2E_ADMIN_CORREO`,
`E2E_ADMIN_PASSWORD` (por defecto, la cuenta de administrador documentada en `database/seed.sql`).

## Datos que dejan

Cada ejecución crea cuentas `e2e-…@prueba.invalid` (dominio reservado, nunca recibe correo real),
publicaciones con «e2e» en el nombre, una custodia, un reclamo aceptado y una campaña. Las campañas
no se pueden borrar (la API responde `405`). Por eso estas pruebas se corren en la base de QA o de
desarrollo, **nunca sobre datos reales**. Las contraseñas de las cuentas nuevas se generan en cada
ejecución y no se guardan.
