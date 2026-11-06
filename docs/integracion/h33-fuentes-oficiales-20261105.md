# H33.1 — Fuentes oficiales de Tecsup integradas

Fecha de consulta: **5 de noviembre de 2026 (America/Lima)**. Leonel autorizó usar
las fuentes oficiales de Tecsup y pidió terminar este punto antes de solicitar
el respaldo compartido. No se modifica el alcance ni el reparto del Sprint 2.

## Documentos suministrados

1. [Términos y Condiciones de Tecsup](https://www.tecsup.edu.pe/terminos-y-condiciones/).
   Se conserva el HTML original y se transcribe su contenido principal, sin
   navegación, scripts ni formularios del sitio.
2. [Política para el Tratamiento de Datos Personales](https://www.tecsup.edu.pe/wp-content/uploads/2024/08/politica-tratamiento-personales-tecsup.pdf),
   también publicada desde el enlace legal del sitio institucional. Se conserva
   el PDF original y la transcripción completa de sus **siete páginas**.

Versión del conjunto presentado por FoundIA: **`foundia-tecsup-2026-11-05-v2`**.
No se inventa una versión o fecha de aprobación de Tecsup: se registra la fecha
de consulta de los documentos que su sitio publica. Las fuentes se conservan en
`backend/springboot/src/main/resources/legal/foundia-tecsup-2026-11-05-v2/`.

## Integración y consentimiento

- API pública: textos completos, fuentes, fecha de consulta y huellas SHA-256 del
  original y su transcripción. El arranque comprueba la integridad de las copias.
- Los textos y enlaces se presentan antes del registro, en web y Android; también
  desde Privacidad en Android. Se incluyen localmente, sin depender de Tecsup
  para que el servidor arranque o se cree una cuenta.
- La casilla permanece desmarcada; leer los textos o fuentes no la marca.
  Una recarga exige aceptar otra vez la versión recibida.
- La aceptación se limita a las condiciones de **FoundIA**. Los permisos de
  publicidad y difusión de imagen, voz o notas que contiene el documento de
  Tecsup se identifican como parte de la referencia y **no se añaden al alta**.
- No se atribuye a Tecsup la operación de la base, el alojamiento, la atención de
  solicitudes del proyecto ni una aprobación institucional de FoundIA.
- Se conserva la versión anterior, sus archivos y los consentimientos guardados.
  Un registro nuevo con esa versión recibe 400; no se reescriben cuentas antiguas.
- No se añade una migración y no se modifica la BD real.

Esto resuelve **la disponibilidad e integración de documentos institucionales de
H33.1**. No convierte una autorización de uso de fuentes del proyecto en una
aprobación institucional de su despliegue: son hechos distintos, no otra tarea
de software pendiente ni una aprobación que el equipo pueda inventar.

## Evidencias

Código funcional: `dba319e`. Resultados nuevos sobre esa integración:

| Comprobación | Resultado |
| --- | --- |
| Spring | 422 casos correctos; JAR generado |
| Django | 57 casos correctos |
| Web usuario / administración | 210 / 17 casos; ambos builds correctos |
| Android 1.2.2 (código 5) | 41 unitarias, APK construido; lint: 0 errores y 25 advertencias |
| API + MySQL QA reales | 9 peticiones; versión, fecha y huella correctas; consentimiento anterior conservado |
| Navegador Edge real | Registro verificado y dos fuentes oficiales 200, sin enviar JWT ni referente a Tecsup |
| Postman local | 8 aserciones correctas, sin publicación cloud |

Los resultados ejecutados se registran en
[`docs/pruebas/h33-fuentes-tecsup-20261105/`](../pruebas/h33-fuentes-tecsup-20261105/).
Las evidencias del cierre anterior permanecen inmutables como antecedentes.
La etiqueta publicada `sprint-2` tampoco se reescribe.

**Aclaración posterior de Leonel:** cada integrante utiliza sus propios datos
locales de prueba. No se necesita el respaldo de Amore ni se mantiene su ausencia
como bloqueo. El [acuerdo de datos locales](datos-locales-pruebas-sprint2.md)
sustituye aquella interpretación de una base central compartida. No se presenta
una restauración local como verificación del archivo de otra PC.
