# Integración del Sprint 1 en dev/amore

Fecha: 20 de octubre de 2026. Responsable de esta integración: Amore.

## Ramas incorporadas

Se actualizaron las referencias de GitHub antes de trabajar. La revisión
anterior usó el checkout `93536e4` y no acreditaba avances que todavía no se
habían integrado en él.

- `origin/dev/yajaira` y `origin/frontend/web`, ambas en `d5e6cf2`: perfil,
  publicaciones propias, edición, administración, fotos, contacto y notificaciones.
- `origin/dev/leonel`, en `1912710`: API/datos de H8, H9, H10, H16 y H17.
- Destino de todos los cambios nuevos: `dev/amore`. No se modificó `main`.

Se conservaron los commits originales. El conflicto de `nucleo/tests.py` se
resolvió manteniendo las pruebas administrativas de Yajaira y pasando las
pruebas de alcance a `nucleo/test_alcance.py`; no se descartaron suites.

## Parte de Yajaira integrada y completada

| Área | Resultado en esta rama |
| --- | --- |
| H11 Registro y verificación | Validaciones y límites del servidor conservados; registro simultáneo responde 201/409. El error de reenvío se muestra sin anunciar éxito ni navegar a una confirmación falsa. Un enlace con correo, por sí solo, no anuncia que se envió un código. |
| H12 Sesión | JWT, cierre entre pestañas y temporizador del login conservados. Django comparte contador y bloqueo: cinco fallos bloquean, un bloqueo vigente rechaza el acceso y el token anterior, y al vencer se permite entrar. |
| H13 Perfil | Pantalla de Yajaira integrada: contraseña actual, confirmación de la nueva, errores por campo, Cancelar y sincronización de los datos visibles. |
| H14/H5 Publicaciones propias | Listado propio con foto/estado, enlace Editar, formulario precargado y confirmación accesible para eliminación lógica. |
| H15 Administración | Listados completos, confirmaciones, suspensión/activación y ocultación/eliminación lógica, sin borrar físicamente filas. Cerrar sesión en otra pestaña también retira la vista del panel. |
| H2 Fotos | Selección, vista previa, quitar selección, progreso, límites y reintento sobre la publicación ya creada, sin duplicarla. |
| H8/H9/H10 Búsqueda | Texto, categoría, tipo, ubicación, fechas inclusivas, coordenadas y radio en una petición a la API de Leonel. Filtros activos, quitar uno, limpiar, URL y Atrás. Distancias calculadas solo con coordenadas reales, con el mismo radio terrestre de la API. |
| H16/H17 Coincidencias | Enlace con `coincidenciaId`, comparación de ambos objetos y fotos, porcentaje/nivel y desglose de criterios. Reintento, pareja retirada y alternativa Sin foto. |
| H18 Contacto | Acción desde la pareja correcta, autor del perdido, confirmación solo tras respuesta del servidor y reintento. La persistencia usa el mismo motor y umbral de Leonel; se retiró el 90/65 fijo. |
| H19 Privacidad | Se conservan los DTO públicos y las pruebas; la comparación solo muestra los campos públicos del autor. |
| H20 Notificaciones | Reclamo separado del contacto, confirmación, avisos propios y lectura persistida; contador y tipos conservados. |

También se limitó a cinco segundos la geocodificación posterior al GPS. Si el
proveedor no responde, Publicar puede continuar con coordenadas. La edición
manual invalida respuestas tardías tanto en Publicar como en los filtros.

Los códigos y bloqueos nuevos usan UTC en Spring y Django. No se modificaron
fechas de cuentas de la base compartida. Al actualizar un despliegue anterior
que almacenaba hora local, conviene hacerlo sin códigos ni bloqueos pendientes
(tienen hasta quince minutos de vigencia), para evitar interpretar un valor
antiguo con la nueva convención.

## Commits importantes

| Commit | Descripción |
| --- | --- |
| `988aa79` | Ocultar funciones fuera del sprint 1 y registrar la revisión |
| `188ed42` | Integrar las pantallas y la administración de Yajaira |
| `5b73ccf` | Integrar los filtros y las coincidencias de Leonel |
| `8be18d1` | Completar la pantalla de búsqueda con fechas y cercanía |
| `4e4cd4f` | Limitar la espera del GPS y permitir continuar la publicación |
| `28ca76d` | Agregar la comparación de coincidencias y el contacto desde la pareja |
| `1d42303` | Corregir el registro simultáneo, el reenvío y los bloqueos de acceso |
| `45126de` | Usar la puntuación de Leonel al iniciar un contacto |
| `fcf5308` | Evitar respuestas tardías y sincronizar el cierre del panel |
| `52b5bcc` | Corregir la columna de bloqueo y ajustar el acceso del panel al sprint 1 |

