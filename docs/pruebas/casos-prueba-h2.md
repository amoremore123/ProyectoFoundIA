# H2 — Agregar una foto a una publicación

## Contrato y configuración

- `POST /api/objetos/{id}/fotos`, `multipart/form-data`, parte `archivo`, JWT requerido.
- Solo el autor autenticado de la publicación puede agregar la imagen.
- Formatos admitidos por firma y decodificador de imagen: JPEG y PNG. Se ignora la extensión/nombre que envía el cliente.
- Máximo: 5 MiB y 40 megapíxeles; límites aplicados por Spring multipart y por validación de servidor.
- Archivos guardados con UUID en `UPLOAD_DIRECTORY` (local: `uploads/`; Docker: `/app/uploads`, volumen persistente `uploads_data`).
- Las URL `/uploads/{uuid}.{jpg|png}` son públicas para que los listados y el detalle muestren la foto. `ImagenObjeto` conserva la alternativa «Sin foto» ante ausencia o fallo al cargarla.
- Ante formato/tamaño inválido no se escribe archivo ni asociación. Si falla el guardado de la fila, el servidor borra compensatoriamente el archivo recién escrito.

## Pruebas automáticas ejecutadas

| Caso | Resultado |
| --- | --- |
| Autor sube PNG de exactamente 5 MiB | `201`; foto asociada al objeto y URL en detalle |
| GET a la URL pública | `200 image/png`, bytes coinciden con el archivo subido |
| Archivo inválido | `400`; ninguna fila ni archivo |
| PNG de 5 MiB + 1 byte | `400`; ninguna fila ni archivo |
| Otro usuario | `403`; sin asociación ni archivo |
| Sin token | `401`; sin asociación ni archivo |
| Publicar sin foto | Conserva alternativa «Sin foto» |
| Fallo transitorio en el cliente | Reintenta subir al objeto ya creado, sin duplicar la publicación |

Comandos:

```bash
cd backend/springboot && mvn -Dtest=FotoPublicacionIntegrationTest test
cd frontend/usuario && npm test -- --run src/pages/Publicar.test.jsx src/components/ImagenObjeto.test.jsx
```
