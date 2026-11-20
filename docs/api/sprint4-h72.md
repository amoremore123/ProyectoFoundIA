# H72 — Ingresar con Firebase Auth (token personalizado)

Responsable del backend: Leonel (Spring). Android: Amore (`signInWithCustomToken`).
Semana 13 del curso de Programación Móvil (Google Cloud y Firebase). Prioridad «Debe» del Sprint 4.
Pendiente de confirmar con el docente que esta forma cumple lo que pide el curso (decisión 4 del reparto).

## Idea

FoundIA **no cambia su forma de iniciar sesión**: las cuentas, las contraseñas y el JWT siguen
siendo los de siempre (H11, H12, H37). Firebase Auth se usa **encima** de esa sesión:

1. La persona inicia sesión en FoundIA como hoy y recibe su JWT.
2. La app pide a Spring un **token personalizado** de Firebase con ese JWT.
3. La app entra a Firebase con `signInWithCustomToken`.

Así la app queda identificada en Firebase (Analytics, Crashlytics y, si se usa, Firestore) con la
misma cuenta de FoundIA, sin crear cuentas nuevas ni pedir otra contraseña.

**Ninguna ruta de FoundIA acepta tokens de Firebase.** La única credencial para la API sigue siendo
el JWT de FoundIA. Si más adelante alguna ruta los aceptara, tendría que verificarlos con el SDK de
administración y resolver la cuenta interna por su UID; hoy no hace falta.

## `POST /api/auth/firebase-token` (Spring)

Requiere sesión (JWT de FoundIA). No recibe cuerpo.

> **Requisito de implementación:** en `SecurityConfig` todas las rutas `POST /api/auth/**` son
> públicas salvo `logout` y `logout-all`. Esta ruta **debe agregarse a la lista de rutas
> autenticadas**, antes de la regla `permitAll`.

Respuesta `200`:

```json
{ "token": "<token personalizado de Firebase>", "uid": "foundia-42", "expiraEn": "2026-11-20T16:20:00Z" }
```

| Campo | Regla |
| --- | --- |
| `uid` | `foundia-<id interno>`. **Estable:** siempre el mismo para la misma cuenta. No contiene correo, nombre ni teléfono |
| `token` | Token personalizado firmado con la cuenta de servicio. Firebase lo acepta durante **1 hora**; solo sirve para entrar, no para la API de FoundIA |
| `expiraEn` | Hasta cuándo se puede usar `token` con `signInWithCustomToken` (UTC) |

- El token **no lleva datos personales** como *claims*. Como mucho, el rol (`rol: "USUARIO"`,
  `"BIBLIOTECA"` o `"ADMIN"`), sin correo ni nombre.
- Cada llamada genera un token nuevo, pero el `uid` es siempre el mismo.

| Código | Caso |
| --- | --- |
| `200` | Token emitido |
| `401` | Sin JWT, JWT vencido, JWT revocado (H37), cuenta **suspendida** o sin verificar (el filtro JWT ya los rechaza) |
| `503` | Firebase no está configurado en el servidor (falta la cuenta de servicio). La app sigue con su sesión normal |

Una cuenta **no puede pedir el token de otra**: la ruta no recibe ningún id, usa solo el del JWT.

## Cierre de sesión y suspensión

Firebase revoca las sesiones **por cuenta**, no por teléfono. Por eso:

| Evento | En Firebase |
| --- | --- |
| Cerrar sesión en un teléfono (`POST /api/auth/logout`) | Android hace `FirebaseAuth.signOut()` en ese teléfono. **El servidor no revoca en Firebase**: si lo hiciera, cerraría también la sesión de Firebase de los otros teléfonos de la cuenta, y H37 exige que este cierre afecte solo a esa sesión |
| Cerrar en todos los dispositivos (`POST /api/auth/logout-all` y `/api/admin/auth/logout-all/`) | El servidor llama a `revokeRefreshTokens(uid)`: todas las sesiones de Firebase de la cuenta dejan de renovarse |
| Suspender la cuenta desde el panel (Django, `PATCH /api/admin/usuarios/{id}/ {"estado": "SUSPENDIDO"}`) | Igual que «todos los dispositivos». Como la suspensión ocurre en Django, el revocado lo hace Django con la misma cuenta de servicio, o Spring al detectarla; se decide al implementar |

> El plan del Sprint 4 dice «revocar el token de Firebase al cerrar sesión». Este contrato lo aplica
> al cierre en todos los dispositivos y a la suspensión, por la razón de la tabla. **Confirmar con
> el equipo.**

Si Firebase no está configurado, cerrar sesión funciona igual que hoy; solo se omite el revocado.

## Lo que hace Android (Amore)

1. Después de iniciar sesión en FoundIA, llamar a `POST /api/auth/firebase-token` y entrar con
   `signInWithCustomToken(token)`.
2. Si la ruta responde `503` o no hay red, **seguir con la sesión normal de FoundIA** sin mostrar
   error: Firebase es un complemento.
3. Al abrir la app con una sesión guardada: si el JWT sigue válido y Firebase no tiene usuario,
   pedir un token nuevo; si el JWT venció (`401`), cerrar las dos sesiones.
4. Al cerrar sesión: `FirebaseAuth.signOut()`, borrar el JWT cifrado y el token de push (H57).
5. Comprobar que el `uid` de Firebase coincide con `foundia-<id>` de la cuenta.

## Configuración

Usa la **misma cuenta de servicio de H57**, solo por variables de entorno (nunca en Git):

| Variable | Uso |
| --- | --- |
| `FIREBASE_CREDENCIALES_JSON` | Contenido del JSON de la cuenta de servicio |
| `FIREBASE_PROYECTO_ID` | Id del proyecto de Firebase |

En la consola de Firebase hay que activar **Authentication**. El token personalizado no necesita
activar ningún proveedor de inicio de sesión.

## Pruebas que se deben ejecutar y registrar

- Sin JWT, con JWT vencido, con JWT revocado por `logout` y con la cuenta suspendida: `401`, sin token.
- Dos llamadas seguidas de la misma cuenta: tokens distintos, **mismo `uid`**.
- Dos cuentas distintas: `uid` distintos; ninguna obtiene el de la otra.
- El token decodificado no contiene correo, nombre ni teléfono.
- Sin cuenta de servicio: `503` y el inicio de sesión normal sigue funcionando (no `500`).
- `logout-all` y suspensión: se llama a `revokeRefreshTokens` con el `uid` correcto (prueba con el
  SDK simulado solo para esta llamada; la prueba real, con la cuenta de servicio del equipo).
- Android: token vencido, otra cuenta, sin red y app sin Firebase configurado.
