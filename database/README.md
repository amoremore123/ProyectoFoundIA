# Base de datos

Base de datos principal de la plataforma: **`objetos_perdidos_db`** (MySQL 8).

## Archivos

| Archivo        | Descripción                                                        |
| -------------- | ------------------------------------------------------------------ |
| `script.sql`   | Crea la base de datos, las 8 tablas, claves foráneas, índices y check constraints. |
| `seed.sql`     | Datos iniciales de desarrollo: categorías, usuarios de prueba y objetos de prueba. |
| `migracion_h20_reclamos.sql` | Agrega la tabla de reclamos a bases ya existentes (H20). |

## Cómo ejecutarlas

### Opción 1 — MySQL Workbench / cliente gráfico

1. Abrir `script.sql` y ejecutar todo el archivo.
2. Abrir `seed.sql` y ejecutar todo el archivo.

### Opción 2 — Terminal

```bash
mysql --default-character-set=utf8mb4 -u root -p < script.sql
mysql --default-character-set=utf8mb4 -u root -p < seed.sql
```

Ambos archivos declaran también `SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci`
para fijar explícitamente el charset de la sesión que ejecuta la importación.
El esquema usa `utf8mb4`/`utf8mb4_unicode_ci`; Django fija `charset=utf8mb4` en
su conexión. La columna `objetos.descripcion` es `TEXT` (65.535 bytes). El
formulario y `POST /api/objetos` limitan la descripción a 16.000 unidades UTF-16:
su peor caso (16.000 caracteres BMP de 3 bytes) ocupa 48.000 bytes; los
caracteres suplementarios, como emojis, consumen 4 bytes por par sustituto.

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
9. `reclamos`

Para una base existente, ejecutar `migracion_h20_reclamos.sql` para añadir la
tabla de reclamos sin recrear el resto del esquema.

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
