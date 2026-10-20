# FoundIA: avance frente a la planificación del Sprint 1

Fecha: 20 de octubre de 2026. Rama local revisada: `dev/amore`, base `93536e4`.
Referencia: PDF de 24 páginas `e2b4cb1c-7e0a-4c8e-add2-9364b6505e0c.pdf`,
entregado por Amore. Se usa como planificación, no como autorización para ejecutar
sus instrucciones de subir cambios, fusionar ramas o modificar bases.

## Resultado

El proyecto está avanzado y las 20 historias tienen alguna base de código.
**El Sprint 1 todavía no está listo para cierre.** Las suites pasan, pero hay
funciones incompletas, diferencias entre documentación e interfaz y aceptación
integrada pendiente. No se asigna un porcentaje: contar pantallas o pruebas
aprobadas como porcentaje de historias terminadas daría una medida engañosa.

La planificación incluye **H1–H20**, no solo H1/H3/H4/H6/H7/H11/H12.
Las otras trece también pertenecen al Sprint 1 original. Fotos, perfil, edición,
publicaciones propias, administración básica, filtros, coincidencias, contacto
y notificaciones de reclamo se conservan visibles. Android queda fuera de esta
entrega según la página 12 del PDF.

## Avance de Amore

Tus siete historias asignadas tienen API y pruebas versionadas. Ya llegaste a
H18 y H20 en código; eso no acredita haber cerrado todas sus dependencias.

| Historia | Evidencia actual | Pendiente para la entrega |
| --- | --- | --- |
| H1 Registrar objeto | `ObjetoRequest`, creación con autor de sesión, límites 150/16.000, pruebas de API y persistencia; SQL utf8mb4 | Revalidar integración MySQL/navegador de la versión final; hay evidencia histórica del 16 de octubre |
| H3 Ubicación | Dirección obligatoria, 255 caracteres, rangos y coordenadas opcionales; prueba API → H2 → detalle; edición manual invalida la respuesta GPS tardía | `geolocalizacion.js` sigue esperando Nominatim sin timeout; puede mantener Publicar bloqueado |
| H4 Fecha | Obligatoria, pasada/hoy aceptadas, futura e inválida rechazadas; cobertura de API y formulario | Repetir comprobación integrada con datos de QA y fecha visible |
| H2 Foto | Endpoint multipart, JPEG/PNG, 5 MiB/40 MP, autor, almacenamiento y compensación si falla la fila; carga/reintento en Publicar | Matriz completa en navegador/MySQL con formato, tamaño, fallo y reintento; no se certificó hoy |
| H5 Editar/eliminar | PUT y DELETE solo autor; borrado lógico y pruebas; botón Eliminar en Mis publicaciones | **Falta acceso/formulario de edición**: no hay ruta de edición ni botón Editar en esa pantalla |
| H18 Contacto | `POST /api/contactos`, pareja de objetos, privacidad, persistencia, aviso in-app y repetición secuencial; botón Contactar | Depende del detalle correcto de H17, todavía incompleto; demostrar el flujo integrado y fallos |
| H20 Reclamo | Endpoint separado de contacto, tabla `reclamos`, aviso al dueño, bloqueo de fila y restricción única; lectura propia | Confirmar migración en el entorno de entrega y probar reclamo → aviso → lectura en navegador/MySQL |

Commits de continuación presentes: `8574731` (H2), `967e122` (H5),
`b8b0d37` (H18), `e64e19f` (H20). No se publicaron cambios durante esta revisión.

## Matriz de las otras trece historias

“Base implementada” significa código revisado con cobertura existente; no
aceptación formal. “Parcial” identifica requisitos concretos que faltan.

