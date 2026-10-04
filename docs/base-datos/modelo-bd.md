# Modelo de base de datos

Base: **`objetos_perdidos_db`** (MySQL 8, InnoDB, utf8mb4).
Fuente de verdad: [`database/script.sql`](../../database/script.sql).

## Diagrama de relaciones

```
usuarios ──1:N── objetos ──1:N── fotos
   │                │
   │                ├──N:1── categorias
   │                │
   │                ├──1:N── reportes
   │                │
   │                ├──M:N (coincidencias: perdido ↔ encontrado)
   │                │
   ├──1:N── notificaciones
   │
   └──M:N (contactos: emisor / receptor)
                     │
coincidencias ──1:N──┘
```

## 1. `usuarios`

| Campo              | Tipo          | Restricción |
| ------------------ | ------------- | ----------- |
| id                 | BIGINT        | PK, AUTO_INCREMENT |
| nombre             | VARCHAR(100)  | NOT NULL |
| apellido           | VARCHAR(100)  | NOT NULL |
| correo             | VARCHAR(150)  | NOT NULL, **UNIQUE** (`uq_usuarios_correo`) |
| password           | VARCHAR(255)  | NOT NULL (hash BCrypt, nunca texto plano) |
| rol                | VARCHAR(20)   | NOT NULL, CHECK `USUARIO` \| `ADMIN` |
| estado             | VARCHAR(20)   | NOT NULL, CHECK `ACTIVO` \| `SUSPENDIDO` |
| fecha_registro     | DATETIME      | NOT NULL, default CURRENT_TIMESTAMP |
| fecha_actualizacion| DATETIME      | NOT NULL, ON UPDATE CURRENT_TIMESTAMP |

## 2. `categorias`

| Campo          | Tipo          | Restricción |
| -------------- | ------------- | ----------- |
| id             | BIGINT        | PK, AUTO_INCREMENT |
| nombre         | VARCHAR(100)  | NOT NULL, **UNIQUE** (`uq_categorias_nombre`) |
| descripcion    | VARCHAR(255)  | NULL |
| estado         | BOOLEAN       | NOT NULL, default TRUE |
| fecha_creacion | DATETIME      | NOT NULL, default CURRENT_TIMESTAMP |

Semillas: Celular, Laptop, Mochila, Documento, Ropa, Accesorio, Llaves, Otros.

## 3. `objetos`

| Campo              | Tipo           | Restricción |
| ------------------ | -------------- | ----------- |
| id                 | BIGINT         | PK, AUTO_INCREMENT |
| usuario_id         | BIGINT         | NOT NULL, **FK → usuarios.id** (`fk_objetos_usuario`) |
| categoria_id       | BIGINT         | NOT NULL, **FK → categorias.id** (`fk_objetos_categoria`) |
| nombre             | VARCHAR(150)   | NOT NULL |
| descripcion        | TEXT           | NOT NULL |
| ubicacion          | VARCHAR(255)   | NULL |
| latitud            | DECIMAL(10,7)  | NULL, CHECK −90..90 |
| longitud           | DECIMAL(10,7)  | NULL, CHECK −180..180 |
| fecha_objeto       | DATE           | NOT NULL — **fecha en que se perdió/encontró** |
| tipo               | VARCHAR(20)    | NOT NULL, CHECK `PERDIDO` \| `ENCONTRADO` |
| estado             | VARCHAR(20)    | NOT NULL, CHECK `ACTIVO` \| `RECUPERADO` \| `OCULTO` \| `ELIMINADO` |
| fecha_publicacion  | DATETIME       | NOT NULL — **fecha en que se creó la publicación** |
| fecha_actualizacion| DATETIME       | NOT NULL, ON UPDATE CURRENT_TIMESTAMP |

> `fecha_objeto` ≠ `fecha_publicacion`: una es el hecho, otra es la publicación.

**Índices:** `idx_objetos_usuario`, `idx_objetos_categoria`, `idx_objetos_tipo`,
`idx_objetos_estado`, `idx_objetos_fecha_objeto`.

## 4. `fotos`

| Campo         | Tipo          | Restricción |
| ------------- | ------------- | ----------- |
| id            | BIGINT        | PK, AUTO_INCREMENT |
| objeto_id     | BIGINT        | NOT NULL, **FK → objetos.id** (`fk_fotos_objeto`, ON DELETE CASCADE) |
| url           | VARCHAR(500)  | NOT NULL |
| nombre_archivo| VARCHAR(255)  | NULL |
| fecha_subida  | DATETIME      | NOT NULL, default CURRENT_TIMESTAMP |

Índice: `idx_fotos_objeto`. Un objeto puede tener varias fotos.

## 5. `coincidencias`

