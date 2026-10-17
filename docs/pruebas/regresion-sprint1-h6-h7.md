# Regresión integrada del Sprint 1 — H6/H7

Fecha: **17 de octubre de 2026**. Responsable: **Leonel**.

## Alcance y base

- Rama `dev/leonel`; únicamente commits locales, sin push ni cambios en `main`.
- Base backend/datos `5cfb391`; frontend autorizado `frontend/web` en `dd3e72b`.
- Merge local `cafdf93`, conservando las correcciones de Amore. Los conflictos
  fueron de interfaz: orden/filtrado de categorías, historial de eliminados y
  descripción. Se mantuvo el máximo de 16.000 unidades UTF-16 de la API, el
  historial y los IDs, junto al diseño de Yajaira.
- Orden del PDF: H6 ejecutada y registrada en `39b5faf` antes de iniciar H7.
- H7, sus pruebas y la corrección de contraste quedaron en `c665177`.
- Esta revisión incluye Buscar, categorías y su integración con Detalle y el
  flujo de registro/publicación. No abre las trece historias restantes, no
  certifica H1–H20 ni sustituye las pruebas particulares de las otras integrantes.

## Suites y compilaciones nuevas

| Ejecución | Resultado |
| --- | --- |
| Integración inicial de frontend | 80/80 tests de usuario; builds de usuario y administrador correctos. |
| H6 backend | 173/173; 0 fallos, errores u omitidas; JAR correcto. |
| H6 frontend | 87/87; 0 omitidas; build correcto. |
| H7 y regresión backend | 189/189; 0 fallos, errores u omitidas; JAR correcto. |
| H7 y regresión frontend | 95/95; 0 omitidas; build correcto. |
| Administrador tras H7 | Build correcto; no hay script/suite de tests configurado. |

La base backend tenía 154 casos: H6 añade 19 y H7 añade 16. El frontend integrado
tenía 80: H6 añade 7 y H7 añade 8. Son suites compartidas, no pruebas exclusivas
de una sola persona; repetirlas no crea casos nuevos.

## API, MySQL y navegador reales

H6: **34/34 comprobaciones HTTP y 7/7 casos agrupados de navegador**, sin fallos.
H7 y regresión final: **78/78 comprobaciones HTTP y 13/13 casos agrupados de
navegador**, sin fallos. Incluye los casos de H6; no se suman como pruebas únicas
de ambas fases. JAR, build y suites se repitieron después de corregir el contraste.

### Hallazgos de la primera regresión

La primera ejecución H7 pasó 89 de 91 comprobaciones. Se conservan sus logs,
capturas y JSON en `evidencias/h7-primera-ejecucion/`; no se sustituyen por el
resultado posterior. Falló el contraste del contador (4,389:1) y un selector de
prueba de imagen fue ambiguo al encontrar dos `Sin foto`. El flujo completo sí
pasó. La corrección cambia únicamente el contador a `--text-muted`; la prueba
de imagen se acota a la etiqueta correspondiente. Se repiten las suites y el
build de usuario y toda la integración antes de registrar el cierre.

La repetición final pasó **91/91**. El contador pasó a **5,94:1 en claro y
8,83:1 en oscuro**; todos los textos medidos de Buscar/Detalle superaron 4,5:1.

### Matriz de aceptación H6/H7

| PDF | Evidencia nueva |
| --- | --- |
| Fragmentos de nombre, descripción y ubicación; espacios, mayúsculas y `%`/`_` literales | Tests de integración y siete entradas React nuevas; HTTP y navegador comparan IDs y orden con fixtures, incluyendo `!`, barra invertida, `+`, apóstrofo y tildes. |
| Privacidad, orden, vacío distinto de error y recuperación H6 | ACTIVO/RECUPERADO sí; OCULTO/ELIMINADO no; detalle privado 404, POST anónimo 401; fecha/id descendentes; vacío 200, entrada inválida 400, caída MySQL 500 y recuperación sin reiniciar API. |
| Categoría/publicación, IDs estables y tres endpoints | Catálogo con IDs 101/205/309/413; listado, búsqueda y endpoint dedicado conservan categoría, objetos y orden; sin huérfanos. |
| Todas, vacías, desactivadas, inexistentes y combinación de filtros | Tests H2, HTTP real y navegador; Todos no equivale a Todas: esta última solo retira categoría, conservando texto/tipo/ubicación. |
| Orden y búsqueda de opciones al escribir | Tests de español/tildes/mayúsculas, reducción/borrado de opciones, selección retenida sin coincidencias y selección real con teclado. |
| URL, enlace, Atrás/Adelante, cancelación y catálogo | Historial/recarga en Chrome; consultas lentas/errores tardíos/cancelación en tests React; fallos independientes de catálogo y búsqueda en navegador, con reintentos reales sin perder filtros. |
| Imágenes, tamaños y contraste de la integración | Imagen válida/ausente/fallida, Sin foto; capturas 320/375/1280 en claro y oscuro; estructura de encabezados, teclado y contraste de textos medidos. |
| Flujo completo del PDF | Formularios reales de registro → correo SMTP en Mailpit → verificación → login/JWT → publicación con ubicación/fecha → detalle → búsqueda → categoría. Datos y bytes UTF-8 coinciden entre UI, API y MySQL. |

