# Arquitectura

## Visión general

La plataforma **ENCUENTRA+** (Plataforma de Objetos Perdidos y Encontrados) es
un sistema web + móvil con **un solo backend principal** (Spring Boot) y
**una sola base de datos** (MySQL).

```
 React Usuario ──────┐
                     ├──►  Spring Boot API  ────►  MySQL
 Android Kotlin ─────┘          (puerto 8081)   (objetos_perdidos_db)
                                          ▲
 React Admin ───► Django Admin API ───────┘
   (5174)           (puerto 8000)
```

## Componentes

| Componente            | Tecnología             | Puerto | Responsabilidad |
| --------------------- | ---------------------- | ------ | --------------- |
| `backend/springboot`  | Java · Spring Boot 3   | 8081   | API principal: auth, objetos, búsqueda, coincidencias, perfil, notificaciones. Único backend de la app pública. |
| `backend/django-admin`| Python · Django + DRF  | 8000   | API administrativa para el panel React Admin: gestión de usuarios, categorías, publicaciones y reportes. |
| `frontend/usuario`    | React + Vite           | 5173   | Aplicación pública: buscar, publicar, detalle, perfil, notificaciones. |
| `frontend/admin`      | React + Vite           | 5174   | Panel administrativo (login, dashboard, usuarios, publicaciones, reportes, categorías). |
| `movil/android`       | Kotlin · Jetpack Compose | —    | App móvil. **No tiene backend propio**: consume la misma API de Spring Boot. |
| `database`            | MySQL 8                | 3306   | Base de datos principal `objetos_perdidos_db`. |

## Reglas de la arquitectura

1. **Spring Boot es el backend principal.** React Usuario y Android consumen
   la misma API (`/api/...`).
2. **Android NO tiene backend propio.** Solo llama a Spring Boot (en el
   emulador: `http://10.0.2.2:8081`).
3. **React Admin no habla con Spring Boot directamente**: su backend es
   Django, que se conecta a la **misma base de datos MySQL**.
4. **MySQL es la única fuente de verdad.** El esquema lo crea
   `database/script.sql`; Spring Boot usa `ddl-auto: none` (no genera tablas)
   y los modelos de Django son `managed = False` (no crean tablas).
5. **Autenticación:** JWT firmado con HS256. Spring Boot lo emite para
   usuarios normales; Django emite su propio JWT (mismo secreto de desarrollo)
   para administradores. El password nunca sale en ninguna respuesta JSON.

## Flujo principal (usuario)

1. El usuario se registra o inicia sesión → `POST /api/auth/register|login` →
   recibe un `token`.
2. Envía `Authorization: Bearer <token>` en las rutas protegidas
   (publicar, editar, eliminar, perfil, notificaciones).
3. La búsqueda y el detalle de objetos son **públicos** (no requieren token).

## Flujo administrativo

1. El admin inicia sesión en React Admin → `POST /api/admin/auth/login/`
   (Django valida contra la tabla `usuarios` con BCrypt y exige rol `ADMIN`).
2. React Admin llama a `/api/admin/...` con su token.
3. Django opera sobre las **mismas tablas** de MySQL (usuarios, categorías,
   objetos/publicaciones, reportes).

## Responsabilidades Django vs Spring Boot

| Tema                   | Spring Boot | Django |
| ---------------------- | ----------- | ------ |
| Auth de usuarios públicos | ✅ (`/api/auth`) | — |
| Auth de administradores | —           | ✅ (`/api/admin/auth`) |
| objetos / fotos / búsqueda | ✅       | solo lectura/gestión (`/api/admin/publicaciones`) |
| categorías             | lectura (`/api/categorias`) | CRUD admin |
| usuarios               | registro/perfil | gestión admin (suspender/activar) |
| reportes               | —            | ✅ CRUD admin |
| notificaciones         | ✅           | — |
| coincidencias          | ✅ (cálculo simple) | — |

> Django **no duplica** la lógica de negocio: administra y consulta. El
> algoritmo de coincidencias y el registro de objetos viven en Spring Boot.

## Decisiones técnicas

- **`ddl-auto: none`** en Spring Boot: el esquema lo gobierna `script.sql`.
- **Modelos Django `managed=False`**: Django no crea ni altera las tablas de
  negocio (solo crea sus tablas internas de `auth`/`sessions` al hacer migrate).
- **Borrado lógico** de objetos (`estado = ELIMINADO`) para conservar
  historial e integridad referencial.
- **Cálculo de coincidencias simple** (misma categoría + tipo opuesto) como
  base para el algoritmo avanzado de un Sprint posterior (IA).

## Variables de entorno

| Variable              | Proyectos           | Descripción |
| --------------------- | ------------------- | ----------- |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Spring Boot, Django | Conexión MySQL |
| `JWT_SECRET`          | Spring Boot, Django | Secreto HS256 (¡cambiar en producción!) |
| `JWT_EXPIRATION_MS`   | Spring Boot         | Vigencia del token |
| `CORS_ALLOWED_ORIGINS`| Spring Boot         | Orígenes permitidos |
| `DJANGO_SECRET_KEY`   | Django              | Secreto de Django |
| `DJANGO_DEBUG`        | Django              | Modo debug |
| `VITE_API_URL`        | React usuario/admin | URL base de su API |

Cada proyecto incluye su `.env.example`.
