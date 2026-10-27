# Amore: H33 y H34, API común para web y Android

## H33: documentos y aceptación antes del registro

`GET /api/legal/documentos` es público. Devuelve `version`, `estado`, `terminos`,
`privacidad` y `sha256`. La versión inicial es `proyecto-2026-10-26-v1`.
Los textos fueron preparados a petición de Amore para la demostración académica;
se presentan como textos del proyecto pendientes de revisión institucional, no
como documentos oficiales aprobados. No se inventan institución, domicilio,
correo de atención, proveedor, plazo de conservación ni aceptación histórica.

Los archivos inmutables están en `backend/springboot/src/main/resources/legal/`.
Una futura modificación requiere otra versión y conservar los archivos anteriores.
La huella SHA-256 usa términos + salto de línea + privacidad, con finales de línea
normalizados a LF, igual en Windows y Linux.

`POST /api/auth/register` exige los campos existentes y:

```json
{"nombre":"Ana","apellido":"Prueba","correo":"ana@example.test","password":"Segura123!",
 "aceptaTerminos":true,"versionLegal":"proyecto-2026-10-26-v1"}
```

La casilla comienza desmarcada. La API rechaza omisión, rechazo o versión distinta
con 400 antes de crear cuenta o enviar correo. El servicio también valida estos
valores: no depende solo de la pantalla. Guarda `version_legal`,
`aceptacion_legal_fecha` UTC y `documentos_legales_hash` en la misma transacción del
registro. Si falla el correo se revierte todo. La verificación del correo continúa
siendo obligatoria; aceptar documentos no activa la cuenta.

Las cuentas anteriores conservan NULL: no se rellenan ni se fuerza una aceptación
retroactiva. Los campos no se exponen en los DTO públicos del autor.

**Integración web de Yajaira:** cargar documentos, mostrar ambos textos y su
versión, casilla desmarcada y enviar los dos campos. No habilitar el registro si
los documentos no cargaron; al recargarlos se vuelve a pedir aceptación. El
formulario web anterior no envía estos campos y la API ahora lo rechaza: esta
adaptación forma parte del apoyo web pendiente de Yajaira.

## H34: solicitud de eliminación, sin ejecutar borrado

JWT de una cuenta activa/verificada; identidad exclusiva del token.

- `GET /api/perfil/solicitud-eliminacion`: estado propio, sin escribir filas.
- `POST /api/perfil/solicitud-eliminacion`, cuerpo exacto `{"confirmar":true}`.
  No admite IDs, fechas ni otros campos manipulados en el cuerpo.
- Primera solicitud: 201. Repetida: 200, `repetido:true`, mismo ID, fecha y estado.
- Respuesta: `id`, `estado` (`SIN_SOLICITUD` o `PENDIENTE`), `fechaSolicitud` UTC,
  `repetido` e `informacion` que explica que el trámite no ejecuta un borrado.
- 400 sin confirmación; 401 sin sesión válida. Se conserva el control existente
  de cuentas suspendidas/bloqueadas. No hay consulta por ID de otra persona.

El bloqueo de la fila de usuario serializa dos solicitudes iniciales simultáneas;
la restricción única por usuario sirve de segunda protección. No se cambian el
perfil, contraseña, publicaciones, avisos, estado de la cuenta ni antecedentes.
No se agregó un endpoint para afirmar o ejecutar una eliminación completa.

En web: explicar, confirmar/cancelar antes del envío, mostrar éxito solo tras
respuesta real y reconsultar el estado. No usar mensajes «cuenta borrada».

## Migraciones y despliegue

Para instalaciones existentes, respaldar y comprobar el destino antes de aplicar,
en este orden, `database/migracion_h33_aceptacion_legal.sql` y
`database/migracion_h34_solicitudes_eliminacion.sql`. Son reejecutables y no
rellenan aceptaciones o solicitudes antiguas. `script.sql` incorpora el esquema
para instalaciones nuevas; no reimportarlo ni ejecutar seed.sql sobre datos del equipo.
La API necesita el esquema actualizado antes de arrancar esta versión. En esta
entrega solo se aplicaron a servidores QA aislados; la BD compartida no se modificó.

## Textos para el proyecto

La redacción describe el código real de FoundIA y las limitaciones de la versión
académica; requiere revisión institucional antes de presentarse como política
oficial. Como orientación de transparencia y consentimiento se consultaron las
[guías oficiales de consentimiento informado](https://guias.servicios.gob.pe/creacion-servicios-digitales/investigacion/consentimiento)
y de [protección de datos](https://guias.servicios.gob.pe/creacion-servicios-digitales/gobierno-digital/gobierno-datos).
Estas referencias no acreditan el cumplimiento jurídico del proyecto.