La cuenta y publicación del flujo final son ficticias; la fecha se comprueba en
el formulario, detalle, respuesta y base, no solo por el código HTTP. No se usaron
datos del equipo. H6/H7 quedan verificadas con esta integración; el cierre global
de la primera parte requiere también las pruebas particulares de las demás
historias y pantallas. **No se iniciaron las trece historias restantes.**

El cliente HTTP y Chrome usan la misma API y MySQL QA. El navegador abre el
**build de producción**, no páginas simuladas; los únicos dobles de red son
fallos controlados para comprobar mensajes y reintentos. La caída de MySQL
se hace deteniendo exclusivamente el contenedor QA y recuperándolo, sin
reiniciar la API.

## Aislamiento y conservación

- Proyecto Docker exclusivo `foundia-leonel-integral-20261017-6we4pzp`.
- MySQL en `127.0.0.1:13329`, SMTP en `127.0.0.1:11329`, Mailpit HTTP en
  `localhost:18329`, API QA en `localhost:18389` y build web en `localhost:15173`.
- Base nueva `qa_leonel_integral`: 4 categorías con IDs 101/205/309/413 y
  29 objetos ficticios. Las fotos y la cuenta/publicación del flujo son también QA.
- `database/script.sql` se adapta solo a esa base nueva. **No se reimporta en
  phpMyAdmin ni en una base del equipo**; no se necesitan migraciones de datos.
- Las copias excluyen `.env`, `target`, `dist` y dependencias anteriores; se usa
  `npm ci`. No se cambiaron configuración privada, esquema ni backend productivo.
- Los documentos Word/PDF y `docs/planificacion/` se comprueban mediante SHA-256
  y permanecen fuera de estos commits.
- Al terminar se cerraron Java, preview y Chrome propios. `docker compose down`
  retiró únicamente los contenedores y la red de este proyecto QA, **sin `-v`**;
  su volumen de datos ficticios se conserva. No se detuvieron servicios del equipo.

## Repetir las suites versionadas

En terminales independientes, desde las respectivas carpetas:

```powershell
# backend/springboot
mvn.cmd -B -ntp clean verify
```

```powershell
# frontend/usuario
npm ci
npm test
npm run build
```

```powershell
# frontend/admin
npm ci
npm run build
```

Estas suites usan H2 efímera y API simulada en componentes; no necesitan los
datos del equipo. La integración adicional usa los scripts y fixtures propios
guardados en la carpeta de evidencia. El origen CORS y la URL de API usados por
QA se pasan solo a esos procesos/builds; no modifican la configuración del equipo.

## Evidencia y límites

Carpeta local: `C:\Users\Leonel\Downloads\FoundIA-Leonel-Integral-20261017\`.
Contiene control de bases/commits, scripts QA, configuración aislada, fixtures,
reportes Surefire/Vitest, logs de Maven/Vite, hashes de fuentes y JSON de casos
con esperado/obtenido/estado, además de capturas de navegador.

Herramientas: Maven 3.9.16; JDK 21.0.12.1 con destino Java 17; Node 24.21.0;
Vite 5.4.21; MySQL 8 y Chrome local mediante Playwright.
Mailpit comprueba entrega SMTP real en QA, no un proveedor externo.
Las comprobaciones de tamaños, teclado y contraste de textos concretos no son
una auditoría WCAG completa. No se certifican aquí los 15 minutos naturales de
bloqueo ni toda la matriz de otras pantallas que el PDF asigna a Yajaira.

Detalles por historia: [H6](casos-prueba-h6.md#5-validación-integral-del-pdf--17-de-octubre-de-2026)
y [H7](casos-prueba-h7.md#5-validación-integral-del-pdf--17-de-octubre-de-2026).
