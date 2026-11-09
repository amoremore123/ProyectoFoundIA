# Cierre técnico del Sprint 2 — Leonel

> **Antecedente:** este primer corte `9a376d8` se conserva ahora como
> `sprint-2-preliminar`. La entrega vigente es `sprint-2`; ver el
> [acta final](cierre-sprint2-final-20261106.md). Las referencias de abajo a la
> etiqueta original describen su situación histórica, no el nombre actual.

> Actualización posterior: [H33.1 integra fuentes oficiales de Tecsup](h33-fuentes-oficiales-20261105.md)
> por autorización de Leonel. Las condiciones y resultados de abajo describen
> el primer cierre, conservado como antecedente; no sustituyen esa actualización.

Fecha: **5 de noviembre de 2026 (America/Lima)**. Alcance académico: **14 historias,
84 tareas y 183 horas estimadas**, conservando el horizonte aprobado de 10 días.
Las horas no se presentan como tiempo real trabajado.

Este cierre sustituye el dictamen técnico bloqueado sobre `521965b`: **C2-01
(revocación H35) y C2-02 (mapeo CHAR) quedaron corregidos y comprobados**. No
sustituye una aprobación institucional ni acredita un despliegue con datos reales.

## Integración y responsables

La corrección parte de `main@521965b`, que ya integraba las entregas de Leonel,
Yajaira y Amore, incluido el rediseño Android. No se reescribe su historial ni se
incorpora trabajo del Sprint 3 para completar cifras.

- H21–H24 y H26–H29: Leonel.
- H33–H34 y capa Android del sprint: Amore.
- H30, H31, H35 y H39, incluida la web: Yajaira.
- Corrección final, comprobación e integración de cierre: Leonel, por solicitud
  expresa de resolver el bloqueo y cerrar el Sprint 2.
- H25, H32, H36, H37, H38 y H40 siguen en Sprint 3.

Commits de reparación: `4e20f17` (sello JWT compartido), `6b0145b` (mapeos CHAR)
y `f1a5087` (regresión HTTP por cada emisor). El código funcional verificado es
`f1a5087`; el commit posterior de cierre solo añade documentación y evidencias.

## H35: garantía corregida

Se añadió `cred`, un HMAC-SHA-256 de la identidad y la credencial almacenada,
compartido por Spring y Django. Se compara en tiempo constante, sin exponer el
hash BCrypt en el JWT. No cambian rutas, cuerpos de petición, columnas ni clientes.
Ambos backends deben actualizarse juntos con el mismo `JWT_SECRET`.

Los JWT anteriores se rechazan incluso cuando `iat` y el reset pertenecen al
mismo segundo. El login nuevo funciona sin esperar. Los tokens sin sello solo
se conservan en cuentas sin recuperación registrada; un sello nulo o inválido
nunca entra en esa compatibilidad. Se mantienen firma, caducidad, estado y
permisos consultados en la base. Un cambio de hash por otra ruta también revoca
los tokens con sello anterior.

Los casos HTTP reales usaron **dos cuentas sintéticas**, una por emisor:

| JWT legítimo | Antes del reset, Spring/Django | Mismo segundo después | Login nuevo, ambos emisores contra ambas API |
| --- | --- | --- | --- |
| Emitido por Spring | 200 / 200 | 401 / 401 | 200 / 200 / 200 / 200 |
| Emitido por Django | 200 / 200 | 401 / 401 | 200 / 200 / 200 / 200 |

Los anteriores siguieron en 401 tras 2,1 segundos. No se alteraron relojes ni
fechas SQL. Evidencia: [revocación real](../pruebas/cierre-sprint2-20261105/h35-revocacion-real.json).
El [contrato](../api/recuperacion-password-sprint2.md) documenta los bytes del sello.

## Verificación y trazabilidad

