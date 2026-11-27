# M5 y M6 contra la API real (27/11/2026)

Entorno: QA local (Spring con MySQL 8, proyecto `foundia-amore-s3`), con las mismas rutas y cuerpos que usa la app
(`PUT/DELETE /api/objetos/{id}`, `POST /api/asistente/buscar-por-foto`). Se probó a nivel de API, **no** tocando la
pantalla del teléfono. Cuentas del seed (`ana@` dueña, `luis@` otra persona).

| Caso | Resultado |
| --- | --- |
| M5 crear publicación | `201` |
| M5 editar como dueña (nombre y lugar) | `200`, los datos cambian |
| M5 editar como otra persona | `403` |
| M5 eliminar como otra persona | `403` |
| M5 aparece editada en «Mis publicaciones» | Sí |
| M5 eliminar como dueña | `204` |
| M5 la publicación eliminada | `404` |
| M6 foto válida (data URL PNG) | `503`: la IA no está configurada en el QA (`mensaje` explica que falta la IA) |

Conclusión: **M5 queda probado contra la API real**. **M6 no se pudo probar de punta a punta**: la API valida la foto y
responde 503 porque el QA no tiene clave de IA; la pantalla de la app para ese 503 solo tiene pruebas unitarias.
