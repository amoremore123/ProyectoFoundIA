# Casos de prueba — H11 Registrarse y H12 Iniciar sesión

**Responsable:** Yajaira Cerron Mancilla · **Enfoque:** Registro, Login, Seguridad

## 1. Pruebas automáticas (backend Spring Boot)

Ubicación: `backend/springboot/src/test/java/com/proyectointegrador/`

| Clase | Tipo | Qué cubre |
| ----- | ---- | --------- |
| `service/AuthServiceTest` | Unitaria (JUnit 5 + Mockito) | Lógica de registro, verificación, login y bloqueo |
| `dto/RegisterRequestValidationTest` | Unitaria (Bean Validation) | Reglas del formulario y de la contraseña |
| `controller/AuthControllerTest` | Endpoints (MockMvc) | Códigos HTTP y mensajes de `/api/auth/*` |
| `security/JwtServiceTest` | Unitaria | Generación y validación del token JWT |

### Cómo ejecutarlas (con Docker, sin instalar Maven)

```powershell
cd C:\Users\Usuario\Desktop\ProyectoFoundIA-main\backend\springboot
docker run --rm -v "${PWD}:/app" -w /app maven:3.9-eclipse-temurin-17 mvn test
```

Con Maven instalado: `mvn test`. Resultado esperado: `BUILD SUCCESS`, 0 fallos.

## 2. Casos de prueba — H11 Registrarse

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H11-01 | Registro válido | Ana / Pérez / ana@correo.com / `Segura123!` | 201, cuenta creada sin verificar, código enviado, contraseña guardada con BCrypt | `registroValido`, `AuthControllerTest.registroValido` |
| CP-H11-02 | Correo ya registrado | correo de un usuario existente | 409 "El correo ya está registrado", no se guarda nada | `correoDuplicado`, `registroDuplicado` |
| CP-H11-03 | Contraseña débil | `123456`, `Corta1!`, `sinmayuscula1!`, `SinNumero!!`, `SinSimbolo123` | 400, el formulario marca los requisitos faltantes | `passwordDebil`, `registroPasswordDebil` |
| CP-H11-04 | Contraseñas no coinciden | confirmar ≠ contraseña | El frontend muestra "Las contraseñas no coinciden" y no envía | Manual |
| CP-H11-05 | Correo inválido | `ana-sin-arroba` | 400 / mensaje "Ingresa un correo válido" | `correoInvalido` |
| CP-H11-06 | Nombre con números o vacío | `An4`, `""` | 400 "solo puede contener letras" / "es obligatorio" | `nombreYApellido` |
| CP-H11-07 | Correo con mayúsculas | `Ana@FoundIA.dev` | Se guarda como `ana@foundia.dev` | `registroValido` |
| CP-H11-08 | Código correcto | código recibido en Mailpit | 200, cuenta verificada, inicia sesión (JWT) | `verificarCodigoCorrecto`, `verificar` |
| CP-H11-09 | Código incorrecto | `000000` | 400 "Código incorrecto." | `verificarCodigoIncorrecto` |
| CP-H11-10 | Código vencido (> 15 min) | código antiguo | 400 "El código expiró. Solicita uno nuevo." | `verificarCodigoExpirado` |
| CP-H11-11 | Reenviar código | botón "Reenviar código" | Llega un código nuevo; el anterior deja de servir | `reenviarCodigo` |
| CP-H11-12 | Servidor de correo caído | Mailpit apagado | 503, la cuenta NO se crea | `falloEnvioCorreo` |

## 3. Casos de prueba — H12 Iniciar sesión

| ID | Escenario | Datos de entrada | Resultado esperado | Prueba automática |
| -- | --------- | ---------------- | ------------------ | ----------------- |
| CP-H12-01 | Login correcto | ana@foundia.dev / `Admin123!` | 200, token JWT, entra al inicio | `loginCorrecto`, `AuthControllerTest.loginCorrecto` |
| CP-H12-02 | Contraseña incorrecta | contraseña errada | 401 "Correo o contraseña incorrectos. Te quedan 4 intentos." | `passwordIncorrecto`, `loginIncorrecto` |
| CP-H12-03 | Correo no registrado | nadie@correo.com | 401 "Correo o contraseña incorrectos." | `correoInexistente` |
| CP-H12-04 | 5 intentos fallidos | 5 contraseñas erradas seguidas | Al 5.º: 423 y cuenta bloqueada 15 min | `quintoIntentoBloquea`, `cincoIntentosSeguidos` |
| CP-H12-05 | Login durante el bloqueo | contraseña correcta | 423 "Intenta de nuevo en N minutos" | `bloqueadaRechazaPasswordCorrecto`, `loginBloqueado` |
| CP-H12-06 | Login después del bloqueo | contraseña correcta tras 15 min | 200 y se limpia el bloqueo | `bloqueoVencido` |
| CP-H12-07 | Login correcto reinicia contador | 3 fallos + 1 correcto | Contador vuelve a 0 | `loginCorrecto` |
| CP-H12-08 | Cuenta sin verificar | usuario recién registrado | 403, botón "Enviar código y verificar" | `cuentaNoVerificada`, `loginNoVerificado` |
| CP-H12-09 | Cuenta suspendida | usuario SUSPENDIDO | 401 "Tu cuenta está suspendida…" | `cuentaSuspendida` |
| CP-H12-10 | Campos vacíos | sin correo | 400 / mensaje en el formulario | `loginSinCorreo` |
| CP-H12-11 | Token válido | JWT generado | Contiene el correo y solo es válido para su dueño | `JwtServiceTest.tokenValido` |
| CP-H12-12 | Token alterado o vencido | otro secreto / expirado | Rechazado | `otroSecreto`, `tokenExpirado` |

## 4. Prueba manual de punta a punta

1. `docker compose down -v` y luego `docker compose up --build` (recrea la BD con las columnas nuevas).
2. Abrir http://localhost:5173/registro y registrarse con un correo cualquiera.
3. Abrir **Mailpit** en http://localhost:8025, copiar el código de 6 dígitos.
4. Ingresarlo en la pantalla "Verifica tu correo" → entra al inicio.
5. Cerrar sesión e intentar 5 veces con una contraseña errada → mensaje de bloqueo 🔒.
6. Ver en MySQL: `SELECT correo, verificado, intentos_fallidos, bloqueado_hasta FROM usuarios;`
