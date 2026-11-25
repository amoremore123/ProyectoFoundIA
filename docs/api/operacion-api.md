# Operación de la API: Swagger, estado, caché, secretos y hora

Responsable: Yajaira · Rama: `frontend/web` · 25/11/2026. Extras para Desarrollo de Aplicaciones Web.

## Swagger / OpenAPI (Spring)

- **http://localhost:8081/swagger-ui.html**: la API completa para recorrerla en vivo (61 rutas).
- **http://localhost:8081/v3/api-docs**: el contrato OpenAPI en JSON.
- Para probar rutas con sesión: `POST /api/auth/login`, copiar `token` y pegarlo en **Authorize**.
- Públicos (solo lectura). Dependencia `springdoc-openapi-starter-webmvc-ui` 2.8.17; configuración en
  `config/OpenApiConfig.java`.

## Estado del servicio (Spring Actuator)

- **http://localhost:8081/actuator/health** → `{"status":"UP","components":{"db":{"status":"UP"}, ...}}`.
- Solo se publica `health`, con el estado de cada parte y **sin detalles** (ni nombre de la base ni rutas).
  `/actuator/env`, `/actuator/beans` y el resto no existen.
- El correo no cuenta para el estado: si el SMTP cae, la API sigue funcionando.

## Caché de categorías

- `GET /api/categorias` se guarda en memoria (Caffeine) por **60 segundos**: la piden casi todas las
  pantallas de la web y de la app.
- Vence sola porque las categorías las edita el panel (Django) y Spring no se entera: un cambio del panel
  se ve en la web, como mucho, un minuto después.
- En las pruebas la caché está apagada (`spring.cache.type=none` en Surefire); `OperacionApiIntegrationTest`
  la enciende para comprobarla. Se puede apagar en Docker con `CACHE_TYPE=none`.

## Secretos fuera de Git

`docker-compose.yml` ya no trae el secreto JWT ni la clave de Django: los toma del `.env` (ignorado por Git).
Si faltan, `docker compose` se detiene con un mensaje claro.

```
JWT_SECRET=...          # el mismo para Spring y Django: una sesión sirve en las dos
DJANGO_SECRET_KEY=...
```

Para generar uno: `python -c "import secrets; print(secrets.token_urlsafe(48))"`. Al cambiar el
`JWT_SECRET` las sesiones abiertas dejan de valer: hay que volver a iniciar sesión.

> El valor de `application.yml` y de `authentication.py` queda solo como respaldo para las pruebas y para
> correr Spring sin Docker. En un despliegue real siempre se define `JWT_SECRET`.

## Hora de Lima en Django

`TIME_ZONE = "America/Lima"` (se puede cambiar con `DJANGO_TIME_ZONE`). La base sigue guardando en UTC
(`USE_TZ = True`); las etiquetas, constancias y reportes convierten con `timezone.localtime`, así que
ahora muestran la hora de Lima. MySQL tiene cargadas sus tablas de zonas horarias (la imagen oficial las
trae), por eso las métricas por mes funcionan con la zona de Lima.
