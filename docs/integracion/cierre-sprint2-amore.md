# Revisión integrada del Sprint 2 en dev/amore

Fecha: 3 de noviembre de 2026. Responsable de esta integración: Amore.

## Integración y alcance

Se unió `origin/dev/yajaira` en `dc0b5f1` a `dev/amore`, conservando su historial y
el commit local `95c9453`. El merge `0166c8c` no tuvo conflictos. El resultado
incluye las entregas de Leonel y Yajaira, el rediseño Android `f390325` y el cierre
Sprint 1 de `origin/main` (`18b6860`). No se hicieron cherry-picks, resets ni pushes
forzados. El trabajo se realizó en `dev/amore`; no se actualizó `dev/leonel`,
`dev/yajaira`, `main` ni la etiqueta `sprint-2`.

| Tarea de la planificación | Resultado en esta revisión |
| --- | --- |
| A1 / L1 — Recibir las entregas | Entrega de Yajaira y rediseño incluidos en dev/amore; se conservan los commits originales |
| A2 — H35 en el APK real | Solicitud, correo, paso web→Android, cambio, rechazo de enlace usado/vencido y nuevo login comprobados |
| L2 — Revisar las 14 historias | Regresión de las cuatro capas y 58 peticiones HTTP reales a API/administración/Mailpit en QA; detalle abajo |
| L3 — Respaldo y migraciones | Copia restaurada y comparada en QA; H33/H34/H35 ya estaban aplicadas y no se ejecutaron otra vez |
| L4 — Cierre en main | Candidato preparado en dev/amore. El destino main y la etiqueta requieren el cierre autorizado del equipo |

