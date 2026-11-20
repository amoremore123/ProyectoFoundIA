# Entrega de Leonel — Sprint 3

Documento: 19/11/2026. Reparto de referencia: PDF «FoundIA-Sprint-3-Reparto» del 30/10/2026.

| Rama | Contenido |
| --- | --- |
| `dev/leonel` | API/datos y pruebas de mis 8 historias, un commit por historia, más las correcciones de H38 y H53 |
| `main` | Contiene los 11 commits de `dev/leonel` (hasta `3942c88`) junto con la web y Android del equipo |

No se hizo push forzado. La web de mis historias es de Yajaira y la capa Android, de Amore
(ver [entrega de Yajaira](entrega-yajaira-sprint3.md) y [entrega de Amore](entrega-amore-sprint3.md)).

## Mis ocho historias

Orden de ejecución: H36 → H41 → H43 → H45 → H32 → H37 → H38 → H53.

| Historia | API / datos | Commit | Estado |
| --- | --- | --- | --- |
| H36 Rol Biblioteca | Roles `USUARIO`, `BIBLIOTECA` y `ADMIN`. Solo un ADMIN activo cambia roles, con confirmación y auditoría en `cambios_rol`. Se protege al propio administrador y al último ADMIN. BIBLIOTECA solo ve custodia y reclamos | `cd464f9` | Terminada |
| H41 Custodia y etiqueta QR | Estado `EN_CUSTODIA`, código `FND-AAAA-####`, token QR firmado sin datos personales, etiqueta y constancia en PDF (`/api/admin/custodias/`) | `b4aacbe` | Terminada |
| H43 Resolver reclamos | Aceptar o rechazar con motivo, responsable y fecha. QR de retiro de un solo uso y 7 días. Bloqueo de fila contra doble resolución y reclamos rivales (`/api/admin/reclamos/`) | `26d1702` | Terminada |
| H45 Asistente | `POST /api/asistente/interpretar` con motor local de respaldo, límite por minuto y proveedor de IA configurable por variables de entorno | `3e9acce` | Terminada. Mi entrega verificó el motor local; el proveedor externo compatible con OpenAI (Groq por defecto en `application.yml`, trabajo de Yajaira) respondió 19 de 20 frases según [h45-20-frases.md](../pruebas/h45-20-frases.md) |
| H32 Reportes | `GET /api/admin/dashboard/exportar.xlsx` y `exportar.pdf`, solo ADMIN, mismas cifras y rango del tablero. Excel con hojas `Resumen`, `Categorías` y `Ubicaciones` | `f6bd781` | Terminada |
| H37 Cierre de sesión | `logout` y `logout-all` en Spring y Django. JWT con `jti` y `ver`; tokens revocados en `tokens_revocados` y `usuarios.sesion_version` | `126a052` | Terminada |
| H38 Respaldos | `scripts/respaldo.sh` (mysqldump, gzip, SHA-256, retención), servicio `respaldo` en Compose, registro en `respaldos` y `GET /api/admin/respaldos/` y `ultimo/`. Restauración solo en una base aislada | `10e9621`, `a7926f4`, `0b61a33` | Terminada |
| H53 Recordatorios | Objetos `PERDIDO` + `ACTIVO` sin recuperar con 7 días o más; un aviso `RECORDATORIO_OBJETO` por objeto y mes; preferencia `recordatorios` | `fb0a469`, `3942c88` | Terminada |

## Contratos

[H36](../api/sprint3-h36.md), [H41](../api/sprint3-h41.md), [H43](../api/sprint3-h43.md),
[H45](../api/sprint3-h45.md), [H32](../api/sprint3-h32.md), [H37](../api/sprint3-h37.md),
[H38](../api/sprint3-h38.md) y [H53](../api/sprint3-h53.md).

## Base de datos

Migraciones idempotentes de mis historias, todas incluidas también en `script.sql`:

- `database/migracion_h36_rol_biblioteca.sql`
- `database/migracion_h37_cierre_sesion.sql`
- `database/migracion_h38_respaldos.sql`
- `database/migracion_h41_custodia.sql`
- `database/migracion_h43_reclamos_custodia.sql`
- `database/migracion_h53_recordatorios.sql`

Para una base existente se aplican con un respaldo previo. Nunca `docker compose down -v` ni
reimportar `script.sql` o `seed.sql` sobre datos reales.

## Validación

Resultados del 10/11/2026 sobre `dev/leonel` (`3942c88`):

| Capa | Resultado |
| --- | --- |
| Spring | 448 pruebas, 0 fallos y 0 errores |
| Django | 100 pruebas OK |
| H53 | 3 pruebas propias y 14 de preferencias (H24) como regresión |
| Compose | `docker compose config --quiet` correcto |

