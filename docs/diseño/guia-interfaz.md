# Guía de interfaz

Guía visual única para **React Usuario**, **React Admin** y **Android**.
Estilo limpio, moderno y sencilillo; fondo claro, azul como color principal.

---

## 1. Colores

| Token         | Valor      | Uso                                      |
| ------------- | ---------- | ---------------------------------------- |
| `--primary`   | `#2563eb`  | Botones principales, enlaces, activo     |
| `--primary-700` | `#1d4ed8` | Hover de botones                         |
| `--primary-50` | `#eff6ff`  | Fondos suaves, chips activos             |
| `--bg`        | `#f5f7fa`  | Fondo general de la app                  |
| `--surface`   | `#ffffff`  | Tarjetas, formularios, navbar            |
| `--border`    | `#e5e7eb`  | Bordes de tarjetas e inputs              |
| `--text`      | `#0f172a`  | Títulos                                  |
| `--text-muted`| `#475569`  | Texto normal                             |
| `--text-soft` | `#94a3b8`  | Texto secundario, placeholders           |
| `--success`   | `#16a34a`  | Éxito, chip "Encontrado"                 |
| `--danger`    | `#dc2626`  | Errores, chip "Perdido"                  |
| `--warning`   | `#d97706`  | Advertencias                             |

Chips de tipo:

- **PERDIDO** → fondo `#fee2e2`, texto `#dc2626`
- **ENCONTRADO** → fondo `#dcfce7`, texto `#16a34a`

## 2. Tipografía

- Familia: sistema (`-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif`)
- Títulos: 700 peso. H1 hero 28–32px · H2 sección 20px · H3 tarjeta 16px
- Cuerpo: 15–16px, color `--text-muted`
- Secundario: 13px, color `--text-soft`

## 3. Botones

| Tipo      | Estilo                                                     |
| --------- | ---------------------------------------------------------- |
| Primario  | fondo `--primary`, texto blanco, radio 10px, padding 12px 20px, peso 600, hover `--primary-700` |
| Secundario| fondo blanco, borde 1px `--border`, texto `--text`         |
| Peligro   | fondo `--danger`, texto blanco                            |
| Fantasma  | solo texto `--primary`, sin borde                         |

Todos: transición 0.15s, cursor pointer, altura mínima 44px (touch).

## 4. Tarjetas (objeto)

- Fondo blanco, radio 14px, borde 1px `--border`, sombra suave `0 1px 3px rgba(15,23,42,.08)`
- Foto arriba, aspecto 16:9, cubre el ancho
- Debajo: chip de tipo (esquina), título (H3), línea `📍 ubicación`, línea `📅 fecha (dd/MM)`
- Listas de tarjetas: responsive — 1 columna móvil, 2–3 columnas en desktop

## 5. Formularios

- Label arriba del campo, 14px, peso 600, color `--text`
- Input/select/textarea: fondo blanco, borde 1px `--border`, radio 10px, padding 12px 14px, ancho 100%
- Focus: borde `--primary` + sombra `0 0 0 3px #eff6ff`
- Error: borde `--danger`, mensaje debajo en 13px `--danger`
- Campo obligatorio: indicado con `*`
- Botón de envío al fondo, ancho completo en móvil

## 6. Navegación

**Navbar (superior)**: fondo blanco, borde inferior `--border`, altura 60px.
Izquierda: logo **ENCUENTRA+** en `--primary` (peso 800).
Derecha: iconos 🔔 (notificaciones, con punto rojo si hay sin leer) y 👤 (perfil).

**Bottom nav (móvil < 768px)**: fijo abajo, fondo blanco, borde superior
`--border`, 4 items con icono + label 11px:

`Inicio · Buscar · Publicar · Perfil`

El item activo se pinta en `--primary`; los demás en `--text-soft`.
En desktop (≥768px) se muestra navegación horizontal en el navbar y se oculta
el bottom nav.

## 7. Diseño general

- Fondo de página `--bg`, contenido centrado, ancho máximo 1100px, padding 16–24px
- Mobile-first, respeta safe-area en el bottom nav
- Espaciado base de 8px (8 / 16 / 24 / 32)
- Estados vacíos: icono grande + texto corto + acción sugerida
- Loading: spinner o texto "Cargando..."
- Errores: banner rojo suave `#fef2f2` con texto `--danger`

## 8. Pantallas de referencia (React Usuario)

**Inicio** — navbar · hero `Perdiste algo. Encuéntralo.` · buscador
`🔍 Buscar objeto...` · filtros [Categoría] [Ubicación] · sección "Objetos
recientes" con tarjetas · FAB/botón `+ Publicar objeto` · bottom nav.

**Publicar objeto** — título con `← Publicar objeto` · pregunta
`¿Qué tipo de publicación es?` con dos botones toggle `[Perdí un objeto]`
`[Encontré un objeto]` · campos: Nombre, Descripción, Categoría (select),
Ubicación, Fecha (date) · espacio reservado para foto y coordenadas
(mensaje "Próximamente") · botón `[PUBLICAR OBJETO]`.

**Buscar** — buscador + filtros + grid de resultados.

**Detalle** — foto principal, chip de tipo, título, descripción, datos
(ubicación, fecha, categoría, publicado por), botón de contacto, sección
"Coincidencias sugeridas".

**Mi perfil** — datos del usuario, botón editar, enlace a Mis publicaciones y
Cerrar sesión.

**Mis publicaciones** — lista de tarjetas propias con estado + acción eliminar.

**Notificaciones** — lista con icono, título, mensaje, fecha; no leídas con
fondo `--primary-50`.

**Login / Registro** — tarjeta centrada, logo, campos, botón primario, enlace
al otro formulario.

## 9. React Admin

Misma paleta, pero layout de **sidebar** oscuro (`#0f172a`) con logo blanco y
menú: Dashboard · Usuarios · Publicaciones · Reportes · Categorías. Área de
contenido con fondo `--bg` y tarjetas blancas.
