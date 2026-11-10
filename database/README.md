# Base de datos

Base de datos principal de la plataforma: **`objetos_perdidos_db`** (MySQL 8).

**Pruebas locales:** cada integrante tiene su propia base y datos sintéticos en
su PC; no necesita importar el SQL de otra persona. Spring y Django comparten
solo la base de esa instalación. Ver el
[acuerdo de datos locales](../docs/integracion/datos-locales-pruebas-sprint2.md).

Autenticación compartida: Spring y Django utilizan `usuarios.intentos_fallidos`
y `usuarios.bloqueado_hasta`. Los códigos y bloqueos nuevos se guardan en UTC;
no hace falta crear una columna adicional para el contador del panel.

## Archivos

| Archivo        | Descripción                                                        |
| -------------- | ------------------------------------------------------------------ |
| `script.sql`   | Instalación nueva: crea la base y sus 20 tablas, claves foráneas, índices y restricciones. Elimina tablas anteriores si se reimporta. |
| `seed.sql`     | Datos iniciales de desarrollo: categorías, usuarios de prueba y objetos de prueba. |
| `migracion_h20_reclamos.sql` | Agrega la tabla de reclamos a bases ya existentes (H20). |
| `migracion_h37_cierre_sesion.sql` | Agrega `usuarios.sesion_version` y `tokens_revocados` (H37). |
| `migracion_h38_respaldos.sql` | Agrega la tabla `respaldos` para el historial de respaldos (H38). |
| `migracion_h53_recordatorios.sql` | Agrega `preferencias_notificacion.recordatorios` y `recordatorios_objeto` (H53). |

## Cómo ejecutarlas

Las opciones siguientes son **solo para una instalación nueva y vacía**. Para
conservar una base existente, respaldar esa base local y aplicar únicamente las
migraciones que falten; nunca reimportar el esquema ni los datos iniciales.

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

Si usas `docker compose up` desde la raíz del proyecto, ambos archivos se cargan
automáticamente **solo al inicializar un volumen MySQL nuevo y vacío**. Un
volumen existente no se actualiza automáticamente con esos archivos.

> **Destructivos sobre una base existente:** `seed.sql` limpia datos de negocio
> y `script.sql` elimina y recrea tablas. No usarlos como actualización ni borrar
> el volumen con `docker compose down -v`.

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
10. `secuencias_custodia`
11. `custodias`
12. `eventos_coincidencia`
13. `preferencias_notificacion`
14. `avisos_coincidencia`
15. `recordatorios_objeto`
16. `cambios_rol`
17. `solicitudes_eliminacion`
18. `tokens_recuperacion_password`
19. `tokens_revocados`
20. `respaldos`

Para una base existente, comparar su esquema antes de elegir las migraciones.
Además de H20, el Sprint 2 incluye cambios H22, H23, H24, H28, H33, H34 y H35;
el Sprint 3 incluye H36, H37, H38, H41, H43 y H53.
Aplicar solo los archivos correspondientes a cambios ausentes, sin recrear
tablas ni rellenar consentimientos o recuperaciones históricos.

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
