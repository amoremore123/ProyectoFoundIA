# API — Endpoints

> Entrega Sprint 1 (H1–H20): las rutas Django de reportes y gestión de
> categorías, y el admin general `/admin/`, se conservan documentados pero
> están deshabilitados por defecto. Se reactivan con `HABILITAR_EXTRAS=true`.
> El catálogo público de Spring `/api/categorias` sigue habilitado.
> Estado y pendientes: [revisión del 20 de octubre](../pruebas/avance-sprint1-2026-10-20.md).

Contrato único de la plataforma. **Spring Boot** (`http://localhost:8081`) expone
la API pública (usuario + móvil) y **Django** (`http://localhost:8000`) expone la
API administrativa (React Admin).

- Formato de datos: JSON (`Content-Type: application/json`)
- Autenticación: `Authorization: Bearer <token>` (JWT)
- En Spring Boot, las rutas privadas exigen que la cuenta siga activa y verificada,
  incluso si el JWT se emitió antes de una suspensión.
- El password **nunca** se devuelve en ninguna respuesta.

---

## 1. Auth — Spring Boot

Flujo (H11 + H12): **registro → código al correo → verificar → login**.
En desarrollo los correos llegan a Mailpit: http://localhost:8025

### POST `/api/auth/register`  _(H11)_

Crea la cuenta **sin verificar** (rol `USUARIO`), guarda la contraseña con
BCrypt y envía un código de 6 dígitos al correo (vence en 15 minutos).
El correo se guarda en minúsculas.

Reglas de contraseña: mínimo 8 caracteres, una mayúscula, una minúscula,
un número y un símbolo (máx. 72 bytes en UTF-8, no 72 caracteres).
El límite se valida antes de BCrypt; una contraseña que lo supera devuelve `400`.
Nombre y apellido: solo letras.

**Request**

```json
{
  "nombre": "Ana",
  "apellido": "Pérez",
  "correo": "ana@correo.com",
  "password": "Secreta123!"
}
```

**Response** `201 Created`

```json
{
  "mensaje": "Te enviamos un código de verificación a ana@correo.com. Ingrésalo para activar tu cuenta.",
  "correo": "ana@correo.com"
}
```

**Errores**: `400` validación · `409` correo ya registrado · `503` no se pudo enviar el correo (no se crea la cuenta)

### POST `/api/auth/verificar`  _(H11)_

**Request**

```json
{ "correo": "ana@correo.com", "codigo": "482913" }
```

