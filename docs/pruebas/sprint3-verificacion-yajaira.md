# Sprint 3 — Verificación real de Yajaira

Fecha: **10/11/2026**. Rama `frontend/web`, base `e7e7063`.
Se actualizó la información remota con `git fetch origin`; `origin/dev/amore`
sigue en `521965b` (cierre Sprint 2). No se hicieron commits, push ni merges.
Las cuentas utilizadas son exclusivamente las de `database/seed.sql`.

## Respaldo, migraciones y servicios

Docker Desktop estaba detenido y se inició. Antes de migrar se generó un
`mysqldump --single-transaction --no-tablespaces`, usando la contraseña del
entorno del contenedor, sin imprimirla ni incluirla en argumentos.

- Archivo externo: `C:\Users\Yajaira\Documents\FoundIA-respaldos\foundia-pre-sprint3-20261110-114205.sql`.
- Tamaño: **23 976 bytes**; comprobados `CREATE TABLE` y el cierre del dump.
- SHA-256: `40d0b876f2cb710c0354ef4fdff8fecbfb70027f2d9daf1ea4c1bf985ab7e03f`.
- Migraciones aplicadas sin error: **H36 → H37 → H38 → H41 → H43 → H53 → H54**.
- `docker compose up -d --build api admin-api`: correcto.
- Django: `docker compose exec -T admin-api python manage.py test --settings=config.settings_test`:
  **108 pruebas OK**, incluida `nucleo/test_sprint3_h54.py`. No requirió correcciones.

No se borraron volúmenes ni se recargó el seed. No se modificaron credenciales.

## Pruebas con las APIs reales

Spring :8081, Django :8000, web :5173 y panel :5174. Evidencia HTTP sin tokens:
[47 peticiones principales](sprint3-evidencias/peticiones-principal.json) y
[17 complementarias](sprint3-evidencias/complemento.json).
Cada entrada conserva método, ruta sin query sensible, código esperado/obtenido
y tamaño/huella cuando se descargó un archivo.

| Historia | Resultado | Evidencia y alcance |
| --- | --- | --- |
| H25 | OK | Historial TODAS/NO_LEIDAS/LEIDAS, páginas 0 y 1 de tamaño 1; filtro inválido 400. Marcar todas y repetir devuelve 0 actualizadas. |
| H49 sin clave | OK | POST devuelve categoría Mochila, descripción y `origen=LOCAL`. La clave no está configurada en el contenedor. |
| H49 con clave | PENDIENTE | No hay `IA_API_KEY` configurada. No se inventó una clave ni se sustituyó el proveedor por un mock para darla por terminada. |
| H47 | OK API | `#8` produjo tarjeta OBJETO y acción ABRIR_RECLAMO; «quiero publicar una mochila perdida en biblioteca» produjo PRELLENAR_PUBLICAR. Las confirmaciones de las tarjetas están cubiertas por Vitest; no se realizó una nueva pasada visual del chat. |
| H37 | OK | Logout individual en ambos backends invalida el token (401) y conserva otra sesión. Logout-all invalida dos sesiones (401). |
| H36 | OK | Cambio temporal de Luis a BIBLIOTECA por API y por panel. Login real: solo Custodia, Reclamos y Vencidos. Rutas de sistema: 403. Se restauró Luis a USUARIO. |
| H41 | OK API | Objetos QA #8 y #9 recibidos; custodias #1/#2. Etiqueta PDF y constancia A4 descargadas, PDF válido de una página cada uno. |
| H43 | OK API | Luis reclamó #8 estando EN_CUSTODIA; biblioteca aceptó, se descargó QR PDF y se registró entrega. Repetición: 409. Estado final RECUPERADO. |
| H54 | OK API | Con plazo 1, solo se envejeció la custodia QA #2 dos días. Apareció en vencidas; DONADO devuelve 201, acta PDF una página; repetición 409. Plazo restaurado a **60 días**. |
| H38 | OK | Servicio de respaldo ejecutado una vez; Dashboard real muestra Completado, fecha, archivo y tamaño 6,6 KB. |
| H32 | OK | Excel y PDF descargados por API y mediante los botones de Estadísticas. La interfaz confirmó ambos nombres de archivo. |
| H51 | OK automático / parcial manual | Diez auditorías con sesión: cero violaciones y sin desbordamiento. Persisten verificaciones de contraste incompletas; ver informe específico. |
| H52 | OK capa web / pendiente celular | Cámara trasera solicitada mediante input capture, validación y vista previa compartidas. Selección de JPEG desde esa entrada y publicación real #10 con foto visible. Falta captura física y permisos en un teléfono. |

