# Swagger/OpenAPI de la API de Spring

Pedido por el equipo el 23/11/2026 para mostrar la API en vivo (por ejemplo, en la presentación).

## Cómo encenderlo

Está **apagado por defecto**: en producción no se publica el mapa completo de la API.

```bash
# Docker Compose (la API de Spring)
SWAGGER_HABILITADO=true docker compose up -d api
```

En PowerShell: `$env:SWAGGER_HABILITADO="true"; docker compose up -d api`. Para apagarlo, volver a levantar la API
sin la variable.

Con la API corriendo en el puerto 8081:

| Qué | Dirección |
| --- | --- |
| Pantalla de Swagger | <http://localhost:8081/swagger-ui.html> |
| Contrato OpenAPI (JSON) | <http://localhost:8081/v3/api-docs> |

## Cómo probar una ruta con sesión

1. En Swagger, `POST /api/auth/login` → «Try it out» → correo y contraseña → copiar el `token` de la respuesta.
2. Botón **«Authorize»** → pegar el token (sin la palabra `Bearer`) → «Authorize».
3. Las rutas protegidas ya responden con esa sesión.

Abrir Swagger **no abre la API**: las rutas protegidas siguen pidiendo sesión.

## Qué cubre

Solo la API de **Spring** (la de la web y la app). El panel de Django tiene su propia API (`/api/admin/…`) y
no está en este Swagger. Los contratos detallados de cada historia siguen en `docs/api/`.

## Detalles

- Librería: `springdoc-openapi-starter-webmvc-ui` 2.8.9 (con Swagger UI 5.21), compatible con Spring Boot 3.5.
- Configuración: `springdoc` en `application.yml` (variable `SWAGGER_HABILITADO`) y `config/OpenApiConfig.java`
  (título, descripción y el botón de token JWT).

## Verificación del 23/11/2026

- Spring: `SwaggerIntegrationTest` (3 pruebas: publica el contrato sin sesión con las rutas y el esquema JWT, la
  pantalla abre, y las rutas protegidas siguen en `401`) y `SwaggerApagadoIntegrationTest` (sin la variable:
  `404`). Suite completa: **605 pruebas, 0 fallos**.
- Docker real (QA): sin la variable, `/v3/api-docs` y `/swagger-ui/index.html` → `404`; con
  `SWAGGER_HABILITADO=true` → `200`, 55 rutas documentadas, botón JWT, y `/api/perfil` sin sesión sigue en `401`.
  La pantalla carga en el navegador con el título de FoundIA y el botón «Authorize».
