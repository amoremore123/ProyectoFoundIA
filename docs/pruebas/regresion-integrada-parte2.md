# Regresión integrada — Sprint 1, parte 2 (primer bloque)

Fecha: 20 de octubre de 2026. Responsable de la integración: Yajaira (`dev/yajaira`).

## Qué se integró

`dev/yajaira` (commit de unión `542bc94`) contiene en una sola versión:

| Origen | Historias |
| --- | --- |
| Yajaira — `dev/yajaira` | H11, H12, H13 (API), H19 |
| Yajaira — `frontend/web` | Correcciones web de la parte 1, rediseño y pantalla Perfil (H13) |
| Amore — `dev/amore` | H1, H3, H4, H2 (fotos), H5 (editar/eliminar), H18 (contacto), H20 (reclamo) |
| Leonel — `dev/leonel` | H6, H7 |

La unión no tuvo conflictos.

## Migración aplicada en la base de Docker

- Respaldo previo: `Desktop/respaldos-foundia/objetos_perdidos_db_..._antes_h20.sql`.
- `database/migracion_h20_reclamos.sql`: crea la tabla `reclamos`. No modifica datos existentes.

## Suites automáticas

| Suite | Resultado |
| --- | --- |
| Backend Spring (`mvn clean test`) | 196 pruebas, 0 fallos, 0 errores |
| Frontend usuario (`vitest run`) | 112 pruebas, 0 fallos |
| Compilación frontend (`vite build`) | Correcta |

## Prueba de punta a punta con la API real (Docker)

Dos cuentas QA temporales: **Ana** publica un objeto ENCONTRADO y **Luis** uno
PERDIDO de la misma categoría. Al terminar se eliminaron cuentas, objetos, foto,
contacto, coincidencia, reclamo y avisos (la base volvió a 4 usuarios y 6 objetos).

| Historia | Caso | Resultado |
| --- | --- | --- |
| H1 | Publicar objeto encontrado y perdido | OK |
| H2 | La autora sube un PNG válido (201) | OK |
| H2 | Otra cuenta no agrega fotos (403) | OK |
| H2 | Archivo falso con extensión .png (400) | OK |
| H2 | El detalle muestra la foto y la imagen se abre | OK |
| H5 | Otra cuenta no edita (403); sin token (401) | OK |
| H5 | La autora edita (200) y se ve en el detalle | OK |
| H5 | Edición inválida, nombre vacío (400) | OK |
| H18 | Contacto válido (201) sin correos en la respuesta | OK |
| H18 | Contacto repetido marcado como `repetido` | OK |
| H18 | Quien no es dueño del perdido no inicia contacto (403); sin token (401) | OK |
| H20 | Reclamo válido (201); la autora no reclama su propio objeto | OK |
| H18/H20 | La propietaria recibe aviso de contacto y de reclamo, sin correo del otro usuario | OK |
| H20 | Otra cuenta no ve ni marca como leídos esos avisos; la propietaria los marca y persiste | OK |
| H19 | Listado, detalle, búsqueda y coincidencias sin correos ni apellido completo | OK |
| H13 | `PUT /api/perfil` sigue funcionando | OK |
| H5 | Otra cuenta no elimina (403); la autora elimina, deja de verse en público (404) y queda `ELIMINADO` en sus publicaciones | OK |

**Total: 30/30 casos correctos.**

## Observaciones para el equipo

- H18 y H20 se adelantaron respecto al orden del PDF (iban después de H16/H17).
  `ContactoService` crea una coincidencia con una regla simple (90 % si coincide
  la ubicación, 65 % si no). Cuando Leonel implemente H16, el contacto debe usar
  el mismo algoritmo de coincidencias para no tener dos criterios.
- Pendiente de Leonel: H8, H9, H10, H16 y H17 (y sus pantallas web).
