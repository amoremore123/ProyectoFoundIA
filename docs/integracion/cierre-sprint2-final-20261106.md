# Cierre del Sprint 2

Revisión final del 6 de noviembre de 2026 (America/Lima), después de retomar la
sesión interrumpida. Este documento reúne la corrección H35, las fuentes H33.1 y
el acuerdo de datos locales. Los registros anteriores conservan sus fechas reales.

## Alcance y datos de prueba

Se mantiene el alcance aprobado de **14 historias, 84 tareas y 183 horas
estimadas**, con el horizonte planificado de 10 días. No se convierten las
estimaciones en horas reales ni se sustituyen historias: H25, H32, H36, H37,
H38 y H40 permanecen en Sprint 3.

Leonel confirmó que cada integrante usa **su propia base local con datos de
prueba**. Spring y Django de una misma PC sí comparten ese MySQL. No hace falta
el SQL de Amore, acceder a su PC ni verificar una base central inexistente para
este trabajo. La exigencia anterior de ese archivo queda retirada; el
[acuerdo de datos locales](datos-locales-pruebas-sprint2.md) es la referencia actual.

Se conserva toda base existente, incluso si solo contiene datos de prueba.
`script.sql` y `seed.sql` son para instalaciones nuevas y vacías; no se usan
para actualizar una base existente. Antes de intervenir datos locales existentes
se respalda esa misma instalación y se aplican solo migraciones ausentes.

## Correcciones incluidas

- **H35:** los JWT de Spring y Django incluyen un sello de la credencial actual.
  Se rechaza la sesión anterior con 401 aunque el reset ocurra en el mismo
  segundo; el acceso con la nueva clave funciona inmediatamente. La compatibilidad
  anterior no admite sellos nulos o inválidos. No se añaden columnas.
- **H33.1:** fuentes oficiales de Tecsup, originales y transcripciones conservados,
  fecha de consulta y huellas verificables, disponibles en API, web y Android.
  La aceptación queda limitada a FoundIA, sin publicidad añadida ni atribuir una
  aprobación institucional del proyecto. Ver el
  [detalle de integración](h33-fuentes-oficiales-20261105.md).
- **Esquema:** mapeos JDBC `CHAR(64)` alineados con el SQL existente; arranque
  estricto de Hibernate comprobado contra MySQL QA. No se alteran datos ni SQL
  para resolver esa diferencia de mapeo.
- **Documentación:** se distingue la base común entre backends de una PC de una
  base central del equipo; no se deja el respaldo de otra integrante como bloqueo.

## Evidencia y criterio de cierre

**Resultado: las 14 historias y sus 84 tareas quedan cerradas para el alcance
de pruebas locales aprobado.** H35 y H33.1 están resueltas; la ausencia del SQL
de otra PC no es un pendiente de este escenario.

| Comprobación | Resultado y fecha de evidencia |
| --- | --- |
| Spring / Django | 422 / 57 correctas, 05/11; código funcional sin cambios posteriores |
| Web usuario / administración | 210 / 17 correctas y ambos builds, 05/11; misma fuente funcional |
| Android unitario / construcción | 41 correctas, APK 1.2.2 y lint: 0 errores / 25 advertencias, 05/11 |
| Android instrumentado nuevo | Los mismos 6 casos pasan en Android 16 (API36) y Android 11 (API30), 06/11; sin omisiones |
| H33 desde el APK | Términos oficiales, casilla desmarcada, aceptación explícita, registro, correo y consentimiento guardado, 06/11 |
| H35 desde el APK | Correo, puente web→Android, cambio, enlace usado/vencido rechazados y acceso con nueva clave, 06/11 |
| HTTP general nuevo | 14 historias, 58 peticiones reales, 06/11 |
| H35 temporal nuevo | 62 peticiones; caso del mismo segundo por cada emisor, anterior 401 y nuevo acceso inmediato 200, 06/11 |
| H39 navegador nuevo | Contexto anónimo y retirada comprobados, 06/11 |
| SMTP real nuevo | H33 revierte el alta; H35 mantiene neutralidad e invalida el enlace no entregado, 06/11 |
| Respaldo/restauración local | 14 tablas sintéticas restauradas idénticas, 06/11 local (07/11 UTC) |
| Migraciones locales | 8 archivos × 2 pasadas, datos conservados y esquema equivalente; SQL sin cambios desde la prueba del 05/11 |
| Postman local | 8 aserciones correctas sobre la API real; sin reporte cloud, 06/11 |

Resultados, capturas, artefactos y matriz:
[evidencias finales](../pruebas/cierre-sprint2-final-20261106/README.md).
Los 6 casos instrumentados se repitieron en dos plataformas: no se presentan como
12 pruebas distintas ni se suman a las 41 unitarias.

Las suites, las peticiones reales y las ejecuciones de dispositivo se registran
por separado, con el código y artefacto probados y las fechas de ejecución. La
matriz conserva las **84 descripciones originales y sus responsables**: no afirma
que se hayan realizado 84 pruebas manuales independientes ni suma repeticiones
HTTP como pruebas unitarias nuevas.

Los recorridos de pantalla nuevos se completaron en API30, compatible con el
`minSdk` de la app. La imagen API36 presentó avisos ANR de servicios auxiliares
y del sistema en esta PC de 8 GB; se conservan sus intentos fallidos como
diagnóstico, no como recorridos aprobados. No se modificó el APK ni se alargaron
los tiempos de las pruebas para obtener el pase. El AVD personal y los demás
dispositivos no se utilizaron.

En la prueba de expiración del enlace H35 se venció únicamente el registro de
una cuenta sintética QA; no se alteró el reloj. La prueba separada del mismo
segundo no manipula relojes ni fechas SQL.

El cierre cubre el funcionamiento del proyecto con datos locales sintéticos.
No es una certificación legal, de seguridad integral o accesibilidad, ni un
despliegue institucional; ninguno de esos hechos se inventa para marcar tareas.

## Conservación y publicación

Se mantienen el historial de Leonel, Amore y Yajaira, los originales Word/PDF,
las evidencias históricas, los volúmenes de datos y `.opencode/`. No se fuerzan
ramas ni se mueve la etiqueta publicada `sprint-2`, que identifica el primer
cierre. Las actualizaciones finales avanzan desde esa versión conservando su
historia. No se exige sincronizar ni copiar datos entre las computadoras.
El cierre completo se identifica con una nueva etiqueta `sprint-2-final`, sin
reescribir `sprint-2`. La publicación de ramas y etiqueta se verifica contra el
remoto después de consolidar estas evidencias.
