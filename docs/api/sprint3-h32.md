# H32 — Exportar reportes a PDF o Excel por rango de fechas

> Estado de esta entrega: API/datos implementados y probados en Django.
> Los botones con carga, error y rango vacío en el panel web quedan en la capa
> de Yajaira; el contrato y los archivos ya están disponibles.

## Endpoints

| Método | Ruta | Formato |
| --- | --- | --- |
| `GET` | `/api/admin/dashboard/exportar.xlsx` | Excel (`.xlsx`) |
| `GET` | `/api/admin/dashboard/exportar.pdf` | PDF A4 |

Solo `ADMIN` activo. Sin token: `401`; `BIBLIOTECA`, `USUARIO` o `ADMIN`
suspendido: `403`.

Aceptan los mismos filtros que el tablero:

| Parámetro | Ejemplo | Efecto |
| --- | --- | --- |
| `desde` | `2026-10-01` | publicaciones desde esa fecha (inclusive) |
| `hasta` | `2026-10-07` | publicaciones hasta esa fecha (inclusive) |

Sin filtros se exporta todo el historial. Los filtros se validan igual que en
`GET /api/admin/dashboard/`: fecha inválida, rango invertido o parámetro
desconocido responden `400` y no generan archivo.

## Contenido

Ambos formatos usan el mismo diccionario que devuelve el tablero, por lo que
las cifras coinciden exactamente:

- **Objetos del periodo**: perdidos, encontrados, recuperados y activos.
- **Tiempo de recuperación**: promedio en horas y días, mediana, mínimo,
  máximo, muestra, recuperaciones sin fecha y fechas incoherentes.
- **Estado actual del sistema**: usuarios registrados, objetos activos,
  reportes pendientes y categorías. Estas cuatro tarjetas no dependen del
  rango; el archivo lo indica expresamente.
- **Categorías con más publicaciones**: hasta 5, con total, perdidos y
  encontrados.
- **Ubicaciones con más publicaciones**: hasta 5, con total, perdidos y
  encontrados, más el conteo de publicaciones sin ubicación.
- **Metadatos**: periodo, campo usado (`fecha_publicacion`), zona horaria
  (`UTC`), fecha de emisión y responsable.

El responsable es el administrador autenticado: nombre, apellido y correo,
tomados del token; el cliente no puede enviarlo. La fecha de emisión la pone
el servidor. En el PDF también aparecen en el pie el nombre del sistema y el
número de página.

## Estructura del Excel

| Hoja | Contenido |
| --- | --- |
| `Resumen` | metadatos, objetos del periodo, tiempo de recuperación y estado actual |
| `Categorías` | ranking con puesto, total, perdidos y encontrados |
| `Ubicaciones` | ranking con puesto, total, perdidos y encontrados y el total sin ubicación |

Los valores sin dato se muestran como `—`; un rango sin publicaciones genera
archivos válidos con ceros y la leyenda «Sin publicaciones en el periodo».

## Nombre de los archivos

El servidor envía `Content-Disposition: attachment` con un nombre estable:

| Rango | Nombre |
| --- | --- |
| desde y hasta | `foundia-reporte_2026-10-01_2026-10-07.xlsx` / `.pdf` |
| solo desde | `foundia-reporte_desde-2026-10-01.xlsx` / `.pdf` |
| solo hasta | `foundia-reporte_hasta-2026-10-07.xlsx` / `.pdf` |
| sin filtros | `foundia-reporte_completo-2026-11-09.xlsx` / `.pdf` |

## Tipos de contenido

- Excel: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
- PDF: `application/pdf`

Ambos incluyen `X-Content-Type-Options: nosniff`.

## Dependencias y pruebas

`openpyxl` se agregó a `backend/django-admin/requirements.txt` para generar el
libro. El PDF usa ReportLab, ya requerido por H41/H43. Las pruebas
`nucleo/test_sprint3_h32.py` verifican permisos, rango inválido, rango vacío,
nombres de archivo y que las cifras del Excel y del PDF coincidan con el
tablero.
