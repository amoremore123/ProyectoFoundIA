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

# H30 y H31 — recuperación y rankings (Yajaira)

El mismo `GET /api/admin/dashboard/` (mismos permisos y mismos filtros `desde`/`hasta`
sobre `fecha_publicacion`, UTC, solo publicaciones ACTIVO/RECUPERADO) agrega:

```json
"recuperacion": {"promedioHoras": 27.0, "promedioDias": 1.1, "medianaHoras": 27.0,
                 "minimoHoras": 6.0, "maximoHoras": 48.0, "muestra": 2,
                 "sinFecha": 0, "fechasIncoherentes": 0},
"categoriasTop": [{"id": 2, "nombre": "Celulares", "total": 3, "perdidos": 3, "encontrados": 0}],
"ubicacionesTop": [{"nombre": "Biblioteca", "total": 3, "perdidos": 2, "encontrados": 1}],
"sinUbicacion": 2
```

- **H30** `recuperacion`: horas entre `fecha_publicacion` y `fecha_recuperacion`
  (columna de H22) de los objetos PERDIDOS en estado RECUPERADO. `muestra` indica
  cuántos entraron al cálculo. Los recuperados antiguos sin fecha no se inventan
  (`sinFecha`) y una recuperación anterior a la publicación se excluye
  (`fechasIncoherentes`). Sin recuperaciones, los tiempos son `null` (no 0).
- **H31** "reportes" = publicaciones de objetos (perdidos + encontrados), no
  denuncias de moderación. Se devuelven como máximo 5 por lista, ordenadas por
  `total` (desempate por nombre). La ubicación es texto libre: se agrupan
  variantes de mayúsculas y espacios ("Biblioteca", " biblioteca ") y se muestra
  la forma más usada. Las publicaciones sin ubicación se informan en `sinUbicacion`.
- No se expone ningún dato de personas.

Requiere la migración `database/migracion_h22_fecha_recuperacion.sql` (ya entregada en H22).

Pruebas: `python manage.py test nucleo.test_metricas_h30_h31 --settings=config.settings_test --noinput`.
