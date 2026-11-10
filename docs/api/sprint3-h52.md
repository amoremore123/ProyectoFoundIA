# H52 — Foto desde la cámara del navegador

La web reutiliza el contrato de subida H2: `POST /api/objetos/{id}/fotos`,
multipart con el campo `archivo`, sesión del autor y el servicio
`subirFotoObjeto` de `frontend/usuario/src/services/api.js`. No se agrega una API.

Publicar conserva la entrada de galería JPEG/PNG y añade otra entrada con
`accept="image/*" capture="environment"` para solicitar la cámara trasera.
El navegador decide cómo presentar ese selector; en escritorio puede abrir archivos.
Ambas entradas usan la misma validación JPEG/PNG y límite de 5 MiB (5 242 880
bytes), vista previa con URL temporal revocada, progreso, reintento y eliminación
de la selección. HEIC/WebP no se envían al servidor. Cancelar no borra una foto
ya elegida ni los campos. Si no hay cámara disponible, se puede usar la galería.

La publicación se crea antes de cargar la foto. Si la carga falla, se conserva
el id creado y se ofrece reintentar o continuar sin foto; no se duplica el objeto.
Ambos selectores se deshabilitan durante la carga. El formulario requiere sesión.

## Verificación del 10/11/2026

- Vitest: cámara trasera, JPEG válido, cancelación, HEIC, exceso de tamaño;
  las pruebas compartidas cubren límite exacto, vista previa, liberación del blob,
  error y reintento del servicio.
- Navegador a 375 px con API real: selección desde la entrada de cámara,
  vista previa y subida de JPEG a la publicación QA **#10**; detalle con foto.
  Se utilizó un archivo de prueba para alimentar el selector, sin simular la API.
- Pendiente: captura física y cancelación/permiso denegado en un celular real.
  Vitest y el selector de escritorio no verifican el hardware ni los permisos del teléfono.

Evidencia: [publicación con fotografía](../pruebas/sprint3-evidencias/h52-publicacion-real.png).