**Response** `200 OK` — inicia sesión directamente:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "usuario": {
    "id": 5, "nombre": "Ana", "apellido": "Pérez", "correo": "ana@correo.com",
    "rol": "USUARIO", "estado": "ACTIVO", "fechaRegistro": "2026-10-04T12:00:00"
  }
}
```

**Errores**: `400` código incorrecto, expirado, invalidado o cuenta ya verificada · `401` cuenta suspendida

**Límite de intentos (servidor)**: cada código admite **5 intentos**. Los fallos se
guardan en la base (`usuarios.intentos_codigo`), así que cuentan igual desde otra
pestaña o llamando a la API directamente. La respuesta indica los intentos que
quedan ("Te quedan 4 intentos."); el quinto fallo **invalida el código** y, desde
ese momento, ni el código correcto verifica la cuenta ni se emite JWT. Hay que
pedir uno nuevo con `/reenviar-codigo`. Verificar con éxito reinicia el contador.

La verificación no permite iniciar sesión con una cuenta suspendida, aunque el
código sea correcto y siga vigente; no se consume el código ni se emite JWT.

### POST `/api/auth/reenviar-codigo`  _(H11)_

**Request** `{ "correo": "ana@correo.com" }`

**Response** `200 OK` `{ "mensaje": "Si el correo está registrado, te enviamos un nuevo código." }`

**Errores**: `400` cuenta ya verificada · `429` reenvío antes de tiempo · `503` fallo de correo

**Frecuencia de reenvío (servidor)**: entre dos envíos deben pasar **60 segundos**,
contados desde el último envío guardado en `usuarios.codigo_enviado_en` (incluye el
correo del registro). Antes de ese tiempo responde `429 Too Many Requests` con la
cabecera `Retry-After` (segundos) y el mensaje "Espera N segundos antes de pedir
otro código."; no genera código nuevo ni envía correo. Un reenvío válido invalida
el código anterior y reinicia los intentos. Si el correo falla (`503`), el código
anterior sigue vigente.

Bases existentes: aplicar `database/migracion_h11_limites_codigo.sql` (solo agrega columnas).

### POST `/api/auth/login`  _(H12)_

**Request**

```json
{ "correo": "ana@correo.com", "password": "Secreta123!" }
```

**Response** `200 OK` — mismo formato que `/verificar` (token JWT HS256, 24 h).

**Errores**

| Código | Cuándo | `mensaje` de ejemplo |
| ------ | ------ | -------------------- |
| `400` | Falta correo/contraseña | `El campo correo es obligatorio` |
| `401` | Credenciales incorrectas | `Correo o contraseña incorrectos. Te quedan 3 intentos.` |
| `401` | Cuenta suspendida | `Tu cuenta está suspendida. Contacta al administrador.` |
| `403` | Correo sin verificar | `Debes verificar tu correo antes de iniciar sesión.` |
| `423` | 5.º intento fallido o cuenta aún bloqueada | `Tu cuenta está bloqueada por demasiados intentos fallidos. Intenta de nuevo en 12 minutos.` |

Bloqueo: al **5.º intento fallido consecutivo** la cuenta se bloquea **15 minutos**
(columna `bloqueado_hasta`). Mientras dure, se rechaza incluso la contraseña
correcta. Un login exitoso reinicia el contador `intentos_fallidos`.

---

## 2. Categorías — Spring Boot

### GET `/api/categorias`  _(público)_

Devuelve el catálogo ordenado alfabéticamente por nombre, incluidas las categorías desactivadas
(`estado = false`), para consultar publicaciones anteriores. En Inicio y
búsqueda se identifican como "(desactivada)"; esta regla no cambia la publicación.

**Response** `200 OK`

```json
[
  { "id": 1, "nombre": "Celular", "descripcion": "Teléfonos inteligentes y celulares", "estado": true }
]
```

---

## 3. Objetos — Spring Boot

Las consultas públicas de listado, búsqueda, categoría, ubicación y fecha
solo devuelven objetos `ACTIVO` o `RECUPERADO`; excluyen `OCULTO` y `ELIMINADO`.
Se ordenan por `fechaPublicacion` descendente y, en empates, por `id` descendente.
El detalle y las coincidencias de un objeto no público devuelven `404`.
La consulta privada de publicaciones propias mantiene su comportamiento.

Casos de prueba: [H6 — Buscar objetos](../pruebas/casos-prueba-h6.md) y
[H7 — Filtrar objetos por categoría](../pruebas/casos-prueba-h7.md).

### ObjetoResponse (formato estándar de respuesta)

```json
{
  "id": 1,
  "nombre": "Mochila negra con libros",
  "descripcion": "Mochila negra con una pulsera roja...",
  "ubicacion": "Comedor principal",
  "latitud": 19.4326100,
  "longitud": -99.1332000,
  "fechaObjeto": "2026-10-02",
  "tipo": "ENCONTRADO",
  "estado": "ACTIVO",
  "fechaPublicacion": "2026-10-02T12:30:00",
  "categoria": { "id": 3, "nombre": "Mochila" },
  "publicadoPor": { "id": 1, "nombre": "Ana", "apellido": "P." },
  "fotos": [ { "id": 1, "url": "/uploads/objetos/mochila-negra.jpg" } ]
}
```

**Privacidad (H19)**: `publicadoPor` es la vista pública del autor: solo `id`,
`nombre` e **inicial del apellido**. Nunca incluye correo, apellido completo, rol
ni estado. Reglas completas en [`docs/privacidad-datos.md`](../privacidad-datos.md).

### GET `/api/objetos`  _(público)_

Query params opcionales: `?tipo=PERDIDO|ENCONTRADO&estado=ACTIVO&categoriaId=3`

**Response** `200 OK` — arreglo de `ObjetoResponse`. Pedir explícitamente
`estado=OCULTO` o `estado=ELIMINADO` devuelve `[]`, sin exponer esos objetos.

**Errores**: `400` parámetro con formato incorrecto · `404` categoría inexistente

### GET `/api/objetos/{id}`  _(público)_

**Response** `200 OK` · **Errores**: `404` si no existe o está `OCULTO`/`ELIMINADO`

### POST `/api/objetos`  _(JWT)_

**Request**

```json
{
  "nombre": "Celular Samsung",
  "descripcion": "Color azul, funda transparente",
  "categoriaId": 1,
  "ubicacion": "Aula 204",
  "latitud": 19.4331000,
  "longitud": -99.1345000,
  "fechaObjeto": "2026-10-01",
  "tipo": "PERDIDO"
}
```

`latitud`/`longitud` son opcionales. `fechaObjeto` = fecha en que se perdió o
encontró el objeto. El autor se toma del token.

El nombre es obligatorio y admite hasta 150 caracteres. Superar ese límite
devuelve `400` antes de guardar, no un error de base de datos.
La descripción es obligatoria y admite hasta **2000 caracteres** (contados como
caracteres reales: una tilde o un emoji cuentan como 1). Con utf8mb4 cada carácter
ocupa como máximo 4 bytes, así que 2000 × 4 = 8000 bytes, siempre dentro de los
65.535 bytes de la columna `TEXT`. El formulario web ya aplica este límite
(`MAX_DESCRIPCION` en `frontend/usuario/src/utils/validaciones.js`); la API debe
validarlo también y devolver `400` con error de campo antes de guardar (H1).
La ubicación es obligatoria, no puede quedar en blanco y admite hasta 255
caracteres. Es válida una dirección escrita a mano sin coordenadas.
Si se envían coordenadas, la latitud debe estar entre -90 y 90 y la longitud
entre -180 y 180 (límites incluidos). Fuera de esos rangos devuelve `400`.

En el formulario web, editar la dirección después de usar GPS descarta las
coordenadas anteriores. Una respuesta GPS pendiente tampoco sobrescribe una
edición manual; si no hay geocodificación, se muestra el texto de las coordenadas.

**Response** `201 Created` — `ObjetoResponse`
**Errores**: `400` validación · `401` sin token

### POST `/api/objetos/{id}/fotos`  _(H2, JWT del autor)_

Sube una imagen con `multipart/form-data`, parte `archivo`. Se admiten JPEG y PNG,
hasta 5 MiB y 40 megapíxeles. El servidor identifica el formato real del archivo,
no confía en el nombre ni en el `Content-Type`. Solo el autor de la publicación
puede añadir fotos. El archivo se guarda con nombre generado en `UPLOAD_DIRECTORY`
(por defecto `uploads/`) y la fila asociada se guarda en `fotos`; el endpoint
público `/uploads/{nombre-generado}` permite mostrarla en el detalle y las tarjetas.
En Docker, el directorio se conserva en el volumen `uploads_data`.

**Response** `201 Created` `{ "id": 4, "url": "/uploads/<uuid>.png" }`

**Errores**: `400` formato inválido o tamaño superior · `401` sin token · `403`
la publicación es de otra persona · `404` objeto inexistente/eliminado.
Una carga rechazada no crea la asociación ni deja archivo en disco.

### PUT `/api/objetos/{id}`  _(H5, JWT — autor)_

Mismo body y validaciones que POST. Solo el autor del JWT puede editar; ADMIN no
puede usar esta ruta (la moderación se define aparte en H15). **Response** `200 OK`
· `400` validación · `401` sin sesión · `403` no es el autor · `404` no existe o
ya fue eliminado.

### DELETE `/api/objetos/{id}`  _(H5, JWT — autor)_

Borrado lógico: pasa a `estado = ELIMINADO`; no elimina físicamente el objeto ni
sus fotos. Las consultas públicas lo excluyen y `/api/perfil/objetos` lo mantiene
como historial. Solo el autor puede borrarlo; ADMIN no puede usar esta ruta.
**Response** `204 No Content` · `401` sin sesión · `403` no es el autor · `404`
no existe o ya fue eliminado.

### POST `/api/contactos`  _(H18, JWT)_

Inicia una solicitud de contacto desde una publicación propia `PERDIDO` hacia
una publicación `ENCONTRADO` ajena. Ambas deben estar activas y pertenecer a la
misma categoría. El servidor crea o reutiliza la coincidencia, registra el
contacto y genera una notificación in-app para el autor del hallazgo. El mensaje
es genérico: no revela correo, teléfono ni apellido completo. Contactar no
reclama ni cambia el estado del objeto.

**Request**

```json
{ "objetoPerdidoId": 15, "objetoEncontradoId": 27 }
```

**Response** `201 Created` (primera solicitud) o `200 OK` (repetición
idempotente): `{ "id": 8, "coincidenciaId": 4, "estado": "PENDIENTE",
"fechaContacto": "2026-10-19T12:00:00", "repetido": false }`.

**Errores**: `400` pareja de objetos inválida · `401` sin token · `403` el
solicitante no es autor del objeto perdido o intenta contactarse a sí mismo ·
`404` objeto inexistente/no activo. Las repeticiones del mismo autor y pareja
devuelven el mismo contacto y no vuelven a notificar.

### GET `/api/objetos/buscar`  _(público)_

Query params opcionales: `q`, `tipo` (`PERDIDO` o `ENCONTRADO`) y `categoriaId`.
Ejemplo: `?q=mochila&tipo=ENCONTRADO&categoriaId=3`.

- Busca coincidencias parciales en nombre, descripción o ubicación, sin
  distinguir mayúsculas y recortando los espacios externos de `q`.
- `%`, `_` y `!` se interpretan como texto literal, no como comodines.
- Sin `q`, o con texto vacío/solo espacios, devuelve los objetos públicos que
  cumplen los demás filtros. Texto, tipo y categoría se combinan con AND.
- Una categoría desactivada sigue siendo consultable; una inexistente devuelve `404`.

**Response** `200 OK` — arreglo de `ObjetoResponse`; `[]` si no hay coincidencias.
**Errores**: `400` tipo o categoría con formato incorrecto · `404` categoría inexistente

El parámetro `ubicacion` de la URL de React (`/buscar`) es un filtro adicional
del frontend sobre esta respuesta; no es un parámetro independiente de este
endpoint. `q` sí incluye el campo ubicación en la búsqueda.

### GET `/api/objetos/categoria/{id}`  _(público)_

**Response** `200 OK` — objetos públicos de esa categoría; `[]` si no hay
publicaciones públicas. También admite categorías desactivadas.
**Errores**: `400` id con formato incorrecto · `404` categoría inexistente

### GET `/api/objetos/ubicacion?ubicacion=texto`  _(público)_

**Response** `200 OK` — objetos cuya ubicación contiene el texto.

### GET `/api/objetos/fecha?desde=yyyy-MM-dd&hasta=yyyy-MM-dd`  _(público)_

Filtra por `fecha_objeto` (rango inclusivo; ambos opcionales).
**Response** `200 OK`.

### GET `/api/objetos/{id}/coincidencias`  _(público)_

Coincidencias **sugeridas** (cálculo simple, sin IA todavía): busca objetos del
tipo opuesto (`PERDIDO` ↔ `ENCONTRADO`) de la misma categoría; nivel `ALTA` si
coincide también la ubicación, `MEDIA` si no. No se persisten.

**Errores**: `404` si el objeto de origen no existe o está `OCULTO`/`ELIMINADO`

**Response** `200 OK`

```json
[
  {
    "objetoId": 2,
    "nombre": "Celular Samsung Galaxy A54",
    "tipo": "PERDIDO",
    "categoria": "Celular",
    "ubicacion": "Aula 204",
    "fechaObjeto": "2026-10-01",
    "porcentaje": 90.00,
    "nivel": "ALTA"
  }
]
```

---

## 4. Perfil — Spring Boot  _(JWT)_

### GET `/api/perfil`

**Response** `200 OK` — usuario actual (sin password).

### PUT `/api/perfil`  _(H13)_

Modifica **solo el perfil del usuario del token**. No recibe `id`, así que no
se puede modificar a otra persona cambiando un número.

**Request**

```json
{ "nombre": "Ana", "apellido": "Pérez", "password": "Nueva456#", "passwordActual": "Segura123!" }
```

| Campo | Regla |
| --- | --- |
| `nombre`, `apellido` | Obligatorios, hasta 100 caracteres, solo letras, espacios, `'` y `-` (mismas reglas que el registro). Se guardan sin espacios al inicio ni al final. |
| `password` | Opcional. Vacío o ausente = no se cambia. Si se envía, debe cumplir las reglas del registro: 8+ caracteres, mayúscula, minúscula, número, símbolo y máximo 72 bytes. Se guarda con hash BCrypt. |
| `passwordActual` | Obligatoria **solo si se cambia la contraseña**. Debe coincidir con la contraseña actual. |

