# H29 — tablero administrativo

`GET /api/admin/dashboard/?desde=2026-10-01&hasta=2026-10-31` en Django.
Solo un ADMIN activo puede acceder (sin JWT: 401, otro rol/suspendido: 403).

Las cuatro tarjetas históricas (`usuarios`, `objetosActivos`,
`reportesPendientes`, `categorias`) se conservan como conteos generales.
El objeto nuevo `objetos` contiene indicadores filtrados:

- `perdidos`: publicaciones PERDIDO visibles, activas o recuperadas.
- `encontrados`: publicaciones ENCONTRADO visibles, activas o recuperadas.
- `recuperados`: **subconjunto** de perdidos con estado RECUPERADO.
- `activos`: publicaciones visibles con estado ACTIVO.

No sumar esos cuatro números como si fueran grupos excluyentes.
Se excluyen ocultos/eliminados. El rango es inclusivo, se aplica a
`fecha_publicacion` y usa UTC. Ambos filtros son opcionales; formatos o rangos
inválidos y parámetros desconocidos devuelven 400. `rango` explica los límites.
Sin resultados se devuelven ceros, no un error ni datos ficticios.

Pruebas con datos conocidos:
`python manage.py test nucleo.test_metricas --settings=config.settings_test --noinput`.
