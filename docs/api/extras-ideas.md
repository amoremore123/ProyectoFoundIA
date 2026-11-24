# Extras después del reparto del Sprint 4

Responsable: Yajaira · Rama: `frontend/web` · Noviembre de 2026.

Cinco ideas que se agregaron después de cerrar las historias del reparto, más tres correcciones que
salieron al probarlas. Todo funciona con los servicios reales y tiene pruebas unitarias, de
integración y de punta a punta (`frontend/usuario/e2e/extras.e2e.js`).

| # | Idea | Dónde se ve | Backend |
| --- | --- | --- | --- |
| 1 | Etiqueta QR preventiva | Web: Perfil y `/etiqueta/{código}` | Spring |
| 2 | Coincidencias por imagen con IA | Web: detalle del objeto | Spring + Groq |
| 3 | Mapa de pérdidas de la sede Santa Anita | Panel: Estadísticas | Django |
| 4 | Impacto: objetos devueltos por mes | Panel: Dashboard | Django |
| 5 | Reconocimiento a quien devuelve | Web: Perfil | Spring |

## 1. Etiqueta QR preventiva

La persona registra algo suyo que todavía no perdió (calculadora, casaca, USB), descarga una etiqueta
con un QR y la pega. Si alguien la encuentra, escanea el QR, ve **solo el nombre y la descripción** que
eligió el dueño y le escribe por el chat de FoundIA.

| Método | Ruta | Sesión | Uso |
| --- | --- | --- | --- |
| `GET` | `/api/etiquetas` | Sí | Mis etiquetas activas |
| `POST` | `/api/etiquetas` | Sí | Crear: `{"nombre": "...", "descripcion": "..."}` → `201` |
| `DELETE` | `/api/etiquetas/{id}` | Sí | Desactivar (solo el dueño; si no, `404`) → `204` |
| `GET` | `/api/etiquetas/publica/{codigo}` | No | Lo que ve quien escanea: `{nombre, descripcion, propia}` |
| `POST` | `/api/etiquetas/publica/{codigo}/avisar` | Sí | Escribir al dueño: `{"mensaje": "..."}` → `201` con la conversación |

Reglas:

- El código del QR tiene 22 caracteres al azar y no revela quién es el dueño. La página pública no
  muestra su nombre completo, correo ni teléfono; en el chat se ve solo el primer nombre, igual que en
  los demás chats.
- `nombre`: obligatorio, hasta 80 caracteres. `descripcion`: opcional, hasta 200. Ninguno admite
  enlaces. Hasta 30 etiquetas activas por persona.
- Para avisar hay que iniciar sesión: así no llegan mensajes anónimos. Después de iniciar sesión, la web
  vuelve a la etiqueta escaneada (`state.volverA` en `/login`, solo rutas internas).
- Quien escanea puede avisar a **5 dueños distintos por día** (`429` después). Si vuelve a escanear la
  misma etiqueta, sigue en la misma conversación. El dueño no puede avisarse a sí mismo (`400`) y se
  respetan los bloqueos del chat.
- El dueño recibe la notificación «Encontraron tu {nombre}» por los canales que eligió para el chat.
- Al desactivar la etiqueta, el QR responde `404` («ya no está activa») y sus conversaciones quedan
  solo para leer.
- La etiqueta para imprimir (PNG de 600 × 760 px) se genera en el navegador: no pasa por el servidor.

Base de datos (`database/migracion_extra_etiquetas_qr.sql`, se puede ejecutar varias veces; también
está en `script.sql`):

- Tabla nueva `pertenencias` (`usuario_id`, `nombre`, `descripcion`, `codigo` único, `activa`).
- `conversaciones.objeto_id` pasa a ser opcional y se agrega `pertenencia_id`. Las conversaciones de
  una etiqueta tienen `tipo = 'ETIQUETA'` y no tienen publicación.

> Quien tenga su propia base debe ejecutar la migración (con respaldo antes): sin ella, el chat falla al
> leer la columna nueva.

## 2. Coincidencias por imagen con IA

Quien publicó un objeto con foto puede pulsar **«Comparar fotos con IA»** en sus coincidencias
sugeridas. La IA mira su foto junto a la de cada coincidencia y dice qué tan parecidas son, con una frase
corta del porqué.

`POST /api/objetos/{id}/coincidencias/comparar-fotos` (con sesión, solo quien publicó):

```json
{
  "resultados": [
    { "objetoId": 13, "coincidenciaId": "95-13", "parecido": 95, "nivel": "ALTO",
      "motivo": "Mismos lentes con marco metálico y lentes oscuros idénticos." },
    { "objetoId": 11, "coincidenciaId": "95-11", "parecido": 0, "nivel": "BAJO",
      "motivo": "La imagen muestra una chaqueta de ropa, no lentes." }
  ],
  "sinFoto": 1
}
```

- `nivel`: `ALTO` desde 75, `MEDIO` desde 45, `BAJO` por debajo. `parecido` es `null` si la IA no
  respondió por esa foto.
- Es una ayuda: no cambia el porcentaje por texto ni confirma nada.
- Se comparan las 3 mejores coincidencias que tienen foto, en **una sola consulta**, con las fotos
  reducidas a 384 px. El plan gratuito de Groq admite unos 7000 tokens de entrada por minuto y cada foto
  de 640 px gastaba unos 2000. El resultado de cada par de fotos queda en memoria: pulsar otra vez no
  gasta cupo.
