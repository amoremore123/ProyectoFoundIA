# ENCUENTRA+ — Plataforma de Objetos Perdidos y Encontrados

Proyecto integrador: plataforma web y móvil para **reportar y buscar objetos
perdidos o encontrados**. Los usuarios publican objetos (perdidos o
encontrados), pueden buscarlos por categoría, ubicación o fecha, y el sistema
sugiere coincidencias. Un panel administrativo gestiona usuarios, categorías,
publicaciones y reportes.

> Equipo de 3 estudiantes. La base del proyecto (arquitectura, base de datos,
> configuración y API base) está lista para desarrollarse por Sprints /
> historias de usuario.

---

## 1. Tecnologías

| Capa | Tecnología |
| ---- | ---------- |
| Backend principal | Java · Spring Boot 3 · Spring Security + JWT · Spring Data JPA · Maven |
| Backend administrativo | Python · Django 5 · Django REST Framework |
| Frontend usuario | React 18 · Vite · JavaScript · React Router · Axios |
| Frontend administrador | React 18 · Vite · JavaScript · React Router · Axios |
| Móvil | Kotlin · Jetpack Compose · Navigation Compose · Retrofit |
| Base de datos | MySQL 8 |
| Contenedores | Docker · Docker Compose |

## 2. Arquitectura

```
 React Usuario ──────┐
                     ├──►  Spring Boot API  ────►  MySQL
 Android Kotlin ─────┘          :8081            objetos_perdidos_db
                                          ▲
 React Admin ───► Django Admin API ───────┘
   :5174             :8000
```

- Android **no tiene backend propio**: consume la misma API de Spring Boot.
- React Admin usa **Django**, que se conecta a la **misma** base de datos MySQL.
- Detalle completo en [`docs/arquitectura/arquitectura.md`](docs/arquitectura/arquitectura.md).

## 3. Estructura del repositorio

```
PROYECTO-INTEGRADOR/
├── backend/
│   ├── springboot/        # API principal (Java + Spring Boot)
│   └── django-admin/      # API administrativa (Python + Django)
├── frontend/
│   ├── usuario/           # App pública (React + Vite)     :5173
│   └── admin/             # Panel administrativo (React)   :5174
├── movil/
│   └── android/           # App móvil (Kotlin + Compose)
├── database/
│   ├── script.sql         # Esquema completo (8 tablas)
│   ├── seed.sql           # Datos iniciales de desarrollo
│   └── README.md
├── docs/
│   ├── arquitectura/arquitectura.md
│   ├── api/endpoints.md
│   ├── base-datos/modelo-bd.md
│   └── diseño/guia-interfaz.md
├── .gitignore
├── README.md
└── docker-compose.yml
```

## 4. Requisitos

| Herramienta | Versión |
| ----------- | ------- |
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 18+ (probado con 24) |
| Python | 3.11+ (probado con 3.13) |
| MySQL | 8 (o Docker) |
| Android Studio | última versión (para el móvil) |
| Docker + Docker Compose | opcional, para levantar todo junto |

## 5. Instalación rápida (Docker)

```bash
git clone https://github.com/amoremore123/ProyectoFoundIA.git
cd ProyectoFoundIA
docker compose up
```

Levanta MySQL (con `script.sql` + `seed.sql` automáticos), la API de Spring
Boot, la API de Django y el frontend usuario.

| Servicio | URL |
| -------- | --- |
| Frontend usuario | http://localhost:5173 |
| API Spring Boot | http://localhost:8081 |
| API Django | http://localhost:8000 |
| MySQL | localhost:3306 (`root` / `root`) |
| Mailpit (correos de prueba) | http://localhost:8025 |

Para detener: `docker compose down` (con `-v` borra también los datos).

