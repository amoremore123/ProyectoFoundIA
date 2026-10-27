# H22 — recuperación: casos de aceptación de la API

Contrato: [endpoints](../api/endpoints.md). Web y Android se integran después.

`ConfirmarRecuperacionIntegrationTest` comprueba:

- JWT del titular del perdido y `confirmarRecuperacion: true` obligatorio.
- Cancelar, omitir, null o false no produce cambios.
- Aceptar una pareja válida guarda ACEPTADA en la pareja y RECUPERADO en el
  perdido, con fecha UTC, en una sola transacción. El hallazgo ajeno no cambia.
- Repetir devuelve la misma fecha y `repetido: true`, sin duplicar recuperación.
- Una pareja descartada, otra recuperación o antecedentes incoherentes dan 409.
- Dos confirmaciones simultáneas no duplican; dos parejas rivales no recuperan
  dos veces el mismo perdido.
- Un fallo deliberado de persistencia devuelve el error genérico y revierte
  todos los cambios: no se oculta un fallo como éxito.
- El perdido recuperado sale de sugerencias; los GET y la respuesta pública no
  filtran contactos ni escriben avisos.

La columna nullable `fecha_recuperacion` no rellena fechas históricas.
La migración `migracion_h22_fecha_recuperacion.sql` se verificó y reejecutó en la
base compartida el 2026-10-26 con respaldo previo; la columna ya existía y los
datos quedaron intactos. Para otra base, revisar y autorizar antes de aplicarla.
No reimportar script.sql ni seed.sql.

```powershell
# Desde backend/springboot; usa H2 temporal
mvn test "-Dtest=ConfirmarRecuperacionIntegrationTest"
```

`ConfirmarRecuperacionMySqlQa` exige servidor nuevo y token de guarda; ver
`scripts/pruebas-api-leonel.py`. No usa la conexión normal de la aplicación.
Los resultados de esta revisión se registran en la entrega para Amore, sin
presentar pruebas antiguas o ejecuciones no realizadas como evidencia actual.
