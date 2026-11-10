# H51 — Accesibilidad de la web (lector de pantalla, teclado y texto ampliado)

Fecha: 2026-11-10 · Responsable: Yajaira · Rama: `frontend/web`

## Cómo se auditó

- **axe-core 4.14.0** ejecutado dentro del navegador sobre el servidor de desarrollo
  (`npm run dev` en `frontend/usuario`), con las reglas `wcag2a`, `wcag2aa`, `wcag21a`,
  `wcag21aa` y `best-practice`.
- Modo **claro y oscuro**, con el chat de FINDI **cerrado y abierto**.
- **Texto ampliado al 130 %** (tamaño de letra raíz) en un celular de 375 × 812 px: se buscó
  scroll horizontal de la página y elementos que se salen de la pantalla.
- Teclado: primer elemento enfocable, foco visible (`:focus-visible`) y movimiento reducido.

> Lighthouse no se ejecutó en esta pasada: el navegador integrado no lo incluye. Queda como
> verificación opcional en Chrome (DevTools › Lighthouse › Accesibilidad).

## Pantallas revisadas

`/` (portada), `/login`, `/registro`, `/explorar`, `/buscar`, `/privacidad`, `/recuperar-password`.

Las pantallas con sesión (Perfil, Notificaciones, Publicar, Mis publicaciones) necesitan la API
encendida; se auditarán con Docker activo (ver «Pendiente»). Sus componentes ya se prueban con
Testing Library por **rol y nombre accesible** (por ejemplo `getByRole('button', { name: … })`),
lo que falla si un control pierde su etiqueta.

## Antes

| Regla de axe | Impacto | Dónde | Problema |
| --- | --- | --- | --- |
| `label-content-name-mismatch` | serio | Globo de FINDI en la portada | El nombre accesible («Hablar con FINDI») no contenía el texto visible del botón; quien usa control por voz no puede activarlo diciendo lo que ve. |
| `landmark-no-duplicate-banner` | moderado | Cabecera del chat de FINDI | Con el chat abierto había dos regiones «banner» (`<header>`), y el lector de pantalla las anunciaba como dos cabeceras de página. |
| `landmark-unique` | moderado | Barra de navegación | Consecuencia del punto anterior: dos regiones del mismo tipo sin nombre que las distinga. |
| (revisión manual) | — | Todas las páginas | No había un enlace para **saltar al contenido**; con teclado había que recorrer todo el menú en cada página. |

Sin fallos de contraste, de etiquetas de formulario ni de idioma (`<html lang="es">`).

## Correcciones

| Cambio | Archivo |
| --- | --- |
| El globo usa su propio texto visible como nombre accesible (se quitó el `aria-label` distinto). | `src/components/SliderPortada.jsx` |
| La cabecera del chat pasó de `<header>` a `<div>`: el diálogo ya tiene su nombre («FINDI, asistente de FoundIA»). | `src/components/ChatAsistente.jsx` |
| Enlace «Saltar al contenido», visible solo al recibir el foco; `<main id="contenido" tabIndex="-1">` en las dos plantillas. | `src/layouts/MainLayout.jsx`, `src/layouts/AuthLayout.jsx`, `src/styles/experiencia.css` |

## Después

| Prueba | Resultado |
| --- | --- |
| axe en las 7 pantallas, modo claro | 0 fallos |
| axe en la portada con el chat abierto, claro y oscuro | 0 fallos |
| Primer elemento enfocable de la página | «Saltar al contenido» |
| Texto al 130 % en 375 px | Sin scroll horizontal en ninguna pantalla. La fila de categorías de la portada se desplaza dentro de su propio contenedor (`overflow-x: auto`), como estaba diseñada. |
| Foco visible | `:focus-visible` global (azul en claro, amarillo en oscuro) y foco propio del globo de FINDI. |
| Movimiento reducido | `prefers-reduced-motion` desactiva animaciones y desplazamientos. |

## Historias del Sprint 3 revisadas en esta misma pasada

Los componentes nuevos de esta entrega se escribieron con las mismas reglas:

- H25: filtros con `aria-pressed`, contador en `role="status"`, errores en `role="alert"`.
- H37: cierre en todos los dispositivos con diálogo de confirmación accesible (foco en «Cancelar», Escape cierra).
- H47: tarjetas de FINDI en una lista con nombre («Tarjetas de FINDI») y acciones en un grupo con nombre; cada acción pide confirmación.
- H49: la sugerencia se anuncia con `aria-live="polite"`; la descripción sugerida tiene su etiqueta.

## Pantallas con sesión — 10/11/2026

Sesión real de Ana (`database/seed.sql`), Spring :8081 y MySQL en Docker.
axe-core **4.14.0**, navegador Chromium integrado, viewport **375 × 812**,
raíz a **130 % (20,8 px)**. No se simularon respuestas de la API.
Se revisaron los mismos tags WCAG y best-practice indicados al inicio.

| Pantalla | Antes claro: nodos con infracción | Antes oscuro: nodos con infracción | Después claro/oscuro |
| --- | ---: | ---: | --- |
| Perfil | 2 | 6 | 0 / 0 |
| Notificaciones | 4 | 4 | 0 / 0 |
| Publicar (incluida H52) | 3 | 2 | 0 / 0 |
| Mis publicaciones | 2 | 2 | 0 / 0 |
| Detalle `/objeto/1` | 2 | 3 | 0 / 0 |

Correcciones:

- El enlace de notificaciones incluye en su nombre el contador visible; el avatar
  incluye las iniciales visibles. Esto permite identificarlos también por voz.
- Los avisos toman su nombre del contenido visible completo. La instrucción para
  marcarlos y abrirlos se comunica por `aria-description`, sin sustituir su nombre.
- El botón de tipo Perdido usa marrón oscuro en claro y naranja claro en oscuro,
  con contraste suficiente frente a su superficie.

No hubo desbordamiento horizontal en las diez combinaciones. La captura inicial
también detectó contrastes durante animaciones de entrada; la repetición espera
fuentes y 1,2 s para medir el render estable, sin desactivar reglas de axe.

Evidencia completa: [antes](sprint3-evidencias/axe-antes.json) y
[después](sprint3-evidencias/axe-despues.json). Las diez pasadas finales tienen
cero **violaciones automáticas**; siete conservan comprobaciones de contraste
`incomplete` (superficies translúcidas, imágenes o controles nativos). No se
convierten en aprobadas: requieren revisión manual y no permiten afirmar una
certificación WCAG completa. Aumentar la raíz tampoco amplía los estilos que
usan píxeles fijos; queda pendiente contrastarlo con la ampliación del teléfono.

Reproducción: `npm run dev` en usuario, entrar con una cuenta seed, abrir cada
ruta con `?auditar=1`, fijar 375 × 812 y pulsar **Ejecutar auditoría H51**.
Cambiar el tema desde la interfaz y repetir. El módulo `src/qa/accesibilidad.js`
solo se importa en desarrollo con ese parámetro; no entra en el build de producción.

## Pendiente

- Revisión manual de los contrastes `incomplete`, lector de pantalla y ampliación nativa.
- Lighthouse en Chrome como segunda opinión: no ejecutado; la herramienta no está instalada
  en la caché local (`npx --offline --no-install lighthouse --version`: `ENOTCACHED`).
  Esta pasada de axe usa el navegador integrado.
- Android (TalkBack y texto al 130 %) es la capa de Amore.
