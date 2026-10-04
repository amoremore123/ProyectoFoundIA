# Base de datos

Base de datos principal de la plataforma: **`objetos_perdidos_db`** (MySQL 8).

## Archivos

| Archivo        | Descripción                                                        |
| -------------- | ------------------------------------------------------------------ |
| `script.sql`   | Crea la base de datos, las 8 tablas, claves foráneas, índices y check constraints. |
| `seed.sql`     | Datos iniciales de desarrollo: categorías, usuarios de prueba y objetos de prueba. |

## Cómo ejecutarlas

### Opción 1 — MySQL Workbench / cliente gráfico

1. Abrir `script.sql` y ejecutar todo el archivo.
2. Abrir `seed.sql` y ejecutar todo el archivo.

### Opción 2 — Terminal

```bash
mysql -u root -p < script.sql
mysql -u root -p < seed.sql
```

### Opción 3 — Docker

Si usas `docker compose up` desde la raíz del proyecto, ambas cargan
automáticamente al levantar el contenedor de MySQL.

> `seed.sql` es re-ejecutable: limpia los datos de negocio antes de insertar.
> `script.sql` también puede re-ejecutarse (elimina y recrea las tablas).

## Tablas

1. `usuarios`
2. `categorias`
3. `objetos`
4. `fotos`
5. `coincidencias`
6. `contactos`
7. `notificaciones`
8. `reportes`

Detalle completo en [`docs/base-datos/modelo-bd.md`](../docs/base-datos/modelo-bd.md).

## Credenciales de desarrollo (seed)

> Solo para desarrollo. Nunca usar en producción.

| Correo              | Contraseña   | Rol      |
| ------------------- | ------------ | -------- |
| `admin@foundia.dev` | `Admin123!`  | ADMIN    |
| `ana@foundia.dev`   | `Admin123!`  | USUARIO  |
| `luis@foundia.dev`  | `Admin123!`  | USUARIO  |
| `carla@foundia.dev` | `Admin123!`  | USUARIO  |

Las contraseñas se guardan como hash **BCrypt** (formato `$2a$10$...`),
compatibles con Spring Security. Nunca se almacenan en texto plano.
