# Entrega de Leonel a Amore — sus ocho historias

## Qué se entrega

La parte de **API/datos y pruebas de Leonel** para **H21, H22, H23, H24, H26,
H27, H28 y H29**, según la distribución del PDF y la corrección de alcance del equipo.
No se adelanta el backend de las historias de Amore o Yajaira. No se cambian PDF,
Word, horas, main ni ramas ajenas.

Leonel termina toda esta parte y entrega el conjunto a Amore. Amore realiza el
móvil correspondiente y sus tareas; después entrega a Yajaira para web. Leonel
recibe el sprint integrado para revisarlo y realizar el único cierre final en main.
No se hace ida/vuelta de ramas por cada historia.

## API de estas ocho historias

| Historia | Entrega de Leonel | Integración siguiente |
| --- | --- | --- |
| H21 | Descarte propio, persistente e idempotente; no reaparece en sugerencias | Confirmación/cancelación y refresco en los clientes |
| H22 | Recuperación atómica del perdido, fecha UTC y protección del hallazgo ajeno | Confirmar y mostrar éxito solo tras respuesta correcta |
| H23 | Avisos propios de parejas nuevas, lectura y eventos durables con reintentos | Bandeja y navegación a la pareja disponible |
| H24 | Tres preferencias propias: coincidencias, contactos y reclamos | Ajustes con guardado real y errores visibles |
| H26 | Crear, editar y desactivar categorías conservando IDs y publicaciones | Gestión administrativa web; no consola ADMIN Android |
| H27 | Denuncia con emisor de la sesión, motivos válidos y control de duplicados | Formulario, cancelar antes del envío y confirmación real |
| H28 | Consultar/resolver reportes con actor y fecha del servidor; repetición segura | Revisión y decisión administrativa web |
| H29 | Conteos de perdidos, encontrados y recuperados con rango de publicación UTC | Tablero web con carga, vacío y error diferenciados |

Contratos y cuerpos JSON: [API de Leonel](../api/sprint2-leonel.md).
Detalle: [H21/H22](../api/endpoints.md), [H26](../api/categorias-sprint2.md),
[H27](../api/reportar-publicacion-sprint2.md), [H28](../api/resolucion-reportes-sprint2.md),
[H29](../api/metricas-sprint2.md).

Spring Boot `:8081` es la API común web/móvil; Django `:8000` es administración.
La identidad siempre sale del JWT. Manejar 400/401/403/404/409 y 423 de login;
no mostrar éxito si se canceló o falló la red. Reconsultar/reintentar según la
idempotencia documentada si se pierde la respuesta.

## Antes de conectar los clientes

1. Recibir el cierre de esta entrega desde `origin/dev/leonel`. Revisar el estado
   local y los conflictos; no forzar ramas ni descartar cambios ajenos.
2. Amore desarrolla móvil en `mobile/android` e integra su parte en `dev/amore`.
   Después Yajaira toma la entrega, hace web en `frontend/web` e integra en `dev/yajaira`.
   Solo Leonel revisa el resultado final y lo lleva a main.
3. Usar la API y MySQL del servidor, no otra base o backend para Android.
   En el emulador, el host del PC suele ser `10.0.2.2`, no `localhost`.
   Configurar el mismo `JWT_SECRET` por entorno en ambos backends; no commitear secretos.
4. La BD compartida ya está respaldada y su esquema fue verificado el 2026-10-26
   (las cinco migraciones se reejecutaron de forma idempotente). No volver a
   aplicarlas ni reimportar `script.sql`/`seed.sql`; no usar `docker compose down -v`.
   Para otro entorno, revisar respaldo y autorizar el destino antes de migrar.
5. En H24 enviar los tres booleanos: `coincidencias`, `contactos`, `reclamos`.
   No hay tipos futuros ni envíos masivos en esta entrega.
6. En H29 `recuperados` es subconjunto de `perdidos`: no sumarlo como otro grupo
   excluyente. Los nuevos conteos filtrados están en `objetos`; las tarjetas
   históricas se conservan como totales generales por compatibilidad.

## Estado de la base de datos compartida

La base del proyecto quedó preparada para que Amore pruebe la integración:

- MySQL **8.0.46**, base `objetos_perdidos_db`, volumen
  `proyectofoundia_mysql_data`.
- Respaldo previo verificado y guardado fuera del repositorio: físico del volumen
  (SHA-256 `3e62657c342709755630e06a6bf3dcee8c4fdfb984a7a0b94a0893c5aeceffd0`)
  y lógico de la base (SHA-256
  `403d9e16e5563bc3c0ee0f0ed0bda7795285bb731348686028039c9ebb7c3190`).
