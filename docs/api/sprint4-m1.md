# M1 (datos) — Desactivar las categorías de prueba

Responsable: Leonel. Parte de datos de M1 del Sprint 4 (las correcciones de Android de M1 son de Amore).

Durante las pruebas del Sprint 3 quedaron dos categorías sin sentido que aparecen al publicar:
**«Accesorios QA semanal»** y **«Accesorios QA React»**.

## Cómo se aplica

> **Antes de aplicarlo:** el equipo confirma que son estas dos categorías (paso 1 del reparto).

Script: `database/datos_m1_desactivar_categorias_qa.sql`.

- Las busca por **nombre**, no por id: cada integrante tiene su propia base y el id puede ser distinto.
- **No borra nada:** pone `estado = FALSE`. Las publicaciones que ya las usan conservan su categoría.
- Es re-ejecutable: la segunda vez desactiva 0. Si una base no las tiene, no hace nada.
- Muestra antes cuántas publicaciones usan cada una, y después cuántas desactivó, la fecha (UTC) y el
  usuario de MySQL: eso sirve como registro de quién lo hizo y cuándo.
- Para volver atrás: `UPDATE categorias SET estado = TRUE WHERE nombre IN (...)`.

Con MySQL encendido (Git Bash):

```bash
docker exec -i foundia-mysql mysql -uroot -proot -t < database/datos_m1_desactivar_categorias_qa.sql
```

En PowerShell:

```powershell
cmd /c "docker exec -i foundia-mysql mysql -uroot -proot -t < database\datos_m1_desactivar_categorias_qa.sql"
```

También se puede hacer a mano desde el panel (Categorías → desactivar); el resultado es el mismo.

## Qué hace el sistema con una categoría desactivada

Lo define el contrato de categorías de H26 ([categorias-sprint2.md](categorias-sprint2.md)):

| Parte | Comportamiento |
| --- | --- |
| `GET /api/categorias` (público) | **Sigue devolviéndola**, con `estado: false`, para los filtros de búsqueda de objetos antiguos (H7) |
| Crear o editar una publicación con ella | Spring responde `400` «No se puede asignar una categoría desactivada» |
| Formularios para publicar | **Deben mostrar solo las activas** |
| Panel de administración | La lista como desactivada |

> La prueba del reparto dice «`GET /api/categorias` ya no las devuelve», pero eso contradice el contrato de
> H26. Lo que se comprueba es lo que pide la misma prueba para el usuario: que **el selector para publicar no
> las muestre**. No se cambió la API pública para no romper los filtros de H7.

## Verificación del 23/11/2026 (MySQL 8.0 de QA)

Se crearon las dos categorías y una publicación que usa «Accesorios QA semanal», como en las bases del equipo.

| Comprobación | Resultado |
| --- | --- |
| Primera ejecución | Desactivó **2** (una con 1 publicación que la usa, otra sin uso) |
| Segunda ejecución | Desactivó **0**, sin error |
| La publicación que la usaba | Sigue visible (`200`) y conserva «Accesorios QA semanal» |
| Panel (`GET /api/admin/categorias/`) | Las dos aparecen con `estado: false` |
| Publicar con una desactivada | `400` «No se puede asignar una categoría desactivada» |
| `GET /api/categorias` | Las sigue devolviendo con `estado: false`, como define H26 |
| Selector de Android (Publicar) | Filtra por `estado`: **no las muestra** (revisado en el código de `mobile/android`) |
| Selector de la web (Publicar) | **Sí las muestra:** `useCategorias` no filtra por `estado` (ver abajo) |

## Pendiente para la web (Yajaira)

En `frontend/web`, `frontend/usuario/src/pages/Publicar.jsx` muestra en el selector todas las categorías de
`useCategorias()`, también las desactivadas. El contrato de H26 dice que los formularios de alta deben
ofrecer solo las activas. Si alguien elige una desactivada, el servidor la rechaza con `400`. Corrección
sugerida en Publicar (no en el filtro de búsqueda de Inicio, que debe conservarlas):

```jsx
{categorias.filter((c) => c.estado !== false).map((c) => ( … ))}
```
