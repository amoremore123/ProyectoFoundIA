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
| `e2e/chat.e2e.js` | Quien perdió contacta y abre el chat; quien encontró lo ve «sin leer» en otro navegador y responde; el mensaje llega sin recargar; reportar un mensaje; bloquear impide escribir a los dos y desbloquear lo restablece | H55, H56 |
| | Quien reclamó un objeto en custodia escribe a la biblioteca y le envía una foto | H62, H63 |
| | La biblioteca ve el mensaje en su bandeja del panel, responde y la respuesta llega sin recargar | H63 |
| | Una conversación ajena no se abre | H55 |
| `e2e/sprint4-avisos.e2e.js` | Un aviso destacado creado en el panel aparece en Inicio, abre su página y desaparece al retirarlo | H71 |
| | El canal de cada tipo de aviso (teléfono, correo, ambos o solo la bandeja) se guarda en la cuenta | H68 |
| | Un mensaje del chat reportado en la web se resuelve en el panel y el autor recibe la advertencia sin saber quién reportó | H56 |
| | El panel muestra las versiones restaurables y el comando para ensayarlas, sin restaurar desde la web | H70 |
| `e2e/sprint4-difusion.e2e.js` | Una visita y un registro por el enlace de una campaña aparecen en el tablero de alcance del panel (y una recarga no vuelve a contar) | H67 |
| | El panel prepara el texto de una campaña para X con su enlace y registra lo publicado | H66 |
| | Un aviso masivo para el personal de biblioteca se envía tras la vista previa y llega a su bandeja | H69 |
| | En el chat se ve la foto de perfil de la otra persona | H64 |

Todas las historias web del Sprint 4 con backend están cubiertas. El chat se agregó el 22/11 con la API de la rama `cierre/sprint4`.

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

## Actualización del 22/11/2026: chat

**17 de 17 pruebas en verde**, con la API de `dev/leonel` más `cierre/sprint4` (chat) y
la migración `migracion_h55_chat.sql` aplicada tras un respaldo. Se agregaron las cuatro pruebas de
`e2e/chat.e2e.js`, incluida la bandeja de la biblioteca en el panel (H63).

## Actualización del 23/11/2026: H56, H68, H70 y H71

**21 de 21 pruebas en verde** (4,6 minutos) con la API de `dev/leonel` (incluye el chat) y las migraciones de
H56, H57, H65, H68, H70, H71 y el script de M1 aplicados tras un respaldo. Se agregaron las cuatro pruebas de
`e2e/sprint4-avisos.e2e.js`.

## Actualización del 23/11/2026 (tarde): H66, H67, H69, H64 y el nuevo H68

**25 pruebas.** En la corrida completa (9,1 minutos) pasaron 24; la de custodia superó el tiempo máximo al
cargar una página con la laptop muy exigida y pasó al repetirla sola (28,7 s). Se agregó
`e2e/sprint4-difusion.e2e.js` y se actualizó la prueba de H68 al canal por tipo de aviso. El tiempo máximo por
prueba subió a 90 s. La bandeja de reportes del chat va del más antiguo al más nuevo, así que la prueba de H56
avanza de página hasta encontrar su reporte.