- **18 tablas existentes conservadas**. La base ya contenía las tablas
  `avisos_coincidencia`, `eventos_coincidencia` y `preferencias_notificacion`, y
  las columnas de H22/H28; las cinco migraciones se reejecutaron y resultaron
  idempotentes, sin cambios de esquema ni de filas. Las filas y valores previos
  quedaron intactos: 5 usuarios y 7 objetos.
- No se reimportó `script.sql` ni `seed.sql` y no se usó `docker compose down -v`.
- Backend levantado solo en localhost: Spring `:8081`, Django `:8000`, Mailpit `:8025`.
  Los contenedores de esta verificación son `foundia-mysql`, `foundia-api`,
  `foundia-admin-api` y `foundia-mailpit`; se detienen con `docker stop` sin borrar
  los volúmenes.
- **22 pruebas HTTP reales correctas** sobre las ocho historias, con login real de
  usuario y administrador. Incluyen una escritura H24 verificada y restaurada;
  las filas y valores previos quedaron sin cambios.
- JAR ejecutado: SHA-256
  `343c62c258985fc594f11d94af3e8725f8cdec203977e65eba45df480fa6a8c3`,
  el mismo de la validación.

Evidencia completa: `docs/pruebas/leonel-ocho-historias-resultados.json`.

## Historial de entrega

Los commits de `dev/leonel` están agrupados en el orden de las historias:

| Orden | Commit | Contenido |
| --- | --- | --- |
| 1 | `5f183d8` | H21: descarte con permisos, persistencia y puntuación coherente |
| 2 | `7876490` | H22: recuperación atómica del perdido propio |
| 3 | `d8b6690` | H23: avisos de parejas nuevas con eventos durables y reintentos |
| 4 | `eddc0c8` | H24: preferencias de coincidencias, contactos y reclamos |
| 5 | `1ff4e8f` | H26: categorías validadas y desactivación conservando asociaciones |
| 6 | `c9a01e2` | H27: denuncias con identidad real y control de duplicados |
| 7 | `8a0199f` | H28: resolución de reportes con auditoría y repetición segura |
| 8 | `7d042f9` | H29: conteos administrativos con rango y estados coherentes |
| 9 | `ffbaa61` | Pruebas de las ocho historias y guarda de alcance |

El cierre documental queda en el HEAD de `dev/leonel`. No hay commits de H25 ni
de historias de otras responsables.

## Validación reproducible

Resultados de esta entrega, sin fallos ni pruebas omitidas:

- **391** pruebas de regresión Spring con `clean verify` y JAR generado.
- **27** pruebas Django en SQLite en memoria.
- MySQL **8.4.6** nuevo y aislado: **82** pruebas Spring y **27** Django.
- **Cinco migraciones, dos pasadas** en QA aislado: antecedentes conservados y
  esquema migrado equivalente al nuevo; el servidor temporal quedó detenido.
- **BD compartida**: respaldo físico y lógico, cinco migraciones aplicadas y
  **22 pruebas HTTP reales** correctas, sin cambiar filas existentes.
- Guarda de alcance: acepta esta entrega y rechaza cambios de historias ajenas.

```powershell
# Control exclusivo del alcance de ESTA entrega, desde la raíz y en dev/leonel
python scripts/verificar-alcance-leonel.py
# backend/springboot: regresión completa en H2 temporal y generación del JAR
mvn clean verify
# backend/django-admin: SQLite en memoria
python manage.py test nucleo --settings=config.settings_test --noinput
# Desde la raíz, con requirements.txt instalado en el Python virtual de Django
python scripts/pruebas-api-leonel.py --mysql-basedir "C:\ruta\mysql-8.4.6-winx64" --workdir "C:\QA\leonel-NUEVO"
```

El ejecutor MySQL exige directorio nuevo fuera del proyecto y usa únicamente
localhost, puerto aislado, base aleatoria y token de guarda. Prueba las cinco
migraciones dos veces sobre una instalación nueva de Sprint 1 con datos ficticios,
verifica que no inventen antecedentes y contrasta el esquema migrado con el nuevo.
Ejecuta las pruebas de API de estas ocho historias y detiene su propio servidor.
Nunca se conecta a XAMPP, Docker o la BD habitual del equipo.

Los resultados de esta revisión se registran en
`docs/pruebas/leonel-ocho-historias-resultados.json`, no se reutilizan cifras de
entregas descartadas. Esto no acredita todavía pantallas web, APK ni aceptación
del sprint completo; cada responsable guarda la evidencia de su parte.