El respaldo H38 adicional fue `foundia_objetos_perdidos_db_20261110T165159Z.sql.gz`,
**6 728 bytes**, SHA-256
`776d4005cc78822834e260e2486a5d2bafd356de4f43c0095dd70777219ec565`.
Permanece en el volumen de respaldos; el respaldo previo externo sigue intacto.
No se versionan los dumps ni los PDF con QR. Los descargados para verificar están
en `tmp/sprint3-real/` (ignorado); los de la interfaz, en Descargas.

Las publicaciones QA #8 (recuperada), #9 (oculta por disposición) y #10 (perdida
con fotografía) permanecen identificadas. Las pruebas de cierre global revocaron
las sesiones de prueba de Ana/Admin; para continuar puede ser necesario entrar de nuevo.

## APIs de Amore: H42, H44 y H40

Se revisaron controladores, DTOs, servicios, rutas Django, contratos y la rama
remota de Amore actualizada. **No están completas estas APIs**:

- **H42:** existe el reclamo básico de H20/H43, sin request ni campo de respuesta
  de verificación, ni consulta propia de estados de reclamo para Mis publicaciones.
  No se inventó formulario o endpoint. Sí se corrigió Detalle y la llegada desde
  FINDI para permitir `EN_CUSTODIA`, comportamiento real ya aprobado por H43.
  ACTIVO conserva su soporte actual. Esto no completa H42.
- **H44:** H43 ya permite a biblioteca validar/consumir el QR y rechazarlo usado.
  Falta la API para que el usuario consulte/descargue su QR y la validación conjunta
  QR de retiro + etiqueta que exige H44. No se expuso la ruta administrativa al usuario.
- **H40:** no hay contrato ni API de invitaciones en esta entrega. No se inventó.

El PDF de reparto asigna estas APIs a Amore. Cuando llegue su contrato se podrán
completar las pantallas, estados y pruebas reales correspondientes.

## Observación sobre 401/403

Se conservó la observación comunicada por el equipo: «Django respondía 401, no
403, con un token USUARIO de Spring». **Esta corrida no la reprodujo**: después
del rebuild, un token nuevo de Ana emitido por Spring obtuvo **403** en
`GET /api/admin/dashboard/`, con «No autorizado: se requiere rol ADMIN».
Sin token o con token revocado las pruebas obtuvieron 401. No se cambió código
de autenticación, permisos ni secretos. Comparar token/versión/compilación si
el equipo sigue observando 401.

## Cambios y validación final

- H52: nueva entrada de cámara y pruebas; reutiliza el servicio de subida existente.
- H51: nombre accesible del avatar/contador, avisos y contraste de Perdido;
  módulo axe opcional de desarrollo (`?auditar=1`).
- H43/Detalle: permite confirmar reclamos en custodia, con prueba de regresión.
- Compose: transmite `CUSTODIA_PLAZO_DIAS` a Django, default 60, para poder
  configurar el plazo sin modificar la imagen ni archivos secretos.
- Script reproducible: `python scripts/verificar-sprint3-yajaira.py`, luego
  `--complemento` con plazo 1. Crea datos QA nuevos y realiza entregas/disposición;
  usar únicamente en esta base de pruebas, después de respaldo y migraciones.

| Comando | Resultado |
| --- | --- |
| Spring: `mvn -o test` | **505 OK**, 0 fallos/errores/omitidas |
| Django: comando indicado arriba | **108 OK** |
| Usuario: `npx vitest run --maxWorkers=2` | **270 OK**, 34 archivos |
| Panel: `npx vitest run --maxWorkers=2` | **49 OK**, 6 archivos |
| Usuario: `npm run build` | OK |
| Panel: `npm run build` | OK |

Se observaron avisos previos de React Router/CORS en pruebas y 8 alertas de
dependencias en `npm install` (4 moderadas, 2 altas, 2 críticas). No se aplicó
`npm audit fix --force` ni se mezcló una actualización mayor con estas historias.
Lighthouse no ejecutado: el intento local con `npx --offline --no-install lighthouse --version`
devolvió `ENOTCACHED` (herramienta no disponible en la caché). Axe no reemplaza las
comprobaciones manuales pendientes.

Evidencias visuales:

- [Menú exclusivo de Biblioteca](sprint3-evidencias/h36-biblioteca.png).
- [Publicación #10 con foto subida](sprint3-evidencias/h52-publicacion-real.png).
- [Informe de accesibilidad H51](accesibilidad-h51.md).

**Pendientes de integración:** clave real para H49, APIs completas H42/H44/H40,
captura/permisos en teléfono, contraste manual y Lighthouse. Ninguno se declara
terminado con mocks. `.claude/` y `.impeccable/` permanecen fuera de los cambios
propuestos para Git; no usar `git add .`.
