# H21 — Descartar coincidencias: API y datos

Fecha: **25 de octubre de 2026**. Rama: `dev/leonel`.
Base: Sprint 1 integrado, `18b6860`. Sin push ni integración a `main`.

## Alcance

Se implementa únicamente backend/datos de H21. No se declara cerrada la historia:
faltan web de Yajaira y Android de Amore, con diálogos, cancelación, reintentos y
pruebas de sus clientes. El contrato se publica en [endpoints](../api/endpoints.md).
No hay migración: el esquema existente ya admite `DESCARTADA` y pareja única.

## Casos automatizados

Clase: `DescartarCoincidenciaIntegrationTest`. Usa exclusivamente H2 temporal
`descarte_s2`, sin una transacción envolvente: cada petición confirma su propia
transacción y las comprobaciones posteriores vuelven a leer la persistencia.

| Caso | Resultado esperado |
| --- | --- |
| Titular del perdido, pareja válida | 200, fila DESCARTADA y objetos intactos; sin contacto ni aviso. |
| Sin sesión, autor del hallazgo, tercero y ADMIN ajeno | 401/403; un `usuarioId` en el cuerpo no suplanta al actor. |
| Clave y posterior ID persistido | Misma pareja y fila; repetición señalada, fecha y puntuación históricas conservadas. |
| GET repetidos desde ambos objetos y detalle | Pareja excluida; detalle 404; otras parejas intactas y sin nuevas escrituras. |
| IDs mal formados, desbordados o inexistentes | 404, sin filas nuevas. |
| Oculto, eliminado o recuperado | 404/409, sin descarte. |
| Pareja aceptada | 409, sin modificar la aceptación. |
| Contactar después de descartar | 409; no crea contacto ni notificación. |
| Tipos/autores/categorías incompatibles, clave invertida o similitud insuficiente | Rechazo sin persistir la pareja. |
| Dos descartes simultáneos | Una fila y una respuesta marcada como repetición. |
| Descarte y contacto simultáneos | Estado final DESCARTADA; como máximo un contacto/aviso si el contacto ocurrió primero. |

Se comparten bloqueos por ID ascendente con H18. La lectura de coincidencia es
para actualización, evitando depender de una instantánea antigua en MySQL
`REPEATABLE READ` al resolver un ID persistido antes de obtener los bloqueos.

## Ejecución

Desde `backend/springboot`:

```text
mvn -B -ntp test -Dtest=DescartarCoincidenciaIntegrationTest
mvn -B -ntp verify
```

Resultados H2: **19 pruebas H21 aprobadas** y **330 pruebas de regresión aprobadas**,
sin fallos, errores ni omisiones. `verify` generó el JAR con `BUILD SUCCESS`,
incluyendo regresiones H16/H17/H18. Rendimiento H16: 500 candidatos de 1001
publicaciones, cinco muestras de 139/85/87/47/71 ms y 3 consultas en la última.

En este equipo, Java 21 requiere cargar Mockito antes de iniciar las pruebas
para evitar la espera de auto-adjunción. Ejemplo PowerShell:

```powershell
mvn -B -ntp verify "-DargLine=-javaagent:$env:USERPROFILE\.m2\repository\org\mockito\mockito-core\5.17.0\mockito-core-5.17.0.jar"
```

El primer intento sin agente excedió 120 s; no se cuenta como prueba aprobada.
Además, **19 pruebas H21 aprobadas en MySQL 8.4.6**, con InnoDB y aislamiento
`REPEATABLE READ`, incluidas las dos pruebas concurrentes. Se usó MySQL portátil
en `127.0.0.1:53749`, base nueva `foundia_s2_qa_2123ee8180cf`, directorio propio en
la auditoría y usuario limitado a esa base. Se importó una copia del esquema
**sin DROP, CREATE DATABASE ni USE** después de comprobar que la base no existía.
No se iniciaron Docker ni servicios del equipo.

`DescartarCoincidenciaMySqlQa` reutiliza los mismos casos con ejecución explícita
`mvn -B -ntp test -Dtest=DescartarCoincidenciaMySqlQa` y el agente anterior. Requiere
`FOUNDIA_QA_MYSQL_PORT`, `FOUNDIA_QA_MYSQL_DATABASE`, `FOUNDIA_QA_MYSQL_PASSWORD` y
`FOUNDIA_QA_MYSQL_TOKEN`: solo admite localhost, puerto no estándar y esquema con
nombre QA y token de propiedad en `foundia_qa_guard`. Sin ellos falla de forma
segura, no recurre a la conexión normal. Hibernate usa `validate`, no crea ni borra
el esquema. La suite limpia sus datos exclusivamente en esa base desechable.

Evidencia local: `foundia-leonel-s2-inicio-20261025-172010` en el directorio temporal
de OpenCode: `h21-final.log`, `h21-verify.log`, `h21-mysql.log`, estados JSON,
`preparar_mysql_qa.py`, `probar_mysql_qa.py` y reportes de Surefire.
Se verificaron 1727 archivos protegidos y las demás referencias Git, sin cambios.
No ejecutar `database/script.sql`, migraciones ni restauraciones sobre bases del
equipo para reproducir estas pruebas.
