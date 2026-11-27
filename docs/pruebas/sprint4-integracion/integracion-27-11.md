# Integración de la web con el backend · 27/11/2026

Rama integrada: `frontend/web` + `dev/leonel` (sin conflictos al volver a unirlas). Probado con Docker (proyecto QA
`foundia-amore-s3`, MySQL 8 real), reconstruyendo `api`, `admin-api`, las dos webs y el servicio de respaldo.

## Pruebas automáticas (local, antes de subir)

| Capa | Resultado |
| --- | --- |
| Spring | 606 pruebas, 0 fallas (Spring arranca con una sola sección `springdoc`) |
| Django | 245 pruebas, OK |
| Web de usuarios | 369 + 3 nuevas, OK; compila |
| Panel | 89 + 11 nuevas, OK; compila |
| Android | 159 unitarias, OK (corrida del 27/11; no cambió código de la app) |

El CI de GitHub **no se pudo consultar desde aquí** (no hay `gh`): hay que revisarlo en GitHub.

## Contra el sistema real (QA, MySQL)

| Historia | Prueba | Resultado |
| --- | --- | --- |
| H62 | Foto del chat: sin sesión `401`; con sesión de quien conversa `200`; de otra persona del chat `200`; de alguien ajeno `404` | Correcto |
| H62 | Web de usuarios en el navegador: la foto del chat se carga como blob con la sesión | Correcto |
| H34 | Se pide la eliminación, el panel la lista con fecha límite y vista previa; correo equivocado `400`; correo correcto `200`, la cuenta queda anonimizada y la persona ya no entra (`401`); el panel la lista en «Atendidas» | Correcto. La cuenta de prueba se borró después |
| H70 | El panel lista la versión disponible con el botón «Restaurar» y el historial | La **lista y el botón** se ven. **No se aplicó una restauración real**: reemplazaría los datos del QA |
| H58 | Con el servicio de respaldo reiniciándose por un candado abandonado, la versión nueva liberó el candado y completó un respaldo de base y de fotos | Correcto |
| H58 | `scripts/probar_candado_respaldo.sh`: con un candado reciente el servicio reintenta cada 2 s (configurable) y sigue al soltarse; la versión anterior esperaba 86 400 s | Correcto |

## Hallazgo importante

Una base existente necesita `database/migracion_extra_etiquetas_qr.sql` (columna `conversaciones.pertenencia_id`).
Sin ella, `GET /api/conversaciones` responde **500** y el chat no abre en la web ni en la app. Es idempotente; hacer un
respaldo antes. Una base creada con `script.sql` ya la trae.

## Sin probar todavía

- Restauración real de una versión desde el panel (H70) de punta a punta.
- Atender una eliminación con datos reales (publicaciones, fotos, chat); se probó con una cuenta vacía.
- Playwright (C2) sobre lo integrado; la web nueva se miró solo por código y por esta prueba puntual.
- Push con la app cerrada, Firebase Auth de punta a punta y la voz natural: dependen de las claves de Leonel.