| Capa / comprobación nueva | Resultado |
| --- | --- |
| Spring, H2 aislado | 418 casos correctos; JAR generado |
| Django, SQLite en memoria | 57 casos correctos |
| Web usuario / admin | 208 / 17 casos y ambos builds correctos |
| Android | 38 unitarias, APK construido; lint: 0 errores y 25 advertencias |
| HTTP general | 14 historias, 58 peticiones reales |
| H35 mismo segundo | 43 peticiones reales; ambos emisores y ambas API |
| Contrato JWT adicional | 21 comprobaciones cruzadas, 53 peticiones reales |
| Navegador | 10 recorridos correctos y H39 anónimo/retirado |
| Postman local | 7 aserciones correctas; sin reporte cloud |
| Migraciones / restauración QA | 8 archivos × 2 pasadas; 14 tablas sintéticas restauradas idénticas |

Los resultados ejecutados, no estimados, están en el
[resumen verificable](../pruebas/cierre-sprint2-20261105/resumen.json) y el
[índice de evidencias](../pruebas/cierre-sprint2-20261105/README.md).
La [matriz de 84 tareas](../pruebas/cierre-sprint2-20261105/matriz-84-tareas.csv)
conserva las descripciones y responsables del Word original; **no afirma 84
pruebas manuales independientes ni convierte la condición externa H33.1 en
una aprobación obtenida**.

Además de las suites por capa, se repitieron las 14 historias por HTTP real,
los recorridos web, H39 anónimo y retirado, el fallo SMTP real, las ocho
migraciones en dos pasadas y el respaldo/restauración de 14 tablas sintéticas.
Los reintentos del ejecutor se distinguen de fallos del producto: se corrigió
una fecha de prueba UTC que resultaba futura en Lima; la API la rechazó con 400
correctamente. La primera ventana HTTP agrupada no obtuvo coincidencia temporal;
la ejecución definitiva demuestra el mismo segundo por cada emisor.

## Base de datos y despliegue

Los campos `token_hash` y `documentos_legales_hash` se mapearon explícitamente
como JDBC `CHAR`, respetando los `CHAR(64)` del SQL existente. **La API arrancó
con `ddl-auto=validate` contra MySQL 8 QA**. No se añadió una migración ni se
modificó SQL para resolver la corrección.

La BD compartida no se leyó ni modificó. El respaldo real citado por Amore el
03/11 permanece en su PC y no estuvo disponible aquí: su
[evidencia previa](../pruebas/cierre-sprint2-20261103/base-compartida.json)
no se presenta como una comprobación nueva. La restauración del 05/11 cubre solo
datos sintéticos QA. Un futuro despliegue compartido debe verificar el destino
y un respaldo actual, y aplicar únicamente migraciones que realmente falten;
**nunca reimportar `script.sql`/`seed.sql` ni usar `down -v`**.

Se detuvieron únicamente los servicios de esta QA y se conservaron sus volúmenes.
La configuración local `.opencode/` del usuario queda sin modificar ni publicar.

## Límites y condiciones conservadas

1. **H33.1, aprobación institucional:** los términos y la política son textos
   académicos versionados del proyecto, pendientes de revisión institucional.
   Se cierra la funcionalidad del prototipo, no esa aprobación externa ni su
   habilitación para producción con datos personales reales.
2. **Android:** la corrección no cambia código móvil ni el contrato que consume.
   Las seis instrumentadas y el recorrido APK del 03/11 son evidencia previa
   del equipo. No se atribuyen al 05/11: no hay una imagen completa de emulador
   y no se usó el AVD personal ni un teléfono físico. La regresión unitaria,
   construcción y lint nuevos se detallan en el resumen.
3. **Alcance de la aceptación:** cierre técnico académico solicitado por Leonel;
   no se inventan firmas del equipo, aprobación institucional, auditoría integral
   de seguridad ni certificación de accesibilidad.
4. **Publicación no es despliegue:** el cierre se identifica con `sprint-2` sobre
   el commit documental final, después de verificar todos los resultados. La
   existencia remota de la etiqueta debe comprobarse al publicarla; este acta
   no declara una sustitución de servicios ni una migración de datos compartidos.

Los documentos del 27/10 y 03/11 se conservan como antecedentes. Esta acta y la
matriz de cierre actualizan su estado, **sin alterar el alcance, el reparto ni
el PDF/Word original de planificación**.
