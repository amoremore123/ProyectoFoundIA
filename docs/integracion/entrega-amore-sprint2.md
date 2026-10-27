# Entrega de Amore: API propia y Android del sprint 2

Cierre de implementación: 27 de octubre de 2026. Reparto de referencia: PDF final
«FoundIA-Sprint-2-Reparto-funcional-FINALXDDDDDDD», fechado el 26/10/2026.

## Parte entregada

| Historia | Trabajo de Amore | Estado y evidencia |
| --- | --- | --- |
| H21 | Sesión cifrada persistente, acceso/401/423, publicaciones propias, comparar y descartar con confirmación | Implementado; cancelar conserva la pareja y el descarte real persiste |
| H22 | Confirmar recuperación propia; éxito y fecha solo tras respuesta; volver a consultar listas | Implementado; perdido recuperado y hallazgo ajeno intacto en API real |
| H23 | Bandeja propia, lectura y apertura de pareja; carga, vacío, error y reintento | Implementado; lectura guardada y destino revalidado, incluso si fue retirado |
| H24 | Tres tipos de aviso; guardar, cancelar y recargar los valores del servidor | Implementado; persistencia real y caída/reintento de API comprobados |
| H27 | Denunciar desde detalle, motivos, explicación para OTRO, cancelar y confirmar; control de repetición | Implementado; denuncia creada desde APK; errores no se transforman en éxito |
| H33 | API de documentos; aceptación expresa/versionada/fechada; registro y verificación Android | Implementado con textos del proyecto pedidos por Amore, pendientes de revisión institucional; apoyo web de Yajaira pendiente |
| H34 | API y Android para solicitud propia de eliminación, confirmación/cancelar y estado persistente | Implementado; no ejecuta ni anuncia borrado; apoyo web de Yajaira pendiente |
| H35 | Pantallas de solicitud/cambio y recepción de enlaces; exigir nuevo acceso después de un cambio válido | Preparado en Android; API de Yajaira aún ausente, así que no se acredita el cambio real de contraseña |
| H39 | Compartir URL pública con selector del sistema y revalidar disponibilidad | Implementado en Android; URL abierta en Chrome sin sesión y retirada comprobada |

La parte Android se desarrolló con commits propios en `mobile/android`, partiendo
de la entrega recibida, y se integró en `dev/amore`. H33/H34 de API se desarrollaron
en `dev/amore`. No se duplicó la autoría con cherry-picks ni se trabajó en main.
Las tareas web y las cuatro historias propias de Yajaira siguen a cargo de ella;
este documento no declara aceptadas las 14 historias del sprint completo.

## Pruebas y artefactos

- Java: **403 pruebas** de regresión correctas, sin fallos, errores ni omitidas.
  Tras normalizar la huella documental entre Windows y Linux se repitieron las
  **8 pruebas afectadas**, correctas; no se suman otra vez al total.
- Android: **8 pruebas unitarias** de contratos REST, manejo de errores y enlaces.
- Emulador Medium Phone: **29 comprobaciones** con APK instalado, Spring Boot real,
  MySQL 8 separado y Mailpit. Incluyen aceptación desmarcada, registro/código
  inválido/válido, sesión persistente, cancelar/confirmar, lectura, preferencias,
  denuncia, enlace público/retirado, caída/reintento, JWT vencido y bloqueo 423.
- MySQL: **9 comprobaciones** de aceptación almacenada, permisos, concurrencia,
  conservación de datos y migraciones incrementales aplicadas dos veces. Una
  segunda base QA con una fila previa verificó que no se inventa su consentimiento.
- Se generó APK debug instalable y se ejecutó la revisión estática Android: cero
  errores y 13 advertencias de estilo/configuración/dependencias. Los
  detalles y las huellas de los artefactos se guardan en la evidencia estructurada.

Evidencias: [capturas y resultados](../pruebas/evidencias-amore-sprint2-2026-10-26/).
El nombre de la carpeta conserva la fecha de inicio de las pruebas; el cierre es
el 27/10. El emulador muestra hora UTC y las fechas del servidor también usan UTC.

Las 29 comprobaciones funcionales cubren el código final de los flujos. La última
corrección fue de presentación: quitar colores de texto heredados que reducían el
contraste de los botones. Se recompiló, se repitieron las ocho pruebas unitarias y
se revisó la captura `h33-contraste-final.png`. Las capturas anteriores muestran el
estado funcional observado antes de ese ajuste de colores. No se acredita una
certificación WCAG ni una sesión completa hablada de TalkBack o un teléfono físico.

El vencimiento se comprobó con `JWT_EXPIRATION_MS=15000` **solo en QA**, esperando
naturalmente 17 segundos; no se cambiaron relojes ni fechas de la base para
simularlo. La configuración habitual mantiene su duración de 24 horas.

El APK local de entrega queda en `output/android/FoundIA-Sprint2-Amore.apk`.
Los binarios APK se excluyen de Git; el repositorio contiene el código y las pruebas.
Por defecto usa API `http://10.0.2.2:8081/` y web `http://10.0.2.2:5173/`, direcciones
del PC desde el emulador. Para un teléfono u otro despliegue, recompilar con
`-PfoundiaApiUrl=.../` y `-PfoundiaWebUrl=.../` usando orígenes realmente accesibles.
Compartir una dirección de emulador no la convierte en un sitio público de Internet.

