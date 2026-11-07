# Evidencias finales del Sprint 2 — 06/11/2026

Las aplicaciones conservan el código funcional verificado en `dba319e`; las
actualizaciones finales posteriores son documentales. Se separan los resultados
nuevos de los reutilizados sobre fuentes idénticas, y se mantienen todos los
antecedentes con sus fechas originales.

- `resumen.json`: alcance, resultados por capa, fechas, artefactos y conservación.
- `matriz-84-tareas.csv`: descripciones/responsables originales y estado de cierre;
  no representa 84 pruebas manuales independientes.
- `instrumentadas-api30.json`, `instrumentadas-api36.json`: mismos 6 casos, en
  dos plataformas, sin fallos ni omisiones y con API/MySQL/Room reales.
- `h33-apk.json`, `h35-apk.json`, `capturas/`: recorridos reales de pantalla en
  Android 11 (API30), con el APK QA 1.2.2 y correo local Mailpit.
- `api-14-historias.json`: 14 historias y 58 peticiones HTTP reales del 06/11.
- `h35-revocacion.json`: 62 peticiones; anterior 401 y nuevo 200 dentro del mismo
  segundo por cada emisor, sin manipular fechas ni relojes.
- `h39-web-real.json`: publicación anónima y retirada en navegador real.
- `fallo-smtp-qa.json`: efectos de una interrupción real del SMTP QA.
- `respaldo-local.json`: copia/restauración idéntica de 14 tablas **sintéticas de
  esta PC**. No se necesita ni se afirma restaurar el SQL de otra integrante.
- `migraciones-locales.json`: ocho archivos en dos pasadas, con sus huellas;
  prueba del 05/11 válida para el SQL idéntico de la entrega final.
- `huellas.json`: SHA-256 del contenido versionado; textos normalizados a LF,
  imágenes sin transformación. No se incluye el propio índice.

## Entorno y límites de la evidencia

Se usaron AVD nuevos bajo el directorio privado de auditoría y el serial explícito
`emulator-5560`. No se usó el AVD personal ni se instalaron APK en otros equipos.
La imagen API36 tuvo avisos ANR de servicios Google, del lanzador y de System UI:
sus intentos visuales no se cuentan como aprobados. Las 6 instrumentadas sí
pasaron en API36 y API30; los recorridos visuales nuevos finalizaron en API30.
No se cambió el código del APK ni se ampliaron tiempos de prueba para el pase.

Los escenarios de enlace vencido ajustan exclusivamente registros sintéticos
de esa QA, no el reloj. Las capturas se revisaron para no publicar tokens ni
contraseñas. Los logs completos, secretos y SQL quedan fuera de Git.

Cada integrante conserva sus datos locales de prueba. Los nombres de base y
contenedor iguales en distintas computadoras no comparten sus volúmenes. La
petición anterior de un respaldo central se retira conforme a la aclaración del
usuario; no se trata como una verificación realizada ni como un pendiente actual.

## Repetir las instrumentadas

Arrancar únicamente la QA aislada de `scripts/qa-cierre-sprint2.compose.yml`,
Spring en 18083 conectado a `127.0.0.1:13372` (no al MySQL habitual 3306), correo
SMTP en 11372 y un AVD QA propio. Comprobar que `emulator-5560` sea ese AVD,
no el personal, antes de instalar. Con JDK/SDK configurados:

```powershell
movil/android/gradlew.bat -p movil/android --no-daemon --max-workers=2 -PfoundiaApiUrl=http://10.0.2.2:18083/ -PfoundiaWebUrl=http://10.0.2.2:15176/ :app:assembleDebug :app:assembleDebugAndroidTest
adb -s emulator-5560 install -r movil/android/app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5560 install -r movil/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5560 shell am instrument -w -r -e probarApiReal true com.proyectointegrador.movil.test/androidx.test.runner.AndroidJUnitRunner
```

El opt-in `probarApiReal` y el origen 18083 son obligatorios para los dos casos
que escriben en la API. Los otros cuatro prueban Room. No dirigirlos a una base
de desarrollo existente ni ejecutar instalaciones sin el serial explícito.
