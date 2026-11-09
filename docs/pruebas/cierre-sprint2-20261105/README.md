# Evidencias nuevas del cierre técnico — 05/11/2026

Código funcional: `f1a5087ddcb79b4ac6d5580694f7da08de99ecf5`, sobre
`main@521965b`. Fecha local: America/Lima; algunos registros UTC ya indican
06/11. El commit de cierre posterior solo cambia documentos/evidencias.

- `resumen.json`: resultados por capa, artefactos, entorno y límites.
- `h35-revocacion-real.json`: JWT legítimos Spring/Django, reset dentro del
  mismo segundo, anterior 401 y login nuevo sin espera; 43 peticiones reales.
- `jwt-contrato-real.json`: 21 comprobaciones cruzadas, incluidos sellos nulos,
  inválidos, sin `iat`, firma, caducidad, roles, suspensión y compatibilidad.
- `api-14-historias.json`: 14 historias y 58 peticiones reales, sin la antigua
  espera artificial del verificador H35.
- `web-real-resultados.json`, `h39-web-real.json` y `capturas/`: navegador Edge
  real, sin rutas simuladas. Las capturas contienen únicamente QA sintética.
- `migraciones-qa.json`, `respaldo-qa-resultados.json`, `fallo-smtp-qa.json`:
  datos previos conservados, restauración sintética y efectos del fallo SMTP.
- `matriz-84-tareas.csv`: descripciones originales, responsables y nivel de
  evidencia; H33.1 sigue siendo una condición externa para uso institucional.
- `huellas.json`: SHA-256 de esta carpeta, salvo el propio índice. Para textos
  se normaliza CRLF a LF (contenido versionado); las imágenes se toman sin cambios.

Los logs completos y el SQL sintético quedan fuera de Git, junto con los
secretos y tokens. No se incluyen credenciales de ejecución ni datos reales.
Las pruebas en emulador y el respaldo compartido del 03/11 son antecedentes
del equipo, **no comprobaciones nuevas de esta revisión**.

## Reproducir la regresión H35

1. Crear únicamente la QA aislada de `scripts/qa-cierre-sprint2.compose.yml`.
2. Arrancar Spring en 18083 y Django en 18004 contra MySQL QA 13372, con la misma
   clave JWT, Mailpit SMTP 11372 / HTTP 18372 y Java en America/Lima.
3. Instalar PyMySQL para los verificadores de QA y ejecutar:

```powershell
python scripts/verificar-revocacion-h35.py --salida evidencia-h35.json
```

El verificador crea cuentas sintéticas y exige una coincidencia real de segundo
por cada emisor. Si la máquina no consigue esa ventana, informa resultado no
concluyente: **no basta con que las peticiones terminen correctamente**.
La regresión determinista sin dependencia temporal está en `JwtServiceTest` y
`nucleo.test_recuperacion_sesion`.