## Qué recibe Yajaira

1. H33/H34: conectar la web al [contrato de cuenta](../api/cuenta-amore-sprint2.md).
   El formulario web anterior no envía aceptación: **el registro web será rechazado
   por la nueva API hasta que envíe `aceptaTerminos:true` y la versión presentada**.
   El backend no acepta omitir la nueva condición para conservar compatibilidad.
2. H21/H22: web con confirmación/cancelar; refrescar solo tras éxito y manejar
   400/401/403/404/409. Las API recibidas de Leonel conservan sus contratos.
3. H23/H24/H27: conectar los avisos y preferencias, navegación al par disponible y
   denuncia con identidad exclusivamente del JWT.
4. H35: acordar e implementar la API que consumen las pantallas Android:
   `POST /api/auth/recuperar-password` con `{"correo":"..."}` y
   `POST /api/auth/restablecer-password` con `{"token":"...","password":"..."}`.
   Es un **contrato propuesto para integración**, no un endpoint que ya exista.
   Ajustar juntos API y Retrofit si se decide otra ruta/campo. Respuestas de solicitud
   no enumerables; token de un uso con caducidad, cambio atómico y restricciones de
   cuenta conservadas. Probar correo, token vencido/repetido y nuevo login cuando
   exista la API; hoy esas comprobaciones integrales están pendientes.
5. Enlace Android admitido: `foundia://restablecer?token=...`, con un único token
   de 16 a 2048 caracteres alfanuméricos/base64url. El manifest recibe ese esquema
   sin reclamar dominios ajenos. También hay validación de URL web del origen
   configurado `/restablecer-password?token=...`; para abrirla automáticamente
   desde Android harán falta el dominio definitivo, su asociación y el intent-filter.
   El backend no debe incluir ni registrar el JWT de sesión en ese enlace.
6. H39: usar `/objeto/{id}`, ruta pública existente de la web. El selector de Android
   solo recibe la URL; cerrar/cancelar nunca se anuncia como un envío completado.
7. H26/H28/H29 y sus propias H30/H31 siguen siendo web/API. El antiguo interruptor
   general de extras también conserva funciones de otros sprints: no usarlo como
   prueba de que todas pertenecen a Sprint 2. Android no añade consola administrativa.

## Despliegue y datos

La BD del equipo **no se modificó** durante esta entrega. Se usaron únicamente los
puertos QA 18082/13369/18369/15175 y el proyecto Docker
`foundia-amore-s2-qa-20261026`; no se reimportaron tablas sobre datos compartidos.

Antes de arrancar esta API en un entorno existente, respaldar y comprobar el
destino, aplicar las dos migraciones H33 y H34 y conectar el registro web. No
usar `script.sql` o `seed.sql` sobre esa base. Los textos son una versión del
proyecto; institución, responsable, canal, conservación y proveedores deberán
ser definidos y revisados antes de un uso institucional con datos reales.

## Reproducir las comprobaciones

```powershell
# Java, desde backend/springboot; Java 17 y Maven
mvn verify
# Android, desde movil/android; Java 17 y SDK 36
gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
# QA aislado desde la raíz; nunca apuntar a la BD del equipo
docker compose -f scripts/qa-amore-sprint2.compose.yml up -d --wait
```

Arrancar el JAR en 18082 con MySQL QA 13369 (`objetos_perdidos_db`, root,
`qa-amore-s2`), correo 11369 y JWT_SECRET de pruebas igual durante todas las fases.
El correo visible está en 18369. Conservar su PID en `tmp/api-amore-s2.pid`; el
último ejecutor comprueba su argumento `--server.port=18082` antes de detenerlo.
Configurar CORS para `http://10.0.2.2:15175`, `http://127.0.0.1:15175` y
`http://localhost:15175`; servir la web QA en 15175 con API 10.0.2.2:18082.

Compilar el APK QA con `-PfoundiaApiUrl=http://10.0.2.2:18082/` y
`-PfoundiaWebUrl=http://10.0.2.2:15175/`, instalar en un emulador **dedicado y
prescindible** en emulator-5554 y marcarlo con
`adb -s emulator-5554 shell setprop debug.foundia.qa amore-sprint2-20261026`.
El ejecutor principal borra solo los datos de FoundIA en ese emulador marcado.

Ejecutar `python scripts/pruebas-android-amore.py preparar` y después la fase
`android`. Ejecutar `python scripts/pruebas-cuenta-amore-mysql.py` antes de retirar
publicaciones en las fases adicionales. Chrome del emulador debe estar preparado
sin cuenta y sin sesión web de FoundIA. La fase
`python scripts/pruebas-android-amore-final.py` comprueba enlace retirado y caída
real; después reiniciar la API con JWT de 15 segundos y ejecutar
`python scripts/pruebas-android-amore-sesion.py`. Estas fases no son independientes:
usar una fixture nueva para repetirlas desde el principio. Nunca ejecutar sobre
un dispositivo personal con datos de usuario.

Al terminar, detener únicamente los procesos QA creados y ejecutar
`docker compose -f scripts/qa-amore-sprint2.compose.yml down`, sin borrar volúmenes.
La aceptación del sprint completo y su integración en main siguen pendientes de
las partes de Yajaira y de la revisión final de Leonel.
