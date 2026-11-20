# Validación en emulador de H52 y H50 (17/11/2026)

Prueba manual guiada por `adb` y `uiautomator` sobre el APK de `dev/amore`
(commit `8fa3598`, con main integrado). Se probó contra APIs reales en Docker:
Spring en 18281, Django en 18200 y MySQL propio del proyecto aislado
`foundia-amore-s3`. No se usaron mocks.

- Emulador: `Medium_Phone`, API 37, texto al 100 %.
- APK de QA: `-PfoundiaApiUrl=http://10.0.2.2:18281/`,
  `-PfoundiaAdminApiUrl=http://10.0.2.2:18200/` y
  `-PfoundiaWebUrl=http://10.0.2.2:18273/`.
- Cuentas de prueba del seed: `ana@foundia.dev`, `luis@foundia.dev` y
  `admin@foundia.dev`.

Las capturas están en [emulador-20261117](emulador-20261117/).

## H52: publicar con foto de la cámara

Los pasos se hicieron en el paso 3 de 6 de Publicar.

| Caso | Resultado |
| --- | --- |
| Negar el permiso de cámara | Aparece «Sin permiso de cámara. Puedes elegir una fotografía de la galería.» El formulario conserva los datos. ([captura](emulador-20261117/h52-03-permiso-negado.png)) |
| Galería como alternativa | «Elegir fotografía» abre el selector del sistema (`com.google.android.photopicker`). Al cancelar, el formulario sigue en el mismo paso. ([captura](emulador-20261117/h52-04-galeria.png)) |
| Reintento después de negar | Al volver a pulsar «Tomar fotografía», Android pide otra vez el permiso. Con «While using the app» se abre la vista previa de CameraX. ([captura](emulador-20261117/h52-05-camarax-preview.png)) |
| Tomar, revisar y repetir | Se toma la foto, aparece la vista previa y «Volver a tomar fotografía» reemplaza la imagen. ([captura](emulador-20261117/h52-07-repetida.png)) |
| Compresión | Las capturas JPEG pesan 83 212 y 83 010 bytes, muy por debajo del límite de 5 MiB. |
| Publicación con foto | Se publicó el objeto 13. MySQL registra la foto `/uploads/dff62eca-….jpg`. `GET` de esa ruta devuelve `200 image/jpeg` con 83 010 bytes, el tamaño exacto de la segunda captura. ([captura](emulador-20261117/h52-09-publicado.png)) |

**Observación corregida:** en esta prueba, la captura descartada seguía en
`cache/capturas` después de publicar. El commit `d91144b` borra la captura
anterior al repetir o quitar la foto. También borra la captura publicada y
la que queda pendiente cuando se cierra Publicar. Si la foto no sube, se
conserva para reintentar. Solo se borran archivos de la carpeta de capturas
de la app, nunca fotos de la galería. En el emulador, al repetir quedó una
sola captura nueva. Después de publicar el objeto 17 con su foto no quedó
ninguna. ([captura](emulador-20261117/h52-10-limpieza-publicado.png))

## H50: aviso de un reclamo en el celular

Para preparar cada caso, Luis publicó un objeto, el admin lo registró en
custodia, Ana lo reclamó y el admin aceptó el reclamo. Las respuestas HTTP
están en [h50-preparacion-http.json](emulador-20261117/h50-preparacion-http.json)
(24 llamadas, todas con el código esperado).

| Caso | Resultado |
| --- | --- |
| Permiso de notificaciones negado | El worker se ejecutó solo a las 16:05 con la app en segundo plano y terminó en `SUCCESS` sin publicar ninguna notificación. Tampoco avanzó el cursor. |
| Conceder el permiso desde la app | «Permitir avisos de reclamo en el dispositivo» abre el diálogo del sistema. Al aceptarlo, la app muestra «Notificaciones del sistema habilitadas». ([captura](emulador-20261117/h50-03-permiso-concedido.png)) |
| App cerrada | Se ejecutó `HOME` y `am kill`, así que no quedaba proceso de la app. Android tenía el trabajo listo, pero lo retrasó por el bucket de standby. Se forzó con `cmd jobscheduler run -f` después de cumplirse el periodo de 15 minutos. Se publicó la notificación 50 en el canal `foundia.reclamos`, con visibilidad privada, mientras el launcher estaba en primer plano. ([captura](emulador-20261117/h50-04-notificacion-app-cerrada.png)) |
| Avisos ajenos | Antes de la consulta se creó un aviso de reclamo para Luis con ID 13, mayor que el último de Ana, que era 12. El cursor de Ana quedó en `ultimo_2 = 12`, así que el aviso de Luis nunca llegó al dispositivo de Ana. |
| Abrir la pantalla correcta | Al tocar la notificación se abre `MainActivity` en «Tus novedades». Esa bandeja autenticada muestra «Reclamo aceptado» del objeto de Ana. ([captura](emulador-20261117/h50-05-bandeja-abierta.png)) |
| Preferencia «Reclamos de objetos» desactivada | El servidor guarda `reclamos:false`. ([captura](emulador-20261117/h50-06-preferencia-reclamos-off.png)) Se creó para Ana el aviso 15, que queda en su historial. Con la app cerrada, el worker se forzó a las 16:40 y terminó en `SUCCESS`, sin publicar ninguna notificación. El cursor siguió en 12. |

## Qué no cubre esta prueba

- La cámara física y la calidad de la foto en un teléfono. El emulador usa
  una escena virtual.
- La entrega natural de WorkManager en un teléfono real. En el emulador,
  Android retrasó el trabajo y la ejecución con la app cerrada se forzó
  después del periodo.
- H44 (escaneo físico de los QR), H46 (dictado con ruido) y H51 (TalkBack y
  texto al 130 %). Siguen pendientes de probar en un teléfono.
