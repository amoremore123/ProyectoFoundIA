# H37 — Cerrar sesión de forma segura

> Estado de esta entrega: API/datos implementados y probados en Spring Boot y
> Django. El cierre seguro en la web y en Android queda en las capas de
> Yajaira y Amore; el contrato ya está disponible.

## Claims nuevos del JWT

Cada token emitido por cualquiera de los dos backends incluye:

| Claim | Tipo | Significado |
| --- | --- | --- |
| `jti` | string (UUID) | identificador único del token; se usa para revocarlo |
| `ver` | entero | versión de sesión de la cuenta al emitir el token |

Los tokens anteriores a H37 no tienen estos claims. Se tratan como legado:

- sin `ver` equivalen a versión `0`;
- solo se aceptan mientras `usuarios.sesion_version` sea `0`;
- si se usan para cerrar sesión, no se puede revocar solo ese token y se
  cierran todas las sesiones de la cuenta (misma respuesta que `logout-all`).

## Endpoints

### Spring Boot

| Método | Ruta | Efecto |
| --- | --- | --- |
| `POST` | `/api/auth/logout` | revoca el `jti` del token usado |
| `POST` | `/api/auth/logout-all` | incrementa `sesion_version` y cierra todos los dispositivos |

Requieren `Authorization: Bearer <token>`. Sin token: `401`.

Respuestas `200 OK`:

```json
{ "mensaje": "Sesión cerrada" }
```

```json
{ "mensaje": "Sesión cerrada en todos los dispositivos" }
```

### Django Admin

| Método | Ruta | Efecto |
| --- | --- | --- |
| `POST` | `/api/admin/auth/logout/` | revoca el `jti` del token usado |
| `POST` | `/api/admin/auth/logout-all/` | incrementa `sesion_version` y cierra todos los dispositivos |

Mismos cuerpos de respuesta y reglas de autenticación que Spring. El token
revocado deja de servir en ambos backends porque comparten el mismo esquema
MySQL y el mismo secreto JWT.

## Cómo se valida un token

En cada petición autenticada:

1. Se verifica la firma HS256, el `sub`, la expiración y el sello `cred` de H35.
2. Si existe `ver`, debe coincidir con `usuarios.sesion_version`; si no existe,
   la cuenta debe seguir en versión `0`.
3. Si existe `jti`, no debe estar en `tokens_revocados`.

Un token con `jti` revocado o con una versión anterior responde `401`. Cerrar
sesión en una cuenta no afecta a las demás cuentas.

## Base de datos

La migración idempotente es `database/migracion_h37_cierre_sesion.sql`:

```sql
ALTER TABLE usuarios ADD COLUMN sesion_version INT NOT NULL DEFAULT 0;

CREATE TABLE tokens_revocados (
    jti         VARCHAR(64) NOT NULL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    revocado_en DATETIME(6) NOT NULL,
    expira_en   DATETIME(6) NOT NULL,
    CONSTRAINT fk_token_revocado_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE CASCADE,
    INDEX idx_tokens_revocados_expira (expira_en)
);
```

`expira_en` permite borrar revocaciones vencidas; el servicio de Spring limpia
las expiradas al registrar un cierre individual. No se guarda el token, solo su
`jti`.

## Pruebas

- **Spring:** `JwtServiceTest` (claims `jti`/`ver`, versión anterior, token
  legado, versión malformada) y `CierreSesionIntegrationTest` (cierre
  individual, cierre global, otra cuenta intacta, token legado, `jti` revocado,
  `401` sin token).
- **Django:** `nucleo/test_sprint3_h37.py` cubre los mismos casos y confirma
  que el login del panel emite `jti` y `ver`.

## Tareas de otras capas

- **Web (Yajaira):** botones «Cerrar sesión» y «Cerrar en todos los
  dispositivos» con carga y error; borrar el token local tras `200`.
- **Android (Amore):** borrar el token cifrado al cerrar sesión y comprobar
  que un nuevo ingreso funciona; no se modificó código Android.
