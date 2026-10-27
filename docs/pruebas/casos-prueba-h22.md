# H22 — Confirmación de recuperación: API y datos

Fecha: **25 de octubre de 2026**. Rama: `dev/leonel`.
Base funcional: H21, `45dd0e1`. Sin push ni integración a `main`.

## Alcance y contrato

Backend/datos de H22, no cierre de la historia completa. Web de Yajaira y Android
de Amore quedan pendientes: diálogo informado, cancelación, reintento, doble clic/toque,
fallo de red y refresco de listas/detalles con datos comunes de QA.
Contrato: [POST de confirmación](../api/endpoints.md).

La API exige `confirmarRecuperacion: true`. Solo el titular del perdido puede
aceptar una pareja; los campos extra del cuerpo no cambian el actor. Aceptación,
estado RECUPERADO y fecha UTC del perdido se guardan atómicamente. No se cambia
el hallazgo ajeno ni se generan contactos, reclamos o avisos. Repetir conserva
fecha; otra pareja o un descarte no se acepta. No se inventan fechas anteriores.

Los bloqueos siguen el orden por ID de H21/H18 y la coincidencia se lee para
actualización, también al usar ID persistido. Edición y eliminación de objetos
toman el mismo bloqueo de escritura para evitar sobrescribir una recuperación.

## Casos automatizados

`ConfirmarRecuperacionIntegrationTest` usa H2 temporal `recuperacion_s2`, con una
transacción real por petición; no hay rollback envolviendo toda la clase.
`ConfirmarRecuperacionMySqlQa` hereda los mismos casos en MySQL exclusivo,
con las protecciones de conexión documentadas en [H21](casos-prueba-h21.md).

| Caso | Resultado esperado |
| --- | --- |
| Confirmación válida | ACEPTADA + RECUPERADO + fecha UTC; respuesta sin datos privados. |
| Hallazgo ajeno | Estado, fecha de recuperación y fecha de actualización intactos. |
| Detalle, perfil y sugerencias | Fecha persistida visible; perdido retirado de sugerencias desde ambos objetos. |
| Cuerpo/campo ausente, null o false | 400, sin cambios. |
| Visitante, hallador y ADMIN ajeno | 401/403; sin suplantación por `usuarioId`. |
| PENDIENTE/REVISADA, clave e ID persistido | Aceptación y repetición sin duplicar ni alterar fecha/puntuación históricas. |
| Cambio posterior del texto de sugerencia | Repetición conserva la recuperación confirmada. |
| OCULTO/ELIMINADO/RECUPERADO en cualquiera de los objetos | Rechazo sin escrituras. |
| DESCARTADA, otra pareja, aceptación histórica sin fecha o incoherente | 409, no se inventan fechas ni otra aceptación. |
| ID, tipos, autor, categoría o similitud inválidos | Rechazo sin guardar. |
| Dos confirmaciones de la misma pareja, por clave y por ID persistido | Una aceptación y una repetición, misma fecha. |
| Dos parejas competidoras, nuevas y persistidas | Exactamente una aceptación y un conflicto; hallazgos intactos. |
| Confirmación y descarte simultáneos | Una transición válida; nunca recuperación parcial ni descarte resucitado. |
| Confirmación y edición simultáneas | Se conserva la edición y también RECUPERADO con su fecha. |
| Fallo de BD tras flush del perdido, con fila previa y sin ella | 500 genérico; rollback de estado, fecha, actualización y aceptación; reintento válido. |

El fallo de rollback es real: una restricción CHECK temporal rechaza ACEPTADA
después de guardar/flush del perdido. Se retira siempre al terminar el caso,
**solo en la base de QA**. No se reemplaza el repositorio por un mock.

## Migración aislada

`migracion_h22_fecha_recuperacion.sql` añade una única columna DATETIME nullable.
Se ejecutó **dos veces** en cada una de dos bases nuevas, creadas exclusivamente
para esta validación: `foundia_s2_migracion_7fb81ccc21ec` y
`foundia_s2_qa_2123ee8180cf`, MySQL portátil 8.4.6 en `127.0.0.1:53749`.
Se verificaron todos los campos previos de las 9/10 tablas respectivamente:
**sin cambios**, sin segunda columna y sin fechas inventadas, incluido un perdido
histórico RECUPERADO con pareja ACEPTADA y textos UTF-8.

No se ejecutó el esquema original destructivo ni migración alguna contra bases
del equipo. No se usó el volumen compartido. La aplicación en esas bases queda
pendiente de autorización, respaldo y despliegue.

## Reproducción y evidencia

Desde `backend/springboot`, con el agente Mockito de H21 si se usa este Java 21:

```text
mvn -B -ntp verify
mvn -B -ntp test -Dtest=DescartarCoincidenciaMySqlQa,ConfirmarRecuperacionMySqlQa
```

La segunda orden requiere las variables y el token de una base nueva marcada;
sin ellos falla sin usar la base predeterminada. No ejecutar contra una base compartida.
Resultados finales: **352 pruebas aprobadas en `verify`**, incluidas 19 de H21 y
22 de H22; **41 pruebas H21/H22 aprobadas en MySQL 8.4.6** con InnoDB y aislamiento
`REPEATABLE READ`. Cero fallos, errores u omisiones en ambas ejecuciones finales.
`verify` compiló y generó el JAR con `BUILD SUCCESS`. Se mantienen las regresiones
de Sprint 1; H16 procesó 500 candidatos con 3 consultas en la última muestra.

La primera ejecución focalizada tenía dos expectativas incorrectas: el fallo de
CHECK deliberado es un error interno `500`, no un conflicto de negocio `409`.
Se corrigió la prueba para exigir el mensaje genérico existente y comprobar el
rollback; no se cambió el manejo global para ocultar el error de persistencia.

### HTTP real sobre el JAR

**23 peticiones HTTP aprobadas** con JWT y MySQL, sobre una segunda instalación
desde el esquema actualizado sin DROP, base nueva `foundia_s2_http_531e1beffdd3`.
Spring escuchó únicamente en `127.0.0.1:55158`, con usuario SQL limitado a esa base
y `ddl-auto=validate`. Se probaron permisos, consentimiento, descarte persistente,
contacto bloqueado, aceptación/repetición, otra pareja, detalle/perfil, sugerencias
y revocación del JWT al suspender la cuenta. Los GET no modificaron los recuentos:
2 coincidencias, 0 contactos y 0 avisos. El hallazgo ajeno permaneció idéntico.

SHA-256 del JAR probado:
`824a3ea490e846d1d135027a53110fe8ba057cad2fd69f8b5d3e2476e0b8830f`.
El primer smoke asumía un orden entre publicaciones creadas en el mismo segundo;
se corrigió el ejecutor para localizar por ID y validar la fecha, sin modificar
el ordenamiento ni ampliar el alcance de H22.

Auditoría local: `foundia-leonel-s2-inicio-20261025-172010` en el directorio temporal
de OpenCode, con `h22-test.log`, `h22-verify.log`, `h22-mysql.log`, estados JSON, reportes Surefire,
`validar_migracion_h22.py` y `h22-migracion-evidencia.json`.
La prueba HTTP conserva `probar_http_h21_h22.py`, `http-spring.log` y
`http-h21-h22-evidencia.json`. Se verificaron 1727 archivos protegidos y las demás
referencias Git, sin diferencias; los servicios temporales se detienen al finalizar.
