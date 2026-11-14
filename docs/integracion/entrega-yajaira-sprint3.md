# Entrega de Yajaira a Leonel — Sprint 3

Documento: 13/11/2026. Reparto de referencia: PDF «FoundIA-Sprint-3-Reparto» del 30/10/2026.

| Rama | Contenido |
| --- | --- |
| `dev/yajaira` | API/datos de mis historias, un commit por historia, sobre `dev/leonel` (`3942c88`) |
| `frontend/web` | Web de usuario y panel de las 20 historias (sin Android) |

No se hizo merge a `main` ni push forzado. Android es la capa de Amore.

## Mis seis historias

| Historia | API / datos (`dev/yajaira`) | Web (`frontend/web`) | Estado |
| --- | --- | --- | --- |
| H48 Privacidad y términos | Reutiliza `GET /api/legal/documentos` de H33 (versión con fecha) | `/privacidad`, `/terminos` y `/cookies`; enlaces en portada, registro, FINDI y panel | Terminada |
| H25 Historial de avisos | `GET /api/notificaciones/historial` y `PUT /api/notificaciones/leidas` (`23d28cc`) | Filtros, paginación, «marcar todas», vacío y error | Terminada |
| H47 Tarjetas y acciones | Catálogo cerrado y acciones validadas (`57adc13`) | Tarjetas de objeto, reclamos y QR; doble confirmación | Terminada |
| H49 Sugerencia con IA | `POST /api/asistente/sugerir-publicacion` (`ef437c2`) | Aceptar, editar o descartar en Publicar | Terminada: probada con IA real (Groq, `openai/gpt-oss-20b`) |
| H51 Accesibilidad | Sin API | axe, Lighthouse 24/24 en 100, contraste manual, 320 px | Falta lector de pantalla real (persona y dispositivo) |
| H54 Disposición final | Plazo, vencidos y acta en Django; `migracion_h54_disposicion.sql` (`772afd3`) | Pantalla de vencidos, confirmación y acta PDF | Terminada |

Antes de H49 van dos commits que esa historia necesita para compilar: el proveedor de IA
compatible (`201f0d3`, `H45`) y la voz natural de FINDI en el servidor (`c73d62c`, `H46`).

## Capa web de las historias del equipo

| Historia | Pantalla | Estado |
| --- | --- | --- |
| H36 (Leonel) | Panel · Usuarios: cambio de rol, menú solo de biblioteca | Terminada |
| H41 (Leonel) | Panel · Custodia: recibir objeto, lista y filtros | Terminada |
| H43 (Leonel) | Panel · Reclamos: bandeja, aceptar o rechazar con motivo | Terminada |
| H45 (Leonel) | FINDI fuera de extras; 20 frases medidas: web 85 %, API con IA 85 % (la IA responde 19/20) | Terminada |
| H32 (Leonel) | Estadísticas: exportar Excel y PDF por rango | Terminada |
| H37 (Leonel) | Perfil: cierre seguro y en todos los dispositivos | Terminada |
| H38 (Leonel) | Dashboard: último respaldo y su estado | Terminada |
| H53 (Leonel) | Notificaciones: recordatorios y su preferencia | Terminada |
| H52 (Amore) | Publicar: cámara dentro de la página (HTTPS), borrador si el navegador recarga, foto reducida y ajustada al recuadro del detalle | Terminada: probada en un Samsung Android |
| H46 (Amore) | FINDI: micrófono, lectura en voz alta y aviso de privacidad | Terminada |
| H42 (Amore) | Formulario de reclamo con verificación y estado | **Bloqueada:** falta la API de Amore |
| H44 (Amore) | QR de retiro del usuario | **Bloqueada:** falta la API de Amore |
| H40 (Amore) | Invitar desde el Perfil y registro desde la invitación | **Bloqueada:** falta la API de Amore |

## Contratos y evidencias

- Contratos: [H25](../api/sprint3-h25.md), [H47](../api/sprint3-h47.md), [H48](../api/sprint3-h48.md),
  [H49](../api/sprint3-h49.md), [H51](../api/sprint3-h51.md), [H54](../api/sprint3-h54.md).
- Verificación con las APIs reales: [sprint3-verificacion-yajaira.md](../pruebas/sprint3-verificacion-yajaira.md).
- Accesibilidad: [accesibilidad-h51.md](../pruebas/accesibilidad-h51.md).
- Asistente: [h45-20-frases.md](../pruebas/h45-20-frases.md).

## Base de datos

Única migración nueva de mis historias: `database/migracion_h54_disposicion.sql` (idempotente);
`script.sql` también la incluye. Se probó en una base de QA con respaldo previo, nunca sobre la
base compartida. `CUSTODIA_PLAZO_DIAS` (por defecto 60) se pasa a Django desde Compose.

## Validación

| Capa | Resultado |
| --- | --- |
| Spring (`dev/yajaira`, `mvn -o test`) | 505 pruebas OK; compila después de cada commit |
| Django (`frontend/web`, 10/11) | 108 pruebas OK, incluida H54 |
| Web de usuario (Vitest) | Todas OK; `npm run build` OK |
| Panel (Vitest) | 51 pruebas OK; `npm run build` OK |
| Lighthouse accesibilidad | 24 / 24 pasadas en 100 |

## Pendientes y de quién dependen

1. Prueba con lector de pantalla real (H51) — Yajaira.
2. Para usar la IA, cada integrante copia `.env.example` como `.env` y completa `IA_API_KEY` (nunca en Git).
3. APIs y contratos de H42, H44 y H40 — Amore. Después se completan sus pantallas web.
4. Integrar `dev/yajaira` y `frontend/web` en `dev/leonel` y llevar el sprint a `main` — Leonel.

## Nota de entorno

En el PC de Yajaira, XAMPP ocupa el puerto 3306; para las pruebas el MySQL de Docker se publicó
en el 3307 con un archivo de Compose fuera del repositorio. Los contenedores no cambian.