Los cambios se publicaron en GitHub en `dev/amore`; las otras ramas de trabajo
siguen conservando su historial. La documentación final se guarda aparte.

## Pruebas automatizadas finales

| Componente | Comando | Resultado |
| --- | --- | --- |
| Spring Boot, Java 17 | `mvn clean verify -B` | **311 pruebas, cero fallos, errores u omitidas; JAR generado** |
| React usuario | `npm test -- --reporter=dot` | **157 pruebas, 21 archivos, todas correctas** |
| Django | `manage.py test nucleo --settings=config.settings_test` | **17 pruebas correctas**, SQLite temporal |
| React usuario y administrador | `npm run build` | Ambos correctos |

Incluyen nuevo registro simultáneo con dos hilos, traducción de unicidad a 409,
rechazo de otras violaciones de integridad, puntuación de contacto y rechazo
bajo umbral, reenvío fallido, geocodificación lenta, filtros completos,
restauración de URL, GPS tardío, comparación de pareja y recuperación de errores.

Las integraciones Java utilizan H2 y MockMvc; las pruebas de componentes React
simulan la API. No se presentan como pruebas MySQL o navegador.

## Navegador, MySQL y SMTP reales

La repetición final del script pasó **41 comprobaciones agrupadas**, con Edge
headless, los builds de producción, Spring/Django reales y una MySQL 8 nueva
del proyecto Docker `foundia-amore-qa-20261020-b8a7`. No son 41 historias ni se
suman a las suites como casos unitarios únicos.

- Registro → SMTP Mailpit → código → verificación → login; dos registros
  simultáneos con el mismo correo devuelven 201/409 en MySQL real.
- Perfil: guardar conserva tildes y persiste; Cancelar restaura el último valor.
- Publicación por formulario con GPS emulado, fecha y foto PNG real, edición
  desde Mis publicaciones y consulta posterior de los datos persistidos.
- Todos los filtros en una petición, distancia cero con coordenadas conocidas,
  teclado para quitar cercanía y Atrás para restaurar URL y controles.
- Pareja con fotos subidas en ambos objetos, 100 puntos, privacidad, contacto,
  reclamo separado y lectura persistida del aviso en la sesión destinataria.
- Buscar, comparación, perfil, Publicar y Mis publicaciones sin desborde a
  320/375/1280 px; panel administrativo comprobado en esos mismos tamaños.
- Paginación administrativa: aparecen registros de páginas posteriores.
  Ocultar/mostrar funciona; cancelar la eliminación conserva la publicación y
  confirmar la elimina lógicamente, manteniéndola en el historial del dueño.
- Cerrar sesión en otra pestaña retira la ruta protegida del usuario y el panel.
- Cinco fallos en Spring bloquean también el login de Django y su token anterior,
  contra la misma fila MySQL y con el vencimiento en UTC de la versión final.
- URLs de extras deshabilitadas y ningún error JavaScript no controlado durante
  el recorrido.

Mediciones de contraste de textos seleccionados:

| Pantalla / tema | Mediciones | Menor relación obtenida |
| --- | ---: | ---: |
| Buscar claro | 18 | 4,51:1 |
| Comparación claro | 15 | 5,94:1 |
| Buscar oscuro | 17 | 6,09:1 |
| Comparación oscuro | 10 | 8,16:1 |

El cálculo usa colores CSS compuestos sobre fondos resolubles y verifica
4,5:1 para texto normal y 3:1 para grande. Omite fondos con imágenes/degradados
sin resolver; estas mediciones y las pruebas de teclado no son una auditoría
WCAG completa. Las capturas usan preferencia de movimiento reducido para
registrar el contenido sin capturar una animación a medio terminar.

Resultados y capturas:

- [Casos, resultados y mediciones](evidencias-integracion-amore-2026-10-20/navegador-mysql.json).
- [Búsqueda a 375 px](evidencias-integracion-amore-2026-10-20/buscar-375.png).
- [Comparación a 375 px](evidencias-integracion-amore-2026-10-20/comparacion-375.png).
- [Listado administrativo](evidencias-integracion-amore-2026-10-20/administracion-1280.png).

