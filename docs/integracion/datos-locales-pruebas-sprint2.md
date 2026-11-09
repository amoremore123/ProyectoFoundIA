# Acuerdo de datos locales para las pruebas del Sprint 2

**Aclaración de Leonel, 05/11/2026:** mientras el proyecto está en pruebas, cada
integrante conserva sus propios datos en su PC. **No existe una BD central del
equipo que deba copiarse para continuar ni se necesita el SQL de Amore.**

## Qué comparte cada instalación

- Spring y Django de **una misma PC** usan su MySQL local y la misma clave JWT
  configurada para ese entorno. Esa es la base común entre los dos backends.
- Los volúmenes Docker, cuentas y publicaciones de Leonel, Amore y Yajaira son
  independientes. Tener el mismo nombre de base o contenedor en computadoras
  distintas no comparte sus datos.
- Se utilizan cuentas, correos, fotos y publicaciones sintéticos de prueba, no
  información real de usuarios. Mailpit retiene los correos localmente.
- No se importan datos de otra persona ni se conectan las pruebas a su servidor.

## Instalación nueva frente a una base que ya tiene datos

En una instalación local **nueva y vacía**, `docker compose up --build` inicializa
el volumen local con `database/script.sql` y `database/seed.sql` y arranca las
aplicaciones. Cada integrante obtiene su propio conjunto inicial de prueba.

En una base que **ya existe**, se conserva su volumen y sus filas: se compara el
esquema y se aplican únicamente las migraciones que falten, respaldando primero
esa base local cuando vaya a intervenirse. No se ejecutan `script.sql` ni
`seed.sql` para actualizarla: ambos pueden borrar datos. Tampoco se usa `down -v`.

El respaldo previo es una precaución para los datos de la instalación que se va a
modificar; **no obliga a conseguir un archivo de otra PC ni bloquea probar el
código con una base local nueva y aislada**.

## QA de integración y Android

`scripts/qa-cierre-sprint2.compose.yml` separa la QA del entorno de desarrollo:
MySQL 13372, API 18083, Django 18004, web 15176 y Mailpit SMTP 11372 / HTTP 18372.
Los verificadores comprueban su etiqueta y rechazan el volumen de desarrollo.
Solo generan datos sintéticos en esa QA.

El Android habitual usa `10.0.2.2:8081` para llegar a la API de la **PC que aloja
su emulador**. El APK QA usa 18083 y web 15176 mediante propiedades Gradle. Las
pruebas de dispositivo deben indicar el serial del emulador QA; no se instalan
aplicaciones ni se ejecutan pruebas en teléfonos o AVD personales por defecto.

## Corrección de la revisión anterior

La petición de conseguir el respaldo de la PC de Amore partió de interpretar que
había una base central compartida por el equipo. Esa condición **no aplica al
acuerdo confirmado por Leonel**. Los informes y huellas anteriores se conservan
como antecedentes, pero la ausencia de ese archivo deja de ser un bloqueo.
Una restauración local sintética demuestra el flujo local probado; no se afirma
haber restaurado el archivo de otra PC ni desplegado un servicio institucional.
