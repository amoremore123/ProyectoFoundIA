# H35 — Recuperar contraseña: casos de prueba (Yajaira)

Contrato: [recuperación de contraseña](../api/recuperacion-password-sprint2.md).
Pantallas web: `RecuperarPassword.jsx` y `RestablecerPassword.jsx`.

## API (Spring Boot)

`RecuperarPasswordIntegrationTest` (H2 temporal, correo simulado) comprueba:

| N.º | Caso | Resultado esperado |
| --- | --- | --- |
| 35-1 | Correo existente e inexistente | Misma respuesta; solo la cuenta real recibe correo |
| 35-2 | Flujo completo | Cambia la contraseña, exige nuevo login y el enlace no se reutiliza |
| 35-3 | Sesión abierta antes del cambio | El JWT anterior responde 401 |
| 35-4 | Enlace vencido, inventado o reemplazado por uno nuevo | 400 «El enlace no es válido o ya venció» |
| 35-5 | Dos pedidos seguidos | No se envía otro correo durante los 60 s de espera |
| 35-6 | Contraseña débil | 400 y el enlace sigue sirviendo |
| 35-7 | Cuenta suspendida | No se reactiva ni cambia la contraseña |
| 35-8 | Falla el envío del correo | Misma respuesta y el enlace queda inutilizable |

```powershell
# backend/springboot, Java 17 y Maven
mvn test "-Dtest=RecuperarPasswordIntegrationTest"
```

## Web (Vitest)

| N.º | Pantalla | Caso |
| --- | --- | --- |
| 35-W1 | Recuperar | Envía el correo normalizado, muestra el mensaje neutro y espera 60 s |
| 35-W2 | Recuperar | No llama a la API con correo vacío o inválido |
| 35-W3 | Recuperar | Completa el correo que venía escrito en el login |
| 35-W4 | Recuperar | Sin conexión avisa y no muestra el mensaje de éxito |
| 35-W5 | Restablecer | Sin token válido muestra el aviso y ofrece pedir otro enlace |
| 35-W6 | Restablecer | Quita el token de la barra de direcciones |
| 35-W7 | Restablecer | No envía contraseña débil ni confirmación distinta |
| 35-W8 | Restablecer | Guarda, cierra la sesión del navegador y vuelve al login con aviso |
| 35-W9 | Restablecer | Enlace vencido o usado: lo informa y bloquea el formulario |
| 35-W10 | Login | Lleva a la recuperación con el correo ya escrito |

```powershell
# frontend/usuario
npx vitest run src/pages/RecuperarPassword.test.jsx src/pages/RestablecerPassword.test.jsx src/pages/Login.test.jsx
```

## Resultado (30/10/2026, rama dev/yajaira 4d50a5c)

- Web usuario: **206 pruebas correctas** en 29 archivos (incluye las de H35) y
  `vite build` correcto.
- Spring: `mvn verify` con **411 pruebas correctas**, 0 fallos, 0 errores y 0
  omitidas (incluye los 8 casos de `RecuperarPasswordIntegrationTest`); `BUILD SUCCESS`
  el 30/10/2026 a las 16:58, con Java 21 y Maven 3.9.16.

## Pendiente

1. **Prueba manual con API real y Mailpit** (`docker compose up`, correo en
   http://localhost:8025): pedir el enlace, abrirlo, cambiar la contraseña, volver
   a usar el mismo enlace (debe fallar) y entrar con la nueva. Guardar capturas en
   `docs/pruebas/evidencias-yajaira-sprint2/`.
2. **Android (Amore):** probar desde el APK un enlace vencido, uno repetido y el
   nuevo login. Hasta hoy la pantalla Android se probó sin esta API.
3. **Django:** el panel ADMIN todavía acepta un token anterior al cambio de
   contraseña (ver la limitación en el contrato). Pasa al Sprint 3.