| Campo                 | Tipo         | Restricción |
| --------------------- | ------------ | ----------- |
| id                    | BIGINT       | PK, AUTO_INCREMENT |
| objeto_perdido_id     | BIGINT       | NOT NULL, **FK → objetos.id** (`fk_coincidencias_perdido`) |
| objeto_encontrado_id  | BIGINT       | NOT NULL, **FK → objetos.id** (`fk_coincidencias_encontrado`) |
| porcentaje            | DECIMAL(5,2) | NULL, CHECK 0..100 |
| nivel                 | VARCHAR(20)  | CHECK `BAJA` \| `MEDIA` \| `ALTA` |
| estado                | VARCHAR(20)  | NOT NULL, CHECK `PENDIENTE` \| `REVISADA` \| `ACEPTADA` \| `DESCARTADA` |
| fecha_creacion        | DATETIME     | NOT NULL, default CURRENT_TIMESTAMP |

Restricciones extra:
- **`uq_coincidencias_pair` (UNIQUE)** sobre `(objeto_perdido_id, objeto_encontrado_id)` → evita duplicados.
- **`chk_coincidencias_distintos`** → los dos objetos deben ser distintos.

Índices: `idx_coincidencias_perdido`, `idx_coincidencias_encontrado`.

## 6. `contactos`

| Campo                | Tipo         | Restricción |
| -------------------- | ------------ | ----------- |
| id                   | BIGINT       | PK, AUTO_INCREMENT |
| coincidencia_id      | BIGINT       | NOT NULL, **FK → coincidencias.id** (`fk_contactos_coincidencia`) |
| usuario_emisor_id    | BIGINT       | NOT NULL, **FK → usuarios.id** (`fk_contactos_emisor`) |
| usuario_receptor_id  | BIGINT       | NOT NULL, **FK → usuarios.id** (`fk_contactos_receptor`) |
| mensaje              | TEXT         | NOT NULL |
| estado               | VARCHAR(20)  | CHECK `PENDIENTE` \| `ACEPTADO` \| `RECHAZADO` \| `CERRADO` |
| fecha_contacto       | DATETIME     | NOT NULL, default CURRENT_TIMESTAMP |

Restricción: `chk_contactos_distintos` → emisor ≠ receptor.

## 7. `notificaciones`

| Campo           | Tipo         | Restricción |
| --------------- | ------------ | ----------- |
| id              | BIGINT       | PK, AUTO_INCREMENT |
| usuario_id      | BIGINT       | NOT NULL, **FK → usuarios.id** (`fk_notificaciones_usuario`, CASCADE) |
| titulo          | VARCHAR(150) | NOT NULL |
| mensaje         | TEXT         | NOT NULL |
| tipo            | VARCHAR(50)  | NULL |
| leida           | BOOLEAN      | NOT NULL, default FALSE |
| fecha_creacion  | DATETIME     | NOT NULL, default CURRENT_TIMESTAMP |

Índice: `idx_notificaciones_usuario`.

## 8. `reportes`

| Campo             | Tipo         | Restricción |
| ----------------- | ------------ | ----------- |
| id                | BIGINT       | PK, AUTO_INCREMENT |
| usuario_id        | BIGINT       | NOT NULL, **FK → usuarios.id** (`fk_reportes_usuario`) |
| objeto_id         | BIGINT       | NOT NULL, **FK → objetos.id** (`fk_reportes_objeto`, CASCADE) |
| motivo            | VARCHAR(100) | NOT NULL |
| descripcion       | TEXT         | NULL |
| estado            | VARCHAR(20)  | CHECK `PENDIENTE` \| `REVISADO` \| `RESUELTO` \| `RECHAZADO` |
| fecha_reporte     | DATETIME     | NOT NULL, default CURRENT_TIMESTAMP |
| fecha_resolucion  | DATETIME     | NULL |

Índices: `idx_reportes_usuario`, `idx_reportes_objeto`, `idx_reportes_estado`.

## Resumen de relaciones

| # | Relación | Tipo |
|---|----------|------|
| 1 | objetos → usuarios | N:1 (`usuario_id`) |
| 2 | objetos → categorias | N:1 (`categoria_id`) |
| 3 | fotos → objetos | N:1 (`objeto_id`) |
| 4 | coincidencias → objetos (perdido) | N:1 |
| 5 | coincidencias → objetos (encontrado) | N:1 |
| 6 | contactos → coincidencias | N:1 |
| 7 | contactos → usuarios (emisor) | N:1 |
| 8 | contactos → usuarios (receptor) | N:1 |
| 9 | notificaciones → usuarios | N:1 |
| 10 | reportes → usuarios | N:1 |
| 11 | reportes → objetos | N:1 |

## Integridad

- PRIMARY KEY en las 8 tablas.
- FOREIGN KEY con nombres claros (`fk_<tabla>_<referencia>`); `RESTRICT` en
  usuarios/categorías (no borrar lo que se referencia), `CASCADE` en fotos,
  coincidencias, notificaciones y reportes.
- UNIQUE: `usuarios.correo`, `categorias.nombre`, par de objetos en `coincidencias`.
- CHECK en todos los enums de estado/rol/tipo/nivel.
- Índices en las columnas de búsqueda: `tipo`, `estado`, `fecha_objeto`,
  claves foráneas y `reportes.estado`.
