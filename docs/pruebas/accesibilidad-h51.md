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

## Lighthouse y revisión manual — 13/11/2026

**Lighthouse 13.6.0** (categoría Accesibilidad, móvil emulado, Chrome headless) sobre las 12
pantallas principales, en modo claro y oscuro: 24 pasadas. Las pantallas con sesión usan la
cuenta de Ana de `seed.sql` y la API real. Script reproducible:
[`scripts/lighthouse-h51.mjs`](../../scripts/lighthouse-h51.mjs) (Lighthouse se instala aparte,
no es dependencia del proyecto).

| | Antes | Después |
| --- | --- | --- |
| Pasadas con 100 | 19 / 24 | **24 / 24** |
| Peor puntaje | 96 | 100 |

Evidencia: [antes](sprint3-evidencias/lighthouse-h51-antes.json) y
[después](sprint3-evidencias/lighthouse-h51.json).

### Hallazgos y correcciones

| Hallazgo | Dónde | Medición | Corrección |
| --- | --- | --- | --- |
| `heading-order`: después del h1 venía un h3 | `/privacidad` y `/terminos`, claro y oscuro | — | `TextoLegal` recibe el nivel inicial; la página pública usa h2/h3 y el registro sigue en h3/h4. Prueba en `Legal.test.jsx`. |
| `color-contrast`: chip «Activo» | Mis publicaciones (y donde se use `.estado-activo`) | 4,50:1 claro · 4,48:1 oscuro | Texto con `--primary-700`: **6,2:1** claro · **5,5:1** oscuro. |
| `color-contrast`: enlaces legales del registro | `/registro`, oscuro | 4,28:1 | Tono claro `--primary-700` en oscuro: **5,3:1**. |
| Contador de notificaciones (manual; Lighthouse no lo marcó) | Cabecera, oscuro | 2,53:1 (blanco sobre salmón) | Número en marino: **6,06:1**. |

### Revisión manual de los contrastes `incomplete` de axe

axe había dejado como «incompletos» los textos sobre superficies translúcidas (h1, contadores,
ayudas, menú inferior, selector, etiqueta de imagen) en Perfil, Notificaciones, Publicar,
Mis publicaciones y Detalle. Se midió cada uno en el navegador, componiendo las capas de fondo
con su transparencia: **100 textos revisados, todos cumplen su mínimo (4,5:1, o 3:1 en texto
grande)**, salvo el contador ya corregido. En los 34 textos con degradado o imagen de fondo se
tomó el color sólido de la capa base; el menor de ellos dio 5,5:1 (menú inferior, oscuro), con
margen sobre el mínimo, pero un degradado no se puede certificar solo con este cálculo.

### Reajuste a 320 px (WCAG 1.4.10)

Las 12 pantallas, en claro y oscuro, a 320 px de ancho: **sin scroll horizontal y sin textos
fuera de la pantalla** (se excluyen los carruseles con desplazamiento propio). Observación:
por debajo de 320 px (p. ej. 286 px) la tarjeta de «Mis publicaciones» corta la ubicación y
la fecha; está fuera del mínimo de WCAG y queda como mejora.

## Pendiente

- Prueba con un lector de pantalla real (NVDA en Windows o TalkBack con el navegador del
  teléfono) y con la ampliación nativa del teléfono: requiere una persona y el dispositivo.
- Android (TalkBack y texto al 130 %) es la capa de Amore.