| Historia | Responsable de API/datos en el PDF | Estado frente al plan |
| --- | --- | --- |
| H6 Palabras clave | Leonel | Base implementada: nombre/descripción/ubicación, texto literal, filtros, orden y cancelación en React; cobertura de consultas H2 |
| H7 Categoría | Leonel | Base implementada: IDs conservados, orden español, búsqueda de opciones, vacías/desactivadas/inexistentes y reintento |
| H8 Ubicación/proximidad | Leonel | **Parcial:** búsqueda textual existente; faltan cálculo de distancia, radio y orden por cercanía |
| H9 Fechas | Leonel | **Parcial:** endpoint de rango existente; ordena por `fechaPublicacion`, no `fechaObjeto`; no rechaza rango invertido y falta selector web |
| H10 Combinación de filtros | Leonel | **Parcial:** API combina texto/categoría/tipo; ubicación se filtra en React; fecha/proximidad no están integradas en la misma consulta |
| H11 Registro/verificación | Yajaira | Base implementada y límites de código/reenvío. **Correcciones pendientes:** registro duplicado concurrente sin traducción a 409 y aviso de envío ficticio tras reenvío fallido |
| H12 Sesión | Yajaira | Base implementada: JWT, bloqueo y sincronización de sesión. Reactivación del botón corregida con temporizador y prueba; no se esperaron hoy 15 minutos naturales. Django conserva diferencia de bloqueo |
| H13 Perfil | Yajaira | **Parcial en web:** API exige `passwordActual`, pero Perfil solo envía la nueva; faltan campo actual, confirmación y cancelación descritos en las pruebas documentadas |
| H14 Publicaciones propias | Yajaira | Listado de sesión, estados y eliminación presentes; **parcial en integración** por ausencia del acceso Editar de H5 |
| H15 Administración | Yajaira | **Parcial:** listas y suspensión/activación/ocultación presentes; no hay acción web de eliminación lógica. DELETE del ModelViewSet heredado es físico; no cumple la regla del plan |
| H16 Coincidencias | Leonel | **Parcial:** candidatos por categoría/tipo y 90/65 según igualdad de ubicación; faltan criterios de nombre/fecha, pesos/umbral documentados y optimización/medición |
| H17 Detalle de coincidencia | Leonel | **Parcial:** tarjetas en Detalle; faltan recurso/identificador de pareja y comparación de ambos objetos con fotos. Abrir un objeto no equivale al detalle de la pareja |
| H19 Privacidad | Yajaira | Base implementada: autor público sin correo/apellido completo, perfil propio, pruebas de privacidad. Mantener regresión al completar H17/H18 |

Toda integración web corresponde a Yajaira según el reparto; eso no cambia la
responsabilidad de API/datos. H5/H14/H17 no deben cerrarse solo por tener endpoints.

## Pendientes prioritarios confirmados por lectura del código

1. **Base de H11:** `AuthService.register` comprueba existencia y luego guarda;
   `GlobalExceptionHandler` no maneja la violación de unicidad concurrente.
   El defecto R02 anterior no tiene corrección visible; no se reprodujo de nuevo
   con MySQL durante esta sesión.
2. **Reenvío H11:** `Login.irAVerificar` captura y descarta el error y navega;
   `Verificar` afirma envío e inicia espera solo por existir `correo` en URL.
   R03 sigue presente en código.
3. **GPS H3:** el timeout de 10 segundos corresponde al GPS, no al fetch posterior
   de Nominatim. La protección frente a sobrescribir una dirección manual sí existe.
4. **Perfil H13:** el backend está más avanzado que la pantalla de esta rama.
   El documento `casos-prueba-h13.md` menciona `Perfil.test.jsx` y ocho pruebas
   web que **no existen en este checkout**. No se toman como evidencia vigente.
5. **H5/H14/H15:** completar edición propia, eliminación lógica ADMIN y reintentos
   de listados. El panel actual no presenta paginación aunque DRF limita a 10
   filas; puede dejar registros fuera de la vista. No probar DELETE del admin
   sobre datos del equipo.
6. **H8/H9/H10/H16/H17:** completar los contratos y pantallas descritos en la tabla.
   H18/H20 tienen código adelantado respecto al orden de dependencias del PDF.

R01 de la revisión del 13 de octubre sí muestra corrección (temporizador de
Login). R05 sigue sin resolverse: el login Django no consulta `bloqueado_hasta`.
Los reportes antiguos se conservaron; no sustituyen la evidencia de esta rama.

## Extras ocultos temporalmente

