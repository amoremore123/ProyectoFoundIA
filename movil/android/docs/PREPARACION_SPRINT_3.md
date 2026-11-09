# Preparación de Amore para el Sprint 3

## Base de Android

Compose presenta las pantallas. Cada ViewModel mantiene el estado y envía las
acciones al repositorio. Retrofit consulta Spring y Room conserva la caché
pública del teléfono. La sesión actual de FoundIA se guarda cifrada y se
mantiene el flujo de acceso, verificación y recuperación del Sprint 2.

Las pantallas se organizan por función en `ui/screens`, los formularios
compartidos en `ui/components`, el acceso HTTP en `data/remote` y la sesión en
`data/session`. El código Android va en `mobile/android`. Los contratos y el
backend de Amore corresponden a `dev/amore`.

## Historias que corresponden a Amore

| Historia | API de Amore en `dev/amore` | Android en `mobile/android` |
| --- | --- | --- |
| H52 | Revisar el contrato y los límites de fotos | CameraX en Publicar, vista previa, compresión, reintento y galería ante permiso denegado |
| H42 | Reclamo propio en custodia, verificación y rechazo de duplicados | Formulario y estado del reclamo, con prueba de repetición |
| H44 | Validar los dos QR y registrar la entrega de un solo uso | Escáner CameraX + ML Kit y QR de retiro, con pruebas de permisos y rechazos |
| H46 | La API del asistente depende de H45 | Dictado, lectura en voz alta, permiso, aviso de privacidad y entrada por texto |
| H50 | Endpoint de novedades propias | WorkManager, canal, permiso, preferencia y apertura del reclamo desde el aviso |
| H40 | Enlace sin datos personales y registro de su uso | Compartir con el selector del sistema y comprobar cancelación y privacidad |

Trabajo móvil de las historias del equipo: H36 (modo biblioteca), H41 (estado
en custodia), H43 (resultado del reclamo), H45 (asistente), H37 (cierre seguro),
H53 (recordatorios), H48 (enlaces legales), H25 (historial de avisos), H47
(tarjetas y confirmación), H49 (sugerencias al publicar) y H51 (TalkBack y texto
al 130 %). H32, H38 y H54 se mantienen en las capas indicadas en el reparto.

Orden técnico: H52 puede avanzar con la API de fotos existente; H42 depende
del contrato de custodia de H41; H44 necesita H36, H41 y H43; H50 debe integrarse
con las novedades de reclamos. H46 puede preparar la entrada y lectura de voz
y necesita H45 para validar el flujo completo. H40 tiene su propio contrato.
Cada historia se valida en dispositivo con API real antes de declararla completa.

## Firebase Auth

Integración transversal adicional al reparto. El APK necesita un proyecto
Firebase y una aplicación registrada con su identificador actual
`com.proyectointegrador.movil`. La credencial de administración queda en el
servidor. Se propone conservar las cuentas existentes: Spring valida la sesión
de FoundIA y emite un token personalizado de Firebase con un UID estable ligado
al identificador interno. Android lo intercambia con `signInWithCustomToken`.

Antes de activar el flujo, probar acceso, recuperación, cambio de cuenta, cierre
en todos los dispositivos y fallos de red. El backend conserva el control de
roles y revocación. Si se aceptan tokens de identidad de Firebase en una API,
el servidor debe verificarlos y resolver la cuenta interna.

[Autenticación personalizada en Android](https://firebase.google.com/docs/auth/android/custom-auth) ·
[Verificación de tokens en el servidor](https://firebase.google.com/docs/auth/admin/verify-id-tokens)

## SQLite en el servidor

Room ya usa SQLite dentro de Android. Para cambiar la base del proyecto completo,
Spring y Django tendrían que usar el mismo archivo SQLite persistente del
servidor. El reparto usa MySQL para QA y asigna Compose y migraciones compartidas
a la integración de Leonel. Tratar el cambio como migración transversal en una
rama de ensayo y actualizar el contrato de infraestructura antes del corte.

1. Respaldar MySQL y restaurarlo en QA. Traducir esquema, índices, restricciones,
   fechas y reglas de comparación de texto. Importar la copia conservando IDs y
   comprobar filas, relaciones y secuencias antes de usarla.
2. Probar el dialecto SQLite de Hibernate y el motor SQLite de Django con el
   mismo archivo local. Revisar las transacciones que hoy bloquean filas en
   MySQL, en particular login, recuperación, coincidencias y resolución de
   reportes. SQLite no aplica `SELECT FOR UPDATE`.
3. Ejecutar ambas APIs en QA. Verificar escrituras concurrentes, fechas UTC,
   consultas desde el otro backend, reinicio de Docker y restauración del
   respaldo. Incluir entrega QR de un solo uso y revocación de sesiones.
4. Con ambas APIs comprobadas, detener escrituras en MySQL durante el corte,
   copiar el estado final y arrancar con SQLite. Conservar el respaldo y definir
   cómo recuperar las escrituras posteriores antes de volver a MySQL.

[Dialectos de Hibernate 6.6](https://docs.hibernate.org/orm/6.6/dialect/) ·
[Transacciones SQLite en Django](https://docs.djangoproject.com/en/5.2/ref/databases/#sqlite-notes)
