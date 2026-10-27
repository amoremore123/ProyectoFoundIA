# Sprint 2 — contratos API/datos de Leonel

Rama `dev/leonel`. No es cierre de historias completas: web y Android se integran
en los turnos de Yajaira y Amore. No modificar bases compartidas ni llevar entregas
parciales a `main`. Referencias H21/H22 en [endpoints](endpoints.md).

## H23 — nuevas coincidencias in-app

Publicar o editar un objeto compara con el motor H16 y avisa una sola vez al
titular activo/verificado del perdido por cada pareja nueva. El hallador no
recibe un aviso de propiedad. Misma categoría, autores distintos, objetos activos,
umbral H16; se respetan descartes. La escritura del objeto y el aviso son atómicos.
No se usa push, correo ni chat; consultar o recalcular por GET no escribe datos.

`GET /api/notificaciones` conserva el contrato y añade `enlace` nullable. Para
`COINCIDENCIA_NUEVA` es `/coincidencias/{perdidoId}-{encontradoId}`. El enlace no
concede permisos y su destino vuelve a validar disponibilidad. La bandeja y lectura
siguen siendo propias del JWT; no se incluyen correos ni contactos en el aviso.

Migración: `database/migracion_h23_avisos_coincidencias.sql`, incremental y
re-ejecutable solo tras autorización del destino. La unicidad de `avisos_coincidencia`
evita duplicados sin crear registros de coincidencia al consultar.

Pruebas focalizadas H2: **57 aprobadas**, incluidas 5 de H23, H18, H21, H22 y
publicación. Clases `AvisosCoincidenciaIntegrationTest` y su variante explícita
`AvisosCoincidenciaMySqlQa`; nunca usar la variante MySQL contra datos compartidos.
Evidencia local: `foundia-leonel-s2-completo-20261025-181525/h23-final.log`.
Los mismos **5 casos H23 pasaron en MySQL 8.4.6 / REPEATABLE READ**, base nueva
`foundia_s2_qa_7f107def8c46`, incluido el evento concurrente; `h23-mysql.log`.
