# Guía de prueba en un teléfono físico (Sprint 3, Amore)

Esta guía cubre lo que el emulador no puede certificar: H44, H46, H51, H52
con cámara real y la entrega natural de H50. Las pruebas usan las APIs de QA
de la laptop (proyecto Docker `foundia-amore-s3`, con su propia MySQL).

## Cómo se conecta el teléfono

El teléfono no usa la red Wi-Fi. Se conecta por USB con `adb reverse`, así
que dentro del teléfono `127.0.0.1:18281` (Spring), `127.0.0.1:18200`
(Django) y `127.0.0.1:18273` (web) llegan a la laptop por el cable. En la
red compartida no se abre ningún puerto ni hace falta tocar el firewall.

Docker publica esos puertos en `127.0.0.1` y en `[::1]`. `adb reverse` se
conecta por IPv6 (`::1`), así que sin esa segunda publicación la conexión se
abre pero no llega ninguna respuesta.

1. En el teléfono, activa **Opciones de desarrollador → Depuración USB**.
2. Conecta el cable y acepta el aviso «¿Permitir depuración USB?».
3. Con Docker QA levantado, ejecuta en la raíz del repositorio:

   ```powershell
   powershell -ExecutionPolicy Bypass -File movil\android\qa\telefono\conectar-telefono.ps1 -Apk output\android\FoundIA-Sprint3-Amore-telefono-usb.apk
   ```

   El script instala el APK, crea los tres túneles y comprueba que la laptop
   responda. Si desconectas el cable, vuelve a ejecutarlo sin `-Apk`.

Para el APK del teléfono hay que compilar con
`-PfoundiaApiUrl=http://127.0.0.1:18281/`,
`-PfoundiaAdminApiUrl=http://127.0.0.1:18200/` y
`-PfoundiaWebUrl=http://127.0.0.1:18273/`. El APK del emulador sigue usando
`10.0.2.2`.

## Cuentas de QA

Usa las cuentas del seed. Las contraseñas están en `database/seed.sql` y no
se repiten aquí.

| Cuenta | Rol | Uso |
| --- | --- | --- |
| `ana@foundia.dev` | USUARIO | Dueña que reclama y presenta el QR de retiro |
| `carla@foundia.dev` | BIBLIOTECA | Escáner de entrega (H44). El rol se asignó el 17/11 con `PATCH /api/admin/usuarios/4/` y quedó auditado como cambio de rol n.º 2 |
| `admin@foundia.dev` | ADMIN | Prepara custodias y acepta reclamos |

## Datos de H44

`tmp/qa-h44-telefono/fixture.py` crea tres objetos en custodia con un
reclamo de Ana aceptado. También guarda en `tmp/qa-h44-telefono/` la etiqueta
y el QR de retiro de cada caso en PDF. La carpeta `tmp/` no se sube a Git
porque los PDF contienen tokens. Abre cada PDF en la laptop con zoom amplio y
escanéalo con el teléfono.

| Caso | Preparación | Resultado esperado |
| --- | --- | --- |
| A, entrega válida | Etiqueta A y QR de retiro A | Entrega registrada. El objeto pasa a RECUPERADO con fecha |
| A, QR usado | Repetir con la misma etiqueta A y el QR A | Rechazo por QR ya usado, sin cambiar el estado |
| Otro objeto | Etiqueta B y QR de retiro A, o etiqueta A y QR B | Rechazo porque no coinciden. B sigue en custodia |
| Vencido | Etiqueta C y QR de retiro C. En la base de QA, `qr_retiro_expira` se movió a ayer | Rechazo por QR vencido. C sigue en custodia |
| Sin permiso de cámara | Negar la cámara en «Registrar entrega» | Aviso y entrada manual disponibles |

La etiqueta de main lleva el enlace `…/custodia/verificar?t=<token>`. El
escáner Android lo acepta porque mide menos de 500 caracteres, y Django
extrae el token.

## Resto de las pruebas en el teléfono

- **H52:** tomar una foto real, repetirla y publicarla. Negar la cámara y
  usar la galería.
- **H46:** dictar en FINDI en un lugar con ruido, corregir el texto y
  enviarlo. Negar el micrófono y escribir.
- **H50:** dejar la app cerrada sin forzar el trabajo y esperar la
  notificación. Al tocarla debe abrirse la bandeja. El cable puede seguir
  conectado.
- **H51:** con TalkBack activo, recorrer Inicio, Publicar, Perfil y FINDI, y
  revisar el orden de lectura y las etiquetas. Repetir con el texto al 130 %.

Registra los resultados con capturas en `docs/pruebas/sprint3-amore/`, igual
que en [validacion-emulador-h50-h52.md](validacion-emulador-h50-h52.md).
