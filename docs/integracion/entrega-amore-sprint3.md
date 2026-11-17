# Entrega de Amore para el Sprint 3

Los cambios de Compose, ViewModels, repositorios y Retrofit están en
`mobile/android`. `dev/amore` integra esa rama con las APIs y la web.
Room sigue guardando la caché pública del dispositivo. La base compartida
de los dos servidores sigue siendo MySQL.

| Historia | Trabajo de Amore |
| --- | --- |
| H52 | CameraX en Publicar con permiso, vista previa, repetición y galería. El JPEG se comprime al capturar y se valida contra el límite de 5 MiB del servicio de fotos existente. |
| H42 | Reclamo autenticado en custodia con respuesta de verificación, consulta del estado propio y protección contra duplicados. La bandeja de biblioteca muestra la respuesta. |
| H44 | Modo biblioteca, escaneo CameraX + ML Kit, entrada manual y QR de retiro del titular. Django valida la etiqueta firmada y el QR de retiro dentro de la misma transacción. |
| H46 | Dictado con el proveedor del dispositivo, aviso previo, permiso de micrófono y lectura con TextToSpeech. El texto dictado se puede revisar antes de enviar. |
| H50 | WorkManager consulta novedades de reclamos con permiso y preferencia habilitados. La notificación del sistema abre la bandeja autenticada y conserva un cursor por cuenta. |
| H40 | Enlace opaco emitido por el servidor, selector de Android y registro del uso en la creación de cuenta. El enlace no incluye identificadores de usuario, correo ni sesión. |

El bloque móvil también incluye H25 (historial, filtros, paginación y marcar
todas), H36 (entrada al modo biblioteca según rol), H37 (revocación y borrado
de sesión), H41 (estado en custodia), H43 (avisos de resolución), H45
(asistente integrado), H47 (tarjetas y acciones confirmadas), H48 (enlaces
legales), H49 (sugerencias editables), H51 (encabezados accesibles y etiquetas
adaptables) y H53 (recordatorios y su preferencia).

## Contratos compartidos

- **H40.** `POST /api/perfil/invitaciones` devuelve `{url}` para compartir.
  `POST /api/auth/register?invitacion=...` cuenta un uso si se crea la cuenta.
  El registro conserva el flujo de verificación por correo.
- **H42.** `GET/POST /api/objetos/{id}/reclamos/custodia`. El POST recibe
  `{respuesta}` de 3 a 500 caracteres. El GET devuelve el estado del reclamo
  propio o `SIN_RECLAMO` para un objeto reclamable. La respuesta privada solo
  aparece en la bandeja protegida de biblioteca.
- **H44.** `GET /api/objetos/{id}/reclamos/custodia/qr-retiro` devuelve
  `{token,expira}` únicamente al titular de un reclamo aceptado y vigente.
  `POST /api/admin/reclamos/retirar/` recibe `{token,etiqueta}`. Rechaza el
  QR vencido, usado o correspondiente a otro objeto y conserva el estado
  previo en cada rechazo.
- **H50.** `GET /api/notificaciones/reclamos/novedades?desde=...&despuesDe=0`
  devuelve hasta 50 avisos propios sin leer, en orden de ID. `desde` usa ISO
  local interpretado como UTC. WorkManager puede retrasar la consulta según
  la batería y las condiciones del dispositivo.

Para un MySQL existente con H41/H43 aplicadas, respaldar y aplicar
`database/migracion_h40_invitaciones.sql` y
`database/migracion_h42_respuesta_custodia.sql`. Una instalación nueva ya
incluye ambas en el esquema. Las comprobaciones se realizaron en un volumen
de demostración separado.

## Validación

- 47 pruebas unitarias Android, 4 instrumentadas de Room, APK compilado y
  lint con 0 errores y 33 advertencias.
- 33 pruebas seleccionadas de Spring, 108 de Django, 270 de web de usuarios
  y 49 del panel. Las dos webs compilaron.
- 45 respuestas HTTP verificadas con Spring, Django, MySQL y Mailpit
  en Docker. Incluyen foto, FINDI, reclamo propio, rechazo de QR cruzado y
  usado, avisos de la cuenta, invitación y revocación.
- En el emulador con texto al 130 %, el acceso real mostró el inicio y el
  perfil. El selector de invitación se abrió y se pudo cancelar. Denegar el
  micrófono conservó el campo para escribir.

Resultados HTTP: [custodia, avisos e invitación](../pruebas/sprint3-amore/custodia-avisos-invitacion.json)
y [foto y FINDI](../pruebas/sprint3-amore/foto-findi.json).

El APK de QA usa los puertos del host 18281 (Spring), 18200 (Django) y
18273 (web). Para el emulador, compilar con `-PfoundiaApiUrl=http://10.0.2.2:18281/`,
`-PfoundiaAdminApiUrl=http://10.0.2.2:18200/` y
`-PfoundiaWebUrl=http://10.0.2.2:18273/`.

El 17/11 se validaron en el emulador con las APIs reales H52 y H50. En H52 se
probó negar la cámara, usar la galería, reintentar, repetir la foto y subirla.
En H50 se probó el permiso negado, la notificación con la app cerrada, que no
lleguen avisos ajenos, que se abra la bandeja y la preferencia desactivada. El
detalle está en
[validacion-emulador-h50-h52.md](../pruebas/sprint3-amore/validacion-emulador-h50-h52.md).

Quedan por certificar en un teléfono la cámara física, el escaneo de los dos
QR, el dictado en un ambiente con ruido y la entrega natural de WorkManager. En
el emulador, la ejecución con la app cerrada se forzó después del periodo. La
lectura con TalkBack requiere una prueba con el servicio activo.