El [PR #5](https://github.com/amoremore123/ProyectoFoundIA/pull/5), creado desde
la entrega de Yajaira, ya existía y se consultó como referencia. No se publicó una
revisión en nombre de Leonel ni se aprobó o fusionó ese PR. Las correcciones de
esta revisión deben incorporarse al candidato que el equipo decida integrar;
no están incluidas automáticamente en el head original de Yajaira.

## Correcciones encontradas al conectar las capas

1. **H35 en el panel:** Django no comprobaba la fecha del último cambio de
   contraseña. Ahora rechaza la sesión anterior y el nuevo login sí funciona.
2. **Identidad compartida:** Spring emite `sub` con correo y Django esperaba ID.
   Django acepta ambos formatos, emite los nuevos con correo y fecha `iat`, y
   mantiene las autorizaciones basadas en el usuario real de la base. Los tokens
   existentes por ID siguen sujetos a la misma clave configurada del entorno.
   Se unificó el valor de desarrollo de JWT; cada entorno debe configurar una
   clave propia. Un entorno que usaba el valor predeterminado anterior de Django
   debe volver a iniciar sesión al actualizar; no se modifican cuentas ni datos.
3. **Fechas UTC:** con Java ejecutándose en Lima, el paso por `Timestamp` al
   guardar `LocalDateTime` en MySQL desplazaba las fechas cinco horas. Se habilitó
   el enlace JDBC directo y los callbacks que dependían de la zona local ahora
   generan UTC. Se comprobó la fecha almacenada frente a `UTC_TIMESTAMP()` en QA.
   No se corrigieron ni inventaron fechas históricas de la base compartida.
4. **Correo hacia Android:** la web ofrece abrir el enlace en FoundIA Android,
   conserva el token solo en su estado y lo retira de la dirección de la página.
5. **Estado del APK:** una recuperación anónima ya no destruye la confirmación;
   si hay sesión, la cierra con un aviso que exige volver a entrar. Reabrir el
   mismo enlace crea un formulario nuevo y vuelve a validarlo con el servidor.

La configuración de enlace directo corresponde a
[Hibernate 6.6, JAVA_TIME_USE_DIRECT_JDBC](https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/cfg/MappingSettings.html#JAVA_TIME_USE_DIRECT_JDBC).
La conversión entre fechas locales y valores que representan instantes está
explicada en la [documentación de Connector/J](https://dev.mysql.com/doc/connectors/en/connector-j-time-instants.html).

## Pruebas nuevas de esta revisión

| Capa | Resultado |
| --- | --- |
| Spring | 411 casos JUnit actuales, 0 fallos, errores u omitidos; JAR generado |
| Django | 49 casos correctos (42 existentes y 7 de sesiones/compatibilidad) |
| Web usuario | 208 casos correctos y compilación Vite correcta |
| Web administración | 17 casos correctos y compilación Vite correcta |
| Android | 38 casos unitarios correctos; APK compilado y lint sin errores |
| Android instrumentado | 6 casos correctos contra la API y MySQL QA reales |
| H35 APK | Correo real en Mailpit, cambio real, reutilización rechazada, vencimiento rechazado y nuevo login |
| H39 web real | Publicación abierta sin sesión y retirada después de eliminarla en la API |

Las cifras se conservan por capa; no se suman peticiones o repeticiones como
nuevas pruebas unitarias. Surefire informa 411 casos; el XML de la clase con tests
anidados registra 0 en su cabecera pero contiene 24 casos: se cuentan los nodos
`testcase`, que coinciden con el total de ejecución. El empaquetado se reintentó
sin repetir tests después de liberar el JAR que usaba QA; el JAR final se ejecutó
desde una copia separada para no bloquear nuevas compilaciones en Windows.

### Las 14 historias

| Historia | Comprobación real |
| --- | --- |
| H21 | Solo titular puede descartar; repetición segura y pareja excluida de las sugerencias |
| H22 | Perdido recuperado con fecha; hallazgo ajeno activo y conservado |
| H23 | Avisos propios reales y lectura persistente |
| H24 | Tres preferencias guardadas, consultadas y restauradas |
| H26 | Crear, editar, desactivar y reactivar manteniendo el ID |
| H27 | Denuncia con identidad de sesión y repetición sin duplicados |
| H28 | Resolución con actor/fecha; repetición segura y cambio posterior rechazado con 409 |
| H29 | Conteos reales y rango inválido rechazado con 400 |
| H30 | Estadísticas de recuperación calculadas con muestra real |
| H31 | Rankings reales, máximo cinco elementos y ubicación agrupada |
| H33 | Consentimiento requerido, versión/fecha/huella almacenadas y correo verificado |
| H34 | Solicitud pendiente con cuenta activa conservada |
| H35 | API, panel, correo y APK comprobados; token anterior no sirve y nuevo login correcto |
| H39 | Consulta pública sin sesión y retirada comprobadas en navegador real |

## Base compartida protegida

- Destino verificado: `foundia-mysql`, base `objetos_perdidos_db`, volumen
  `proyectofoundia_mysql_data`.
- Respaldo fuera del repositorio:
  `C:\Users\amore\Documents\ProyectoIntegrador\RespaldosFoundIA\cierre-sprint2-20261103\objetos_perdidos_db.sql`.
- La copia se restauró en otra base del contenedor QA y se compararon columnas,
  cantidades y huellas de todas las filas. Son 14 tablas; antes del trabajo la
  base compartida tenía 7 usuarios y 16 objetos.
- H33, H34 y H35 ya estaban presentes. No se ejecutó DDL ni se cambiaron filas de
  la base compartida. No se reimportó `script.sql`/`seed.sql`, no se usó `down -v`
  y no se borraron tablas, datos ni volúmenes.
- Los datos nuevos de prueba se crearon solo en MySQL QA, puerto 13372. La copia
  del respaldo y los tokens/contraseñas de prueba no están en Git.

La herramienta `scripts/cerrar-base-sprint2.py` separa respaldar, verificar y
migrar, exige una copia verificada y rechaza cambios de datos posteriores al
respaldo. Solo considera las tres migraciones autorizadas por la planificación.

## Evidencias y reproducción

Resultados y capturas: [evidencias de esta revisión](../pruebas/cierre-sprint2-20261103/).
Los archivos `resumen.json`, `api-14-historias.json`, `base-compartida.json` y
`h35-apk.json` registran los resultados y las huellas de entrega.

- `mvn -f backend/springboot/pom.xml verify` (Java 17).
- `python backend/django-admin/manage.py test nucleo --settings=config.settings_test --noinput`.
- En cada web: `npm ci`, `npm test`, `npm run build`.
- `movil/android/gradlew.bat -p movil/android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` (Gradle JDK 17).
- Integración aislada: `scripts/qa-cierre-sprint2.compose.yml`, API 18083,
  Django 18004, web 15176 y Mailpit 18372, con una clave JWT QA común y el mismo
  MySQL QA 13372. `scripts/verificar-cierre-sprint2.py` rechaza el volumen compartido.
- `scripts/verificar-cierre-sprint2-web.cjs` prueba el enlace público y la retirada.
  Requiere Playwright instalado en el entorno de ejecución.
- `scripts/verificar-h35-apk.py` requiere el AVD QA de puerto 5560 con marcador
  `debug.foundia.qa_cierre=20261103` y un APK compilado con
  `-PfoundiaApiUrl=http://10.0.2.2:18083/ -PfoundiaWebUrl=http://10.0.2.2:15176/`.

En H35 se ajustaron exclusivamente las fechas de los registros sintéticos QA
para volver a solicitar un enlace y probar uno ya vencido sin esperar 30 minutos.
No se cambió el reloj del sistema ni la vigencia normal de la aplicación.
La prueba del APK se ejecutó en dos fases sobre la misma cuenta después de
corregir la navegación del ejecutor de pruebas hacia la pantalla de acceso.
No se presentan tokens, contraseñas ni datos reales de la base compartida en las
capturas. No se acredita una certificación de accesibilidad ni un teléfono físico.

## Artefactos para la PC

- Android: `movil/android/output/FoundIA-Sprint2-Amore-1.2.1.apk` (versión 1.2.1,
  código 4). Usa API 8081 y web 5173 mediante `10.0.2.2` en el emulador.
- Spring: `output/FoundIA-Sprint2-Amore-20261103.jar`.
- Android Studio: abrir `movil/android`, sincronizar Gradle con JDK 17 y ejecutar.
  En un teléfono físico se deben usar las direcciones accesibles de la PC/servidor.

Esta entrega prepara el cierre del Sprint 2 en la rama solicitada. La aprobación
final, la integración en main y la etiqueta `sprint-2` corresponden al cierre que
autorice el equipo; aquí no se modificaron esas referencias. Los contenedores
compartidos no se sustituyeron por imágenes nuevas: el despliegue del código se
realiza cuando el equipo lo acuerde.