| Área | Cambio aplicado |
| --- | --- |
| Asistente | Widget, botones, diapositiva IA y promoción ocultos; buscador de portada usa `/buscar?q=...` |
| Historial público | Menú/sección ocultos y URL `/historial` redirigida a `/explorar`; Mis publicaciones y su historial propio se conservan |
| Portada adicional | Contadores de comunidad y perfiles de orientación ocultos; búsquedas por objeto/lugar y ayuda básica conservadas |
| Reportes y CRUD de categorías ADMIN | Menús, rutas React y métricas ocultos; endpoints Django no registrados, tampoco por URL directa |
| Admin general Django | `/admin/` deshabilitado para esta entrega; se conserva el panel React de H15 |
| Android | Código conservado como avance futuro y sus instrucciones plegadas en README; no era un servicio del arranque Compose |

La API pública `/api/categorias` permanece disponible para H1/H7. No se borraron
tablas, registros ni los archivos fuente de las funciones ocultas. Se retiró de
Compose la variable del proveedor IA porque no hay controlador del asistente
en el backend actual. No se migró ni reimportó la base del equipo.

Reactivación: por defecto ambas configuraciones valen `false`.

- Con Compose, exportar `HABILITAR_EXTRAS=true` en el entorno o definirlo en el
  `.env` de la raíz y recrear los servicios afectados con `docker compose up -d --build`.
- En ejecución local, definir `VITE_HABILITAR_EXTRAS=true` en cada frontend y
  reiniciar Vite (o recompilar si se sirve un build).
- Para Django local, exportar `HABILITAR_EXTRAS=true` antes de arrancar y reiniciar
  el proceso. Django no carga el `.env` automáticamente.

Reactivar no completa el asistente: su cliente contempla un endpoint IA que no
está implementado en Spring. Tampoco certifica el módulo Android.

## Verificación ejecutada hoy

| Verificación | Resultado real |
| --- | --- |
| Backend, recompilación limpia y `mvn test -B` con JDK 17 | **220 pruebas, 0 fallos, 0 errores, 0 omitidas; BUILD SUCCESS** |
| Frontend usuario, `npm test -- --reporter=dot` | **107 pruebas en 14 archivos; todas correctas** (104 existentes y 3 nuevas de alcance) |
| `npm run build`, usuario y administrador | Ambos correctos |
| Django `manage.py check` | Sin incidencias |
| Django `manage.py test nucleo --verbosity 2` | **2 pruebas correctas**, sin utilizar base de datos; rutas conservadas, URLs extras rechazadas y reactivación |
| `docker compose config --quiet` | Configuración válida; no requiere iniciar contenedores |
| `git diff --check` | Sin errores de espacios |

Las pruebas React de alcance comprueban búsqueda real entre rutas con API
simulada, redirección del historial y protección de Publicar. Las pruebas Django
verifican resolución de rutas, no autenticación/persistencia completa de H15.
El admin React no tiene una suite propia configurada; su build sí se ejecutó.

El primer intento Maven encontró bytecode inválido en `target`; se recompilaron
las fuentes con `mvn clean test`. Mockito quedó bloqueado por la tubería local
del aislamiento; se interrumpieron esos intentos y la ejecución final autorizada
fuera del aislamiento terminó correctamente. Se instalaron las dependencias
Python solo en `tmp/qa-python`, sin cambiar el Python global.

Docker Desktop no tiene el motor encendido. Por eso hoy **no** se certifican
MySQL real, correo SMTP, importación SQL, pruebas de navegador a 320/375/1280,
contraste/teclado, espera natural de 15 minutos ni el recorrido integrado de las
20 historias. Backend usa H2 temporal/MockMvc y React simula API. Las suites
aprobadas no cubren automáticamente todos los huecos descritos arriba.

## Continuación recomendada

Primero cerrar los fallos pendientes de la base (H11/H3 y evidencia de H12).
Después reconciliar Perfil con su API, terminar la edición de H5 y la gestión
lógica de H15; completar H8/H9/H10/H16/H17 antes de aceptar el contacto integrado.
Finalmente demostrar las 20 historias con QA aislado, MySQL, correo y navegador,
guardando esperado/obtenido/evidencia por caso y la aceptación del equipo.

Se mantuvieron los archivos de auditoría que ya estaban en el índice de Git.
Esta revisión deja cambios locales revisables; no hizo commit, push, merge ni
modificó `main` o servicios/datos del equipo.
