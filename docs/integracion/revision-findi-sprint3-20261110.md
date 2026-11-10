# Integración de dev/leonel — 10/11/2026

Se fusionó `origin/dev/leonel` (`3942c8810ec64d29dea2e25af01b31da1285b240`)
sobre `frontend/web` (HEAD `225784e`) con `git merge --no-commit --no-ff`.
El resultado está preparado en el árbol de trabajo, sin commit ni push.

## Cambios incorporados

Se incorporaron H36 (rol biblioteca), H41 (custodia y etiquetas QR), H43
(resolución de reclamos), H45 (asistente con respaldo local y resumen propio),
H32 (exportaciones), H37 (revocación de sesiones), H38 (respaldos) y H53
(recordatorios), con sus contratos, migraciones y pruebas. También entró la
reorganización de Android que ya estaba en `main`.

Los contratos nuevos del backend no equivalen a implementar todas sus pantallas
web y Android; cada historia conserva el alcance que describe su documento en
`docs/api/sprint3-h*.md`.

## Resolución de FINDI

- Hay un solo `POST /api/asistente/interpretar`, en el controlador H45 de Leonel.
  Conserva el límite por cuenta/IP, la respuesta compartida y el resumen propio.
- La opción local de Groq/OpenAI se conserva como `InterpretadorIaCompatible`.
  Se intenta primero si tiene clave, luego Claude opcional y finalmente el
  motor local del servidor. El límite se aplica una vez en el controlador.
- `fuente` devuelve `ia`, `claude` o `local` según la interpretación utilizada.
- La voz permanece en `VozController`, con su endpoint, caché concurrente y
  límites propios. Una sola excepción HTTP 429 admite ambos constructores.
- La web envía el token a H45 cuando hay sesión, sin usar el interceptor que
  cerraría la sesión ante un error del asistente. Conserva las acciones locales
  Mis reportes y Cómo funciona si el motor compartido no las reconoce.
- Se conservaron las correcciones de mensajes duplicados y cancelación de voz,
  el rediseño, los recursos de FINDI y el dashboard/estadísticas del usuario.

Las superposiciones en `SecurityConfig`, `GlobalExceptionHandler` y Compose
se resolvieron conservando los contratos de Leonel y la extensión de voz.
No quedan marcadores de conflicto.

## Copia de recuperación

El stash llamado `Respaldo local antes de integrar dev/leonel 2026-11-10`
se conservó y no se eliminó. Además existe una copia local ignorada en
`tmp/integracion-leonel-20261110/`: ZIP de los archivos locales, diferencias
contra HEAD, diferencias del índice y estado previo. Los archivos originales
no relacionados con la integración se compararon con esa copia.

## Verificación

- Spring: suite completa, 479 pruebas aprobadas.
- Selección de proveedores y adaptador compatible: 14 pruebas aprobadas,
  incluyendo 5 pruebas agregadas después de la ejecución completa.
- Django: 100 pruebas aprobadas con `config.settings_test` (SQLite en memoria).
- Administrador: 29 pruebas aprobadas y compilación correcta.
- Usuario: 250 pruebas aprobadas en 32 archivos y compilación correcta.
- Docker Compose: configuración validada con `docker compose config --quiet`.

Los logs se guardaron junto al respaldo local. Las pruebas simulan IA/voz y
usan bases temporales; no consumen proveedores externos ni cambian MySQL.
Las dependencias de Django se instalaron únicamente en una carpeta temporal.

## Base de datos existente

El código nuevo requiere revisar y aplicar las migraciones ausentes de H36,
H37, H38, H41, H43 y H53 en la instalación que vaya a ejecutarlo. Esta integración
no las ejecutó contra la base personal. Seguir `database/README.md` y los
contratos de cada historia, con respaldo previo. No importar `script.sql` o
`seed.sql` sobre datos existentes: esos archivos son para instalaciones nuevas.
No se ejecutó QA con MySQL real ni una compilación de Android en esta integración.
