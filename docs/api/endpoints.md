# API — Endpoints

Contrato único de la plataforma. **Spring Boot** (`http://localhost:8081`) expone
la API pública (usuario + móvil) y **Django** (`http://localhost:8000`) expone la
API administrativa (React Admin).

- Formato de datos: JSON (`Content-Type: application/json`)
- Autenticación: `Authorization: Bearer <token>` (JWT)
- El password **nunca** se devuelve en ninguna respuesta.

---

## 1. Auth — Spring Boot

### POST `/api/auth/register`

Registra un usuario nuevo. Rol por defecto: `USUARIO`.

**Request**

```json
{
  "nombre": "Ana",
  "apellido": "Pérez",
  "correo": "ana@correo.com",
  "password": "Secreta123"
}
```

**Response** `201 Created`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "usuario": {
    "id": 5,
    "nombre": "Ana",
    "apellido": "Pérez",
    "correo": "ana@correo.com",
    "rol": "USUARIO",
    "estado": "ACTIVO",
    "fechaRegistro": "2026-10-04T12:00:00"
  }
}
```

**Errores**: `400` validación · `409` correo ya registrado

### POST `/api/auth/login`

**Request**

```json
{ "correo": "ana@correo.com", "password": "Secreta123" }
```

**Response** `200 OK` — mismo formato que register.

**Errores**: `401` credenciales inválidas o cuenta suspendida

---

## 2. Categorías — Spring Boot

### GET `/api/categorias`  _(público)_

**Response** `200 OK`

```json
[
  { "id": 1, "nombre": "Celular", "descripcion": "Teléfonos inteligentes y celulares", "estado": true }
]
```

---

## 3. Objetos — Spring Boot

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
  "publicadoPor": { "id": 1, "nombre": "Ana", "apellido": "Pérez" },
  "fotos": [ { "id": 1, "url": "/uploads/objetos/mochila-negra.jpg" } ]
}
```

### GET `/api/objetos`  _(público)_

Query params opcionales: `?tipo=PERDIDO|ENCONTRADO&estado=ACTIVO&categoriaId=3`

**Response** `200 OK` — arreglo de `ObjetoResponse` (excluye `ELIMINADO`).

### GET `/api/objetos/{id}`  _(público)_

**Response** `200 OK` · **Errores**: `404` (también si el objeto está `ELIMINADO`)

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

**Response** `201 Created` — `ObjetoResponse`
**Errores**: `400` validación · `401` sin token

### PUT `/api/objetos/{id}`  _(JWT — dueño o ADMIN)_

Mismo body que POST. **Response** `200 OK` · `403` no es el dueño · `404`

### DELETE `/api/objetos/{id}`  _(JWT — dueño o ADMIN)_

Borrado lógico: pasa a `estado = ELIMINADO`.
**Response** `204 No Content` · `403` · `404`

### GET `/api/objetos/buscar`  _(público)_

Query params: `?q=texto&tipo=&categoriaId=` — busca en nombre, descripción y
ubicación (LIKE). **Response** `200 OK` — arreglo de `ObjetoResponse`.

### GET `/api/objetos/categoria/{id}`  _(público)_

**Response** `200 OK` — objetos de esa categoría.

### GET `/api/objetos/ubicacion?ubicacion=texto`  _(público)_

**Response** `200 OK` — objetos cuya ubicación contiene el texto.

### GET `/api/objetos/fecha?desde=yyyy-MM-dd&hasta=yyyy-MM-dd`  _(público)_

Filtra por `fecha_objeto` (rango inclusivo; ambos opcionales).
**Response** `200 OK`.

### GET `/api/objetos/{id}/coincidencias`  _(público)_

Coincidencias **sugeridas** (cálculo simple, sin IA todavía): busca objetos del
tipo opuesto (`PERDIDO` ↔ `ENCONTRADO`) de la misma categoría; nivel `ALTA` si
coincide también la ubicación, `MEDIA` si no. No se persisten.

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

### PUT `/api/perfil`

**Request** `{ "nombre": "Ana", "apellido": "Pérez", "password": "Nueva123" }`
(`password` opcional)
**Response** `200 OK` — usuario actualizado.

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