- Errores: `400` si la publicación no tiene foto, `404` si no es tuya, `429` por el límite por minuto,
  `503` si la IA no está configurada o está saturada («Espera un minuto y vuelve a comparar»).
- El motivo pasa por el mismo filtro de datos personales que la búsqueda por foto. Los registros
  técnicos nunca guardan imágenes ni descripciones.

## 3. Mapa de pérdidas (panel, Estadísticas)

`GET /api/admin/metricas/mapa/?tipo=PERDIDO|ENCONTRADO&desde=AAAA-MM-DD&hasta=AAAA-MM-DD` (Django,
solo `ADMIN`):

```json
{ "puntos": [ { "lat": -12.0439, "lng": -76.9529, "total": 4, "perdidos": 3, "encontrados": 1, "lugar": "Biblioteca" } ],
  "conCoordenadas": 12, "sinCoordenadas": 80 }
```

- Usa las coordenadas que se guardan al publicar (M4). Los puntos se agrupan redondeando a 4 decimales
  (unos 11 m): no se ve la ubicación exacta de nadie. No cuenta publicaciones `ELIMINADO` ni `OCULTO`.
- El mapa (Leaflet con OpenStreetMap) abre siempre en el contorno del campus Tecsup, sede Santa Anita,
  tomado de OpenStreetMap. Los puntos a más de unos 300 m se avisan como «fuera del campus» (por
  ejemplo, datos de prueba) y no mueven el encuadre. «Ver la sede completa» vuelve al campus.
- Debajo hay una tabla con los 10 lugares con más objetos, para quien no puede ver el mapa.

## 4. Impacto (panel, Dashboard)

`GET /api/admin/metricas/impacto/` (Django, solo `ADMIN`): objetos devueltos en total y en los
últimos 30 días, devueltos por mes en los últimos 6 meses (en la biblioteca y entre personas), tiempo
promedio hasta la devolución, porcentaje de perdidos recuperados y de encontrados devueltos, y el
estado de la custodia (en custodia, entregadas, dispuestas).

## 5. Reconocimiento a quien devuelve (web, Perfil)

`GET /api/perfil/reconocimiento` (Spring, con sesión):

```json
{ "entregados": 3, "devueltos": 1,
  "nivel": { "minimo": 3, "nombre": "Buen compañero", "descripcion": "Ya entregaste 3 objetos: tu ayuda se nota." },
  "siguiente": { "nombre": "Guardián de Tecsup", "faltan": 2 } }
```

- Solo cuenta lo que la biblioteca verificó: objetos que la persona encontró y que la biblioteca
  recibió en custodia (`entregados`), y de ellos los que su dueño ya retiró (`devueltos`). Así no se
  puede inflar con publicaciones falsas.
- Niveles: 1 «Primera entrega», 3 «Buen compañero», 5 «Guardián de Tecsup», 10 «Leyenda FoundIA».
- Es privado: solo lo ve la propia persona y no hay ranking. Si la consulta falla, la tarjeta no aparece.

## Correcciones que salieron al probar

### Fotos de los datos de ejemplo

`database/seed.sql` publicaba tres objetos (mochila negra, celular Samsung, llavero) con fotos en
`/uploads/objetos/...` que nunca estuvieron en el repositorio: respondían `404`. Ahora esas tres fotos
vienen dentro de la API (`backend/springboot/src/main/resources/semilla/uploads/`, con sus créditos de
Unsplash) y Spring las sirve cuando no están en el volumen de subidas. Funcionan en cualquier base sin
copiar archivos. Lo que no existe sigue dando `404` y las fotos de perfil siguen privadas.

### Coincidencias sin parecido en el nombre

La categoría (25 puntos), el lugar (20) y la fecha (15) sumaban 60, más que el umbral de 40, aunque los
nombres no tuvieran nada en común: unos lentes salían como coincidencia de una billetera. Ahora:

- Si los nombres no comparten ninguna palabra que describa el objeto, **no hay coincidencia**. La regla
  está en `CoincidenciaService.puntuar`, que usan la lista, el detalle, los avisos, el contacto y la
  confirmación.
- No cuentan las palabras vacías («de», «con», «la»…) ni los códigos que mezclan letras y números
  («e2e», «a9977d54»). Los números solos sí cuentan (un modelo, como «991»).
- Los plurales se igualan: «Lentes de sol» y «Lente de sol» dan 100 %.

### Cerrar el objeto encontrado cuando vuelve a su dueño

Antes, cuando el dueño confirmaba «Recuperé mi objeto» (H22), solo se cerraba su publicación de
perdido: la de quien lo encontró seguía abierta y se podía reclamar. Ahora:

- Al confirmar la recuperación, **las dos publicaciones** pasan a `RECUPERADO` con la misma fecha. Si dos
  parejas compiten por el mismo perdido, solo se cierra el hallazgo de la pareja aceptada.
- `POST /api/objetos/{id}/devuelto` (con sesión): quien encontró un objeto y lo tiene en mano lo marca
  como devuelto. Solo para publicaciones `ENCONTRADO` propias y `ACTIVO`; repetirlo no cambia nada. Los
  objetos en custodia los cierra la biblioteca al entregarlos (`400` con ese mensaje).
- En el detalle se ve el estado: «Buscando a su dueño», «Su dueño lo busca» o «Devuelto · caso
  cerrado». Un objeto devuelto no ofrece «Reclamar este objeto» y sus chats quedan solo para leer.