La revisión MySQL detectó y permitió corregir el mapeo del contador Django a
`intentos_fallidos`; SQLite por sí sola no detectaba esa diferencia. Hubo
repeticiones del runner para ajustar selectores accesibles y esperar las
respuestas antes de comprobar persistencia. El JSON enlazado corresponde a la
repetición final completa, no a una mezcla de resultados parciales.

El GPS y Nominatim se emulan en el navegador; no se certifican sensores físicos
ni disponibilidad del proveedor. Los PNG son imágenes sintéticas de QA. No se
usaron datos personales reales ni se enviaron mensajes a destinatarios externos.

## Bloqueo natural de quince minutos

La cuenta ficticia recibió cuatro 401 y después un 423. Se dejó abierta la
pantalla, se escribió la contraseña correcta y se esperó sin recargar ni cambiar
el correo, sin adelantar el reloj o editar el bloqueo en la base.

- Inicio: 13:45:39 del 20 de octubre (America/Lima).
- Acceso correcto: 14:00:41 del mismo día.
- Tiempo real registrado: **902 segundos**.
- Resultado: Entrar se reactivó y el login terminó correctamente.

[Resultado crudo de la espera natural](evidencias-integracion-amore-2026-10-20/bloqueo-natural.json)
y [captura del bloqueo vigente](evidencias-integracion-amore-2026-10-20/bloqueo-vigente.png).

Esta prueba se ejecutó sobre el artefacto de QA anterior a fijar explícitamente
UTC en el reloj de autenticación; el formulario y la duración de quince minutos
son los mismos. La versión con UTC se comprueba con las suites finales y la
regresión integrada posterior. No se presenta esta espera como una prueba nueva
realizada después de ese cambio.

## Repetir el navegador sobre QA

Los scripts versionados son `scripts/pruebas-web-sprint1.cjs` y
`scripts/prueba-bloqueo-natural.cjs`. Requieren Node, Playwright y Microsoft Edge.
Los puertos de API están fijados a los de QA y se rechaza usar los habituales.

Preparar **una MySQL nueva y separada** y Mailpit. Importar `script.sql` y
`seed.sql` únicamente en esa base vacía; ambos scripts contienen operaciones
destructivas y no se deben reimportar en la base del equipo.

| Servicio | Puerto de QA |
| --- | ---: |
| MySQL | 13368 |
| SMTP / bandeja Mailpit | 11368 / 18368 |
| Spring / Django | 18081 / 18000 |
| Preview usuario / administrador | 15173 / 15174 |

Configurar las dos APIs con el mismo `JWT_SECRET` temporal y las credenciales
de esa MySQL, usando las variables del README. Pasar `MAIL_PORT=11368`,
`MAIL_HOST=127.0.0.1` y permitir los orígenes de QA por CORS. Usar un directorio
de fotos temporal. No ejecutar migraciones Django sobre las tablas de negocio.

Compilar usuario con `VITE_API_URL=http://localhost:18081` y admin con
`VITE_API_URL=http://localhost:18000`, ambos con extras desactivados. Servir los
builds en sus puertos de QA. Los builds normales usan nuevamente 8081/8000.

Desde la raíz, con Playwright instalado y sus dependencias disponibles:

```powershell
node scripts/pruebas-web-sprint1.cjs
node scripts/prueba-bloqueo-natural.cjs
```

Si Playwright no está en las dependencias de la raíz, `PLAYWRIGHT_MODULE` acepta
la ruta a su paquete. `QA_EVIDENCIAS` permite elegir la carpeta de resultados;
el valor predeterminado está en `tmp/`. Los scripts solo utilizan cuentas
`@prueba.invalid`, objetos de prueba y la bandeja SMTP local; no envían correo
a personas. No guardan tokens ni contraseñas en los JSON de evidencia.

## Alcance conservado

Los extras continúan ocultos por defecto. El catálogo público de categorías,
perfil, publicaciones propias, notificaciones y todas las funciones H1–H20 se
mantienen. Android se conserva como avance futuro. El cierre formal del sprint
y la aceptación del Product Owner corresponden al equipo; una suite aprobada
no sustituye esa decisión.