> **Registro (H11):** al registrarte se envía un código de 6 dígitos. En
> desarrollo los correos no salen a internet: ábrelos en **Mailpit**
> (http://localhost:8025). Para enviar correos reales con Gmail, define en el
> servicio `api`: `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`,
> `MAIL_SMTP_AUTH=true`, `MAIL_STARTTLS=true`, `MAIL_USERNAME`, `MAIL_PASSWORD`
> (contraseña de aplicación) y `MAIL_FROM`.
>
> Si tu base se creó antes de este cambio, ejecuta `docker compose down -v`
> (o `database/migracion_h11_h12.sql` en MySQL local).

> El frontend admin no está en compose: ejecútalo localmente (sección 8).

## 6. Configuración de MySQL

**Opción A — Docker:** no hay nada que hacer; `docker compose up` crea la base
`objetos_perdidos_db` y ejecuta `database/script.sql` + `database/seed.sql`.

**Opción B — MySQL local:**

```bash
mysql -u root -p < database/script.sql
mysql -u root -p < database/seed.sql
```

Variables de entorno (cada proyecto tiene su `.env.example`):

```
DB_HOST=localhost
DB_PORT=3306
DB_NAME=objetos_perdidos_db
DB_USERNAME=root
DB_PASSWORD=root
JWT_SECRET=<secreto-de-al-menos-32-caracteres>
```

> ⚠️ Credenciales solo de desarrollo. Nunca subir `.env` a Git (el
> `.gitignore` ya lo excluye).

## 7. Ejecución de Spring Boot

```bash
cd backend/springboot
mvn spring-boot:run
```

Queda en `http://localhost:8081`. Requiere MySQL corriendo (sección 6).
Compilación sin ejecutar: `mvn -DskipTests compile`.

**Pruebas (H11/H12):** `mvn test`. Sin Maven instalado, con Docker:

```powershell
cd backend/springboot
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-17 mvn test
```

Casos de prueba documentados en [`docs/pruebas/casos-prueba-h11-h12.md`](docs/pruebas/casos-prueba-h11-h12.md).

## 8. Ejecución de React

Frontend usuario:

```bash
cd frontend/usuario
npm install
npm run dev        # http://localhost:5173
```

Frontend administrador:

```bash
cd frontend/admin
npm install
npm run dev        # http://localhost:5174
```

Cada uno lee su URL de API de `.env` (ver `.env.example`):
usuario → `VITE_API_URL=http://localhost:8081`,
admin → `VITE_API_URL=http://localhost:8000`.

## 9. Ejecución de Django

```bash
cd backend/django-admin
py -m pip install -r requirements.txt
py manage.py runserver        # http://localhost:8000
```

> Los modelos de negocio son `managed=False`: **no ejecutes `makemigrations`**
> para el app `nucleo`. Sus tablas las crea `database/script.sql`. Un
> `py manage.py migrate` solo crea las tablas internas de Django
> (auth/sessions) y es opcional.

## 10. Ejecución de Android

1. Abrir `movil/android/` con **Android Studio** (Generate/Import y esperar la
   sincronización de Gradle).
2. Crear/emular un dispositivo (la app usa `http://10.0.2.2:8081` para llegar
   al Spring Boot del host).
3. Run ▶.

Desde terminal (requiere SDK de Android configurado):

```bash
cd movil/android
./gradlew assembleDebug     # en Windows: gradlew.bat assembleDebug
```

## 11. Credenciales de desarrollo (seed)

| Correo | Contraseña | Rol |
| ------ | ---------- | --- |
| `admin@foundia.dev` | `Admin123!` | ADMIN |
| `ana@foundia.dev` | `Admin123!` | USUARIO |
| `luis@foundia.dev` | `Admin123!` | USUARIO |
| `carla@foundia.dev` | `Admin123!` | USUARIO |

Passwords guardados como hash **BCrypt** (nunca en texto plano). Solo para
desarrollo.

## 12. API principal

Documentación completa: [`docs/api/endpoints.md`](docs/api/endpoints.md).

Más usados:

| Método | Ruta | Descripción |
| ------ | ---- | ----------- |
| POST | `/api/auth/register` | Registro |
| POST | `/api/auth/login` | Login → JWT |
| GET | `/api/objetos` | Listar objetos (público) |
| GET | `/api/objetos/buscar?q=` | Búsqueda |
| GET | `/api/objetos/{id}` | Detalle |
| POST | `/api/objetos` | Publicar (JWT) |
| GET | `/api/objetos/{id}/coincidencias` | Coincidencias sugeridas |
| GET | `/api/perfil` | Perfil actual (JWT) |
| GET | `/api/notificaciones` | Notificaciones (JWT) |
| POST | `/api/admin/auth/login/` | Login admin (Django) |

## 13. Docker

```bash
docker compose up --build     # levanta todo
docker compose up -d          # en segundo plano
docker compose logs -f api    # logs de Spring Boot
docker compose down           # detener
docker compose down -v        # detener y borrar volúmenes (datos MySQL)
```

Contenedores: `mysql` (8.0), `api` (Spring Boot), `admin-api` (Django),
`frontend-usuario` (Vite).

## 14. Documentación

| Documento | Contenido |
| --------- | --------- |
| [`docs/arquitectura/arquitectura.md`](docs/arquitectura/arquitectura.md) | Componentes, flujos, decisiones, variables de entorno |
| [`docs/api/endpoints.md`](docs/api/endpoints.md) | Contrato de la API (request/response/errores) |
| [`docs/base-datos/modelo-bd.md`](docs/base-datos/modelo-bd.md) | Tablas, campos, relaciones, índices |
| [`docs/diseño/guia-interfaz.md`](docs/diseño/guia-interfaz.md) | Paleta, tipografía, componentes, pantallas |
| [`database/README.md`](database/README.md) | Cómo ejecutar script.sql y seed.sql |

## 15. Flujo Git recomendado

**Ramas**

```
main                    ← producción / entregas
develop                 ← integración del equipo
  feature/registro-login
  feature/publicaciones
  feature/busqueda
  feature/admin
  feature/movil
```

**Flujo**

```bash
git checkout develop              # partir siempre de develop
git checkout -b feature/publicaciones
# ... trabajo y commits pequeños ...
git push -u origin feature/publicaciones
# abrir Pull Request → develop → revisión del equipo
```

**Reglas**

1. Nunca commitear directamente a `main`.
2. Commit messages cortos e en inglés o español, pero consistentes
   (`add publicaciones endpoint`).
3. Revisar `git status` antes de commitear: no subir `node_modules/`,
   `target/`, `build/`, `.env`, `local.properties` (ya excluidos en
   `.gitignore`).
4. Hacer pull de `develop` a menudo para detectar conflictos temprano.
