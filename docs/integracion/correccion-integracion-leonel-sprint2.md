# Corrección de integración de Leonel — H26, H28 y H29

Fecha: 27 de octubre de 2026. Rama: `dev/leonel`.

## Base y alcance

La rama parte de `dev/amore` en `1321289`: contiene el cierre de Sprint 1 de
`main`, la entrega original de Leonel y la entrega de Amore. La rama anterior de
Leonel (`9d210a3`) quedó respaldada fuera del repositorio y conservada en el
historial. Como ya era antecesora de Amore, se pudo actualizar sin descartar
commits, borrar la rama remota ni forzar el push.

Esta corrección solo modifica configuración, rutas, pruebas y documentación
de H26/H28/H29, además de adaptar la guarda de alcance a la base integrada.
No cambia Spring, Android, frontend, Docker, SQL, migraciones ni datos.

## Problema comprobado y corrección

Con la configuración predeterminada, la base integrada fallaba **9 de 31 pruebas
Django**. El interruptor de extras heredado de Sprint 1 ocultaba las rutas de
categorías y reportes y retiraba dos campos del tablero.

Ahora, sin cambiar `HABILITAR_EXTRAS=false`:

- **H26:** categorías disponibles; solo ADMIN activo puede gestionarlas.
- **H28:** consulta y resolución de reportes disponibles; se conservan actor,
  fecha, repetición segura y prohibición de borrado o edición general.
- **H29:** se conservan `usuarios`, `objetosActivos`, `reportesPendientes` y
  `categorias`, además de `rango` y los conteos filtrados de `objetos`.
- `/admin/` nativo de Django sigue deshabilitado. Su activación opcional no
  altera las API anteriores. No se activa el interruptor general de las webs.

## Validación nueva

- **32 pruebas Django correctas**, cero fallos, errores u omisiones, con la
  configuración predeterminada y SQLite en memoria.
- Las mismas **32 pruebas correctas con `HABILITAR_EXTRAS=true`**, como segunda
  configuración de prueba; no son 64 casos distintos.
- **8 peticiones HTTP internas sin sesión**: las rutas existentes devuelven 401,
  no 404. `/admin/` permanece opcional. No se utiliza ninguna BD real.
- Guarda de alcance: acepta la corrección y rechaza cinco cambios simulados
  ajenos (Android, frontend, autenticación y migraciones de otras historias),
  sin escribir esos archivos.
- El contenido heredado de Spring, frontend, Android y `database/` permanece
  idéntico a `1321289`. No se cuentan como nuevas ejecuciones las 403 pruebas
  Spring, 8 Android y 157 web de la auditoría previa sobre ese mismo contenido.

Evidencia: [resultados de la corrección](../pruebas/correccion-integracion-leonel-resultados.json).
Logs completos y respaldo Git: directorio local externo
`foundia-corregir-integracion-leonel-20261027-y02t2qr0`.

## Reproducción segura

```powershell
# Desde la raíz, en dev/leonel: solo cambios posteriores a la base integrada
python scripts/verificar-alcance-leonel.py
# Desde backend/django-admin, con requirements.txt instalado
python manage.py test nucleo --settings=config.settings_test --noinput
# Segunda configuración QA opcional; retirar después la variable
$env:HABILITAR_EXTRAS = 'true'
python manage.py test nucleo --settings=config.settings_test --noinput
Remove-Item Env:HABILITAR_EXTRAS
```

La guarda compara contra `1321289`. El trabajo ya recibido de Amore no se marca
como cambio propio; cualquier modificación nueva a sus archivos protegidos o a
migraciones de otras historias sigue siendo rechazada.

El ejecutor MySQL de la entrega original está vinculado a aquel esquema de cinco
migraciones: reproducir esa evidencia solo en una copia aislada de `9d210a3`.
No se ejecutó contra la base integrada ni se presenta como prueba nueva de esta
corrección. Aquí solo se usan bases temporales en memoria.

## Continuación del equipo

Para incorporar esta corrección, Yajaira debe tomar el nuevo cierre de
`origin/dev/leonel`, que ya contiene la entrega de Amore. No debe sustituirlo por
una copia anterior de `dev/amore` que aún no incluya este arreglo.

Siguen pendientes de Yajaira sus historias y la integración web, incluido enviar
la aceptación y versión legal de H33. H35 requiere su API de recuperación.
Esta corrección no implementa esas tareas ni cierra las 14 historias del sprint.

No se arrancó, migró ni reimportó la BD del equipo. Antes de desplegar el backend
heredado de Amore sobre otro entorno, revisar su guía y aplicar únicamente las
migraciones H33/H34 que falten, con autorización y respaldo previo. Nunca
reimportar `script.sql`/`seed.sql` ni usar `docker compose down -v`.