Verificación con Docker y MySQL 8.0.46 en un volumen nuevo (20 tablas, 4 usuarios, 8 categorías y 6 objetos):

| Historia | Comprobado |
| --- | --- |
| H36 | Una usuaria pasó a BIBLIOTECA con auditoría: custodia `200`; tablero, usuarios y respaldos `403`. Después se devolvió a USUARIO |
| H41 | Custodia `FND-2026-0001`, PDF de etiqueta y constancia válidos y QR verificado |
| H43 | Reclamo aceptado, QR de 7 días, retiro completo y segundo retiro rechazado con `409` |
| H45 | Respuesta con `fuente: local` y resumen propio |
| H32 | Excel y PDF reales; rango inválido `400` y sin token `401` |
| H37 | `logout` y `logout-all` en Spring y Django: el mismo token pasó de `200` a `401` |
| H38 | Tres respaldos `COMPLETADO` con huella válida, restauración aislada con conteos iguales y retención probada. Se encontró y corrigió `ERROR 1046 (No database selected)` |
| H53 | Migración ejecutada dos veces sin error; 3 recordatorios (objetos 2, 4 y 5) sin repetir en el segundo ciclo; la preferencia `true → false → true` se conserva |

El detalle está en las secciones «Pruebas» de cada contrato.

## Trabajo transversal

El reparto (página 6) asigna a Leonel, además de sus historias, este trabajo de backend para todo el equipo:

| Tarea | Estado |
| --- | --- |
| Contratos de API de las 20 historias en `docs/api/sprint3-*.md` | Hecho: los 20 contratos están en `docs/api/`. Los de H40, H42, H44, H46 y H50 se escribieron a partir del código integrado en `main` |
| Datos de demostración: fotos reales, lugares de Tecsup, pares que coinciden y objetos en custodia | Hecho: `scripts/cargar_datos_demo.py` crea, con las APIs reales, 9 publicaciones en lugares del campus, 3 pares que coinciden, 5 fotos y 2 custodias. Las fotos son de Wikimedia Commons con licencia libre (ver `database/demo/fotos/CREDITOS.md`), no fotos tomadas en el campus. Probado dos veces seguidas en un MySQL de QA aislado: la segunda no duplica nada |
| CI en GitHub Actions: pruebas de Spring, Django, web y Android en cada push | Hecho: `.github/workflows/ci.yml`. Cada comando se ejecutó aquí con éxito antes de escribirlo (ver «Validación final») |
| Revisión de backend (errores 400/401/403/404/409) | Al escribir los contratos de Amore se encontró que H50 respondía `500` si faltaba `desde`. Se corrigió a `400` para todas las rutas con un parámetro obligatorio y se agregaron 4 pruebas de H50, que no tenía ninguna. Suite de Spring: 502 pruebas, 0 fallos (19/11/2026) |
| Integraciones del 5/11 y 8/11 y revisión final para `main` | Las integraciones del 5/11 y 8/11 las hicieron Yajaira (`f798d7e`, `91693cc`) y Amore (`edaf699`, `3248bf4`). La integración final está en `dev/leonel`: `main` completo más las correcciones de H44 y H52 de `dev/amore` hasta `37a1199`, sin los commits de planificación del Sprint 4 |

## Validación final (19/11/2026, `dev/leonel`)

| Capa | Resultado |
| --- | --- |
| Spring (`mvn test`) | 502 pruebas, 0 fallos |
| Django (`manage.py test nucleo`, SQLite) | 110 pruebas OK |
| Web de usuario (`npm test` y `npm run build`) | 312 pruebas OK; build correcto |
| Panel (`npm test` y `npm run build`) | 52 pruebas OK; build correcto |
| Android (`gradlew testDebugUnitTest`) | 53 pruebas, 0 fallos (incluye las 6 de `RetiroCustodiaTest` de H44) |
| APK (`gradlew assembleDebug`) | Compila sin errores: `app-debug.apk` 1.2.2 (versión 5), 35 MB. Sin claves ni JWT dentro; apunta a `10.0.2.2` (emulador) |
| Datos de demostración | Docker con MySQL de QA aislado: 9 publicaciones, 3 coincidencias, 5 fotos servidas como JPEG, custodias `FND-2026-0001` y `FND-2026-0002` con etiqueta PDF |

## Para llevar a `main`

`dev/leonel` contiene todo el Sprint 3 integrado. Pasarlo a `main` requiere la autorización escrita del equipo.
La CI se ejecuta en GitHub al subir la rama.

Queda fuera de este sprint, por acuerdo del reparto del Sprint 4 (M2): las pruebas físicas en teléfono de H44, H46, H50, H51 y H52.
