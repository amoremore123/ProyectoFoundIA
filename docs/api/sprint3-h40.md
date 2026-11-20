# H40 — Invitar a amigos a la plataforma

Responsable de la API: Amore. Web: Yajaira (`Perfil.jsx`). Android: Amore (`PerfilScreen`).

## `POST /api/perfil/invitaciones`

Requiere sesión (JWT). No recibe cuerpo.

```json
{ "url": "http://localhost:5173/registro?invitacion=<codigo>" }
```

- El código tiene 32 caracteres URL-safe (24 bytes aleatorios en Base64 sin relleno).
- En `invitaciones` solo se guarda su huella SHA-256 (`codigo_hash`) y el `usuario_id`.
  El enlace no lleva el id de quien invita, ni su correo, ni la sesión.
- La base del enlace sale de `app.web.url` (`WEB_URL`).

| Código | Caso |
| --- | --- |
| `200` | Enlace creado |
| `401` | Sin token o token revocado |

## `POST /api/auth/register?invitacion=<codigo>`

El registro de H11 acepta el parámetro opcional `invitacion`. Si viene, suma un uso
en `invitaciones.usos` dentro de la misma transacción que crea la cuenta.
El resto del flujo (código de verificación por correo) no cambia.

| Código | Caso |
| --- | --- |
| `201` | Cuenta creada y uso registrado |
| `400` | Invitación con formato inválido o inexistente: «La invitación no es válida. Puedes registrarte sin el enlace.» No se crea la cuenta |

Quien se registra nunca ve quién lo invitó.

## Base de datos

`database/migracion_h40_invitaciones.sql` (idempotente; también incluida en `script.sql`).
Aplicar con respaldo previo sobre una base existente.

## Verificación

Ver [entrega de Amore](../integracion/entrega-amore-sprint3.md) y
[custodia-avisos-invitacion.json](../pruebas/sprint3-amore/custodia-avisos-invitacion.json).