Cualquier otro campo (`rol`, `estado`, `correo`, `id`, `verificado`) se ignora:
un usuario no puede elevar su rol ni cambiar su estado o su correo.

**Response** `200 OK` — usuario actualizado (nunca incluye contraseña ni hash).

**Errores**: `400` dato inválido, contraseña nueva débil o contraseña actual
ausente/incorrecta (en ese caso no se guarda **ningún** cambio) · `401` sin sesión.
La contraseña actual incorrecta devuelve 400 y no 401, para no cerrar la sesión
del usuario en la web.

### GET `/api/perfil/objetos`

**Response** `200 OK` — publicaciones propias (incluye `ELIMINADO`).

---

## 5. Notificaciones — Spring Boot  _(JWT)_

### GET `/api/notificaciones`

**Response** `200 OK`

```json
[
  {
    "id": 1,
    "titulo": "Nuevo objeto encontrado",
    "mensaje": "Se publicó un objeto cerca de ti",
    "tipo": "SISTEMA",
    "leida": false,
    "fechaCreacion": "2026-10-04T12:00:00"
  }
]
```

### PUT `/api/notificaciones/{id}/leida`

**Response** `200 OK` — notificación marcada como leída · `404`

### POST `/api/objetos/{id}/reclamos`  _(H20, JWT)_

