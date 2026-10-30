# H39 — Compartir publicaciones de objetos perdidos: casos de prueba (Yajaira)

Pantalla web: detalle de la publicación, componente
`frontend/usuario/src/components/CompartirPublicacion.jsx`. Android lo implementó
Amore con el selector del sistema (ver su entrega).

## Reglas

- Solo se comparten objetos **PERDIDOS** en estado ACTIVO o RECUPERADO, igual que en
  Android.
- El enlace es la ruta pública existente `/objeto/{id}`: no lleva token, sesión,
  correo ni teléfono.
- Antes de compartir se vuelve a consultar la publicación, por si fue retirada.
- Con menú del sistema (`navigator.share`) se dice «Elegiste dónde compartir»: el
  navegador no confirma el envío, así que no se afirma que se envió. Cancelar no
  muestra ningún mensaje.
- Sin menú del sistema se copia al portapapeles; si eso falla, se muestra el
  enlace para copiarlo a mano.

## Casos (Vitest)

| N.º | Caso | Resultado esperado |
| --- | --- | --- |
| 39-1 | Compartir con menú del sistema | Revalida y comparte solo la URL pública |
| 39-2 | Cancelar el menú | No se anuncia como compartido |
| 39-3 | Navegador sin menú | Copia el enlace al portapapeles |
| 39-4 | Falla el portapapeles | Muestra el enlace para copiarlo a mano |
| 39-5 | Retirada mientras estaba abierta (404) | «Ya no está disponible»; no comparte |
| 39-6 | Al revalidar ya no está vigente | No comparte |
| 39-7 | Objeto encontrado o publicación retirada | No aparece el botón |

```powershell
# frontend/usuario
npx vitest run src/components/CompartirPublicacion.test.jsx
```

## Resultado (30/10/2026, rama dev/yajaira 4d50a5c)

Web usuario: **206 pruebas correctas** en 29 archivos (7 de H39) y `vite build`
correcto.

## Evidencia manual pendiente

Con la API real: copiar el enlace, abrirlo en una ventana privada sin sesión
(debe verse la publicación sin datos de contacto) y, tras retirar la publicación,
abrir el mismo enlace (debe estar inaccesible). Guardar las capturas en
`docs/pruebas/evidencias-yajaira-sprint2/`. Amore ya registró la versión Android
(`h39-selector.png`, `h39-web-publica.png`, `h39-web-retirada.png`).
