# H51 — Usar la plataforma con lector de pantalla y texto ampliado

> Estado: historia **sin API**. No agrega ni cambia rutas, JSON ni errores. Este archivo existe
> para que el conjunto `docs/api/sprint3-*.md` cubra las 20 historias del Sprint 3.

## Alcance

H51 cambia solo las capas de presentación:

- **Web (Yajaira):** etiquetas y nombres accesibles, foco visible, navegación por teclado,
  «Saltar al contenido», contraste y texto ampliado. Informe con antes y después en
  [`docs/pruebas/accesibilidad-h51.md`](../pruebas/accesibilidad-h51.md).
- **Android (Amore):** etiquetas y orden de lectura con TalkBack y texto ampliado al 130 %.

## Reglas para las demás historias

Como no hay contrato de datos, el «contrato» de H51 son las reglas que deben cumplir las
pantallas nuevas que consumen las APIs del sprint:

| Regla | Comprobación |
| --- | --- |
| Todo control tiene un nombre accesible que **incluye su texto visible** | axe `label`, `label-content-name-mismatch`; pruebas con `getByRole(…, { name })` |
| Errores con `role="alert"`; avisos y contadores con `role="status"` o `aria-live` | Testing Library |
| Contraste de texto ≥ 4,5:1 (≥ 3:1 en texto grande), en claro y en oscuro | Lighthouse y medición manual |
| Títulos sin saltos de nivel (`h1` → `h2` → `h3`) | Lighthouse `heading-order` |
| Sin scroll horizontal a 320 px de ancho | Revisión en las 12 pantallas principales |
| Acciones que avisan a otra persona o ejecutan algo piden confirmación | Diálogo accesible: foco inicial, Escape cierra |

## Cómo se audita

- axe-core en el navegador: `npm run dev`, abrir la ruta con `?auditar=1`.
- Lighthouse (se instala aparte):
  `LH_DIR=<carpeta> LH_CORREO=... LH_PASSWORD=... node scripts/lighthouse-h51.mjs`.