Inicia un reclamo pendiente sobre una publicación activa de tipo `ENCONTRADO`.
No requiere datos personales ni cambia el estado del objeto: registra el objeto
y la cuenta reclamante, y avisa al propietario mediante una notificación in-app.
Solo otra cuenta puede reclamar; cada cuenta tiene un reclamo por objeto. Una
repetición devuelve el reclamo existente sin duplicar la notificación.

**Request**: sin body.

**Response** `201 Created` (nuevo) o `200 OK` (repetido):
`{ "id": 3, "objetoId": 27, "estado": "PENDIENTE", "fechaReclamo": "2026-10-19T12:00:00", "repetido": false }`.

**Errores**: `400` objeto que no es hallazgo activo · `401` sin sesión · `403`
el usuario es propietario del objeto · `404` objeto inexistente.

---

## 6. API Admin — Django  (React Admin)

Base: `http://localhost:8000` — todos los endpoints usan prefijo `/api/admin/`
y requieren `Authorization: Bearer <token>` (excepto login).
Las listas usan paginación DRF: `{ "count": 12, "results": [...] }`.

### POST `/api/admin/auth/login/`

**Request** `{ "correo": "admin@foundia.dev", "password": "Admin123!" }`

**Response** `200 OK`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "usuario": { "id": 1, "nombre": "Admin", "apellido": "Sistema", "correo": "admin@foundia.dev", "rol": "ADMIN" }
}
```

**Errores**: `401` credenciales inválidas · `403` el usuario no es ADMIN

### GET `/api/admin/dashboard/`

**Response** `200 OK`

```json
{ "usuarios": 4, "objetosActivos": 6, "reportesPendientes": 0, "categorias": 8 }
```

### CRUDs  _(solo ADMIN)_

| Recurso                          | Rutas                                     | Notas                                        |
| -------------------------------- | ----------------------------------------- | -------------------------------------------- |
| Usuarios                         | `GET/POST /api/admin/usuarios/` · `GET/PUT/PATCH/DELETE /api/admin/usuarios/{id}/` | `PATCH { "estado": "SUSPENDIDO" }` para suspender |
| Categorías                       | `GET/POST /api/admin/categorias/` · `GET/PUT/PATCH/DELETE /api/admin/categorias/{id}/` | `PATCH { "estado": false }` para desactivar  |
| Publicaciones (objetos)          | `GET/POST /api/admin/publicaciones/` · `GET/PUT/PATCH/DELETE /api/admin/publicaciones/{id}/` | `PATCH { "estado": "OCULTO" }` para ocultar  |
| Reportes                         | `GET/POST /api/admin/reportes/` · `GET/PUT/PATCH/DELETE /api/admin/reportes/{id}/` | `PATCH { "estado": "RESUELTO", "fechaResolucion": "2026-10-04T12:00:00" }` |

**Errores comunes**: `400` validación · `401` sin token · `403` no es ADMIN · `404` no existe

---

## 7. Formato de errores (Spring Boot)

```json
{
  "timestamp": "2026-10-04T12:00:00",
  "status": 400,
  "error": "Bad Request",
  "mensaje": "El campo nombre es obligatorio",
  "path": "/api/objetos"
}
```

| Código | Cuándo                          |
| ------ | ------------------------------- |
| 400    | Validación / body inválido      |
| 401    | Sin token o token inválido      |
| 403    | No tiene permiso (dueño / rol)  |
| 404    | Recurso no encontrado           |
| 409    | Correo duplicado (register)     |
| 500    | Error interno                   |
