# H27 — denunciar contenido inapropiado

Spring Boot: `POST /api/objetos/{objetoId}/reportes`, JWT de una cuenta activa y verificada.

```json
{"motivo": "SPAM", "descripcion": "Explicación opcional"}
```

Motivos admitidos: `CONTENIDO_INAPROPIADO`, `INFORMACION_FALSA`, `SPAM`, `OTRO`.
Con OTRO la explicación es obligatoria. La descripción admite hasta 2000 caracteres.
El denunciante sale de la sesión, no de IDs del cuerpo. Solo publicaciones visibles
(ACTIVO/RECUPERADO); retiradas o inexistentes dan 404.

Primera denuncia: 201 con `id`, `objetoId`, `motivo`, `estado: "PENDIENTE"`,
`fechaReporte` UTC y `repetido: false`. Repetir por el mismo titular/publicación
devuelve la denuncia original, 200 y `repetido: true`, sin cambiar motivo, texto o fecha.
Los envíos concurrentes se serializan por publicación. No se borra/modera el objeto
automáticamente ni se exponen correos/contactos. Un tercero puede registrar su
propia denuncia, pero no modificar la de otra persona.

Cancelar en el cliente significa no enviar POST. Confirmación solo tras recibir
la respuesta real. La denuncia aparece en el listado administrativo Django de H28.
No necesita nuevas tablas; reutiliza `reportes` y conserva su historial.
