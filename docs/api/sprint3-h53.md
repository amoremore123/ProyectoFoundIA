# H53 — Recordatorios de objetos sin resolver

> Estado de esta entrega: detección programada, generación de la notificación
> in-app, preferencia y migración SQL implementadas y probadas en Spring Boot
> con H2. La visualización en la web (Yajaira) y en Android (Amore) queda en
> sus capas; el contrato ya está disponible. La migración no se ejecutó contra
> un MySQL real en este equipo porque el daemon de Docker no estaba disponible.

## Cuándo un objeto está sin resolver

Un objeto entra en la detección cuando cumple **todas** estas condiciones:

| Condición | Valor |
| --- | --- |
| Tipo | `PERDIDO` |
| Estado | `ACTIVO` |
| Fecha de recuperación | `NULL` |
| Antigüedad de la publicación | al menos `RECORDATORIOS_DIAS_SIN_RESOLVER` días |

Se usa `objetos.fecha_publicacion`, no `fecha_objeto`: así una publicación
nueva sobre un hecho antiguo no recibe un recordatorio inmediato. Los objetos
`ENCONTRADO`, `EN_CUSTODIA`, `RECUPERADO`, `OCULTO` o `ELIMINADO` no reciben
recordatorios.

## Configuración

| Variable | Valor por defecto | Significado |
| --- | --- | --- |
| `RECORDATORIOS_HABILITADOS` | `true` | activa la tarea programada |
| `RECORDATORIOS_DIAS_SIN_RESOLVER` | `7` | días mínimos desde la publicación |
| `RECORDATORIOS_INTERVALO_MS` | `86400000` | espera entre ejecuciones (24 h) |
| `RECORDATORIOS_RETRASO_INICIAL_MS` | `300000` | espera antes de la primera ejecución (5 min) |

La tarea usa `fixedDelay`, por lo que una ejecución no se solapa con la
siguiente en el mismo proceso. Un valor de `RECORDATORIOS_DIAS_SIN_RESOLVER`
menor que `1` desactiva la generación y deja un aviso en el log.

## Flujo

1. `RecordatorioObjetoScheduler` ejecuta `RecordatorioObjetoService` cada
   intervalo configurado.
2. El servicio calcula la hora y el periodo con `Clock` en UTC
   (`yyyy-MM`) y consulta los objetos que cumplen la definición anterior.
3. Por cada objeto, `RecordatorioObjetoGenerador` abre una transacción
   `REQUIRES_NEW`, vuelve a comprobar que siga sin resolverse y consulta la
   preferencia `recordatorios` del dueño.
4. Si la preferencia está activa y no existe un registro para
   `(objeto_id, periodo)`, crea una `Notificacion` y un
   `RecordatorioObjeto`.
5. La restricción única `uk_recordatorio_objeto_periodo` es la última defensa
   contra duplicados si dos ejecuciones coinciden. Un fallo en un objeto no
   revierte los recordatorios de los demás.

No se envían correos ni notificaciones push: el aviso es in-app, igual que los
avisos de coincidencias, contactos y reclamos.

## Notificación generada

```json
{
  "id": 41,
  "titulo": "Tu objeto sigue sin resolver",
  "mensaje": "Han pasado varios días desde que publicaste \"Mochila azul\" (#27) y sigue sin resolverse. Si ya lo recuperaste, actualiza su estado.",
  "tipo": "RECORDATORIO_OBJETO",
  "leida": false,
  "fechaCreacion": "2026-11-09T03:00:00",
  "enlace": "/objeto/27"
}
```

El mensaje solo usa el nombre y el identificador de la publicación del propio
destinatario; no incluye correo, teléfono ni datos de otras cuentas. El enlace
`/objeto/{id}` ya es una ruta válida del frontend de usuario.

## Preferencia `recordatorios`

`GET /api/notificaciones/preferencias` devuelve los cuatro valores:

```json
{ "coincidencias": true, "contactos": true, "reclamos": true, "recordatorios": true }
```

`PUT /api/notificaciones/preferencias` acepta el cuarto campo:

```json
{ "coincidencias": true, "contactos": true, "reclamos": true, "recordatorios": false }
```

- `coincidencias`, `contactos` y `reclamos` siguen siendo obligatorios.
- `recordatorios` es opcional para no romper a los clientes de H24. Si no
  llega, se conserva el valor guardado; en una cuenta nueva vale `true`.
- Cambiar la preferencia solo afecta a recordatorios futuros. No borra
  notificaciones ni registros históricos.
- Si la preferencia está desactivada no se crea el registro del periodo. Si se
  vuelve a activar dentro del mismo mes, el próximo barrido puede generar el
  recordatorio de ese mes.

## Base de datos

En una instalación nueva, `database/script.sql` crea la columna y la tabla. En
una base existente se aplica
[`database/migracion_h53_recordatorios.sql`](../../database/migracion_h53_recordatorios.sql),
que es idempotente:

```sql
ALTER TABLE preferencias_notificacion
    ADD COLUMN recordatorios BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE recordatorios_objeto (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    objeto_id       BIGINT      NOT NULL,
    periodo         CHAR(7)     NOT NULL,
    notificacion_id BIGINT      NULL,
    fecha_creacion  DATETIME(6) NOT NULL,
    CONSTRAINT uk_recordatorio_objeto_periodo UNIQUE (objeto_id, periodo),
    CONSTRAINT fk_recordatorio_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos(id) ON DELETE CASCADE,
    CONSTRAINT fk_recordatorio_notificacion FOREIGN KEY (notificacion_id)
        REFERENCES notificaciones(id) ON DELETE SET NULL,
    INDEX idx_recordatorios_periodo (periodo)
);
```

`periodo` es el mes UTC con formato `yyyy-MM`. No se rellenan meses perdidos:
si el servicio estuvo apagado, en el siguiente arranque genera como máximo el
recordatorio del periodo actual.

## Pruebas

`backend/springboot/src/test/java/com/proyectointegrador/controller/RecordatoriosIntegrationTest.java`
(**3 pruebas**):

1. avisa solo a objetos `PERDIDO` + `ACTIVO`, publicados hace al menos 7 días y
   sin fecha de recuperación; excluye recientes, encontrados, ocultos y
   recuperados; comprueba tipo, enlace, destinatario y ausencia de correos;
2. no repite el recordatorio en el mismo periodo y sí lo genera al cambiar de
   periodo;
3. respeta `recordatorios=false`, conserva el valor cuando un cliente H24 envía
   solo tres campos y vuelve a generar al activarlo.

También se ejecutó `PreferenciasIntegrationTest` (**14 pruebas**) para
comprobar que el contrato de H24 sigue funcionando.

## Tareas de otras capas

- **Web (Yajaira):** agregar `RECORDATORIO_OBJETO` a las etiquetas de
  `frontend/usuario/src/pages/Notificaciones.jsx`, añadir la opción
  `recordatorios` a `PreferenciasNotificaciones.jsx` y mostrar el aviso. El
  enlace `/objeto/{id}` ya lo acepta `enlaceInterno`.
- **Android (Amore):** mostrar el tipo `RECORDATORIO_OBJETO`, abrir el detalle
  del objeto y añadir el cuarto interruptor de preferencias con el mismo
  contrato.
