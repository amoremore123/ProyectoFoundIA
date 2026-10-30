# Entrega de Yajaira a Leonel — Sprint 2

Fecha de implementación: 27 de octubre de 2026. Documento: 30 de octubre de 2026.
Rama: `dev/yajaira` (`4d50a5c`). Se creó a partir de la corrección de Leonel
`b4d810c`, que ya contenía `main`, su entrega y la de Amore, e integra `frontend/web`.
Reparto de referencia: PDF «FoundIA-Sprint-2-Reparto-funcional-FINALXDDDDDDD»
del 26/10/2026.

## Qué se entrega

Mis cuatro historias (H30, H31, H35 y H39) y el apoyo web de las historias de
Leonel y Amore, según el PDF. No se cambiaron Android, horas, PDF, Word ni ramas
ajenas, y no se hizo merge a `main`.

| Historia | Trabajo de Yajaira | Dónde |
| --- | --- | --- |
| H30 | Tiempo de recuperación en el tablero: promedio, mediana, mínimo, máximo, muestra y casos excluidos; horas o días; estado sin datos | Django `nucleo/metricas.py` · admin `Dashboard.jsx` |
| H31 | Rankings de categorías y ubicaciones (top 5, desempate por nombre, variantes de escritura agrupadas, `sinUbicacion`) | Django `nucleo/metricas.py` · admin `Dashboard.jsx` |
| H35 | API completa (token de un uso, 30 min, hash, sin enumerar cuentas, invalidación de sesiones) y pantallas web de solicitud y cambio | Spring `RecuperacionPasswordService` · web `RecuperarPassword.jsx`, `RestablecerPassword.jsx` |
| H39 | Compartir o copiar la URL pública desde el detalle, revalidando la publicación | web `CompartirPublicacion.jsx`, `Detalle.jsx` |

### Apoyo web (30 h del PDF)

| Historia | Pantalla web conectada a la API real |
| --- | --- |
| H21–H22 | `DecisionCoincidencia.jsx`: descartar y confirmar recuperación con confirmación; éxito solo tras la respuesta; 409 como error |
| H23–H24 | `Notificaciones.jsx` (avisos de coincidencia, abrir la pareja) y `PreferenciasNotificaciones.jsx` (tres tipos, cancelar, reintentar) |
| H26 | Admin `Categorias.jsx`: crear, editar conservando el ID, desactivar con confirmación y reactivar |
| H27 | `ReportarPublicacion.jsx` en el detalle: motivo, explicación para «Otro», cancelar y repetición |
| H28 | Admin `Reportes.jsx`: pendientes/todos, decisión con explicación y 409 visible |
| H29 | Admin `Dashboard.jsx`: conteos del periodo con filtro de fechas, vacío y error diferenciados |
| H33 | `Registro.jsx`: documentos con versión, casilla desmarcada y envío de `aceptaTerminos` y `versionLegal` |
| H34 | `SolicitudEliminacion.jsx` en el perfil: confirmar, cancelar, estado real y fecha UTC |

## Contratos y casos de prueba

- API de H30/H31: [métricas del tablero](../api/metricas-sprint2.md).
- API de H35: [recuperación de contraseña](../api/recuperacion-password-sprint2.md).
- Casos: [H30/H31](../pruebas/casos-prueba-h30-h31.md),
  [H35](../pruebas/casos-prueba-h35.md), [H39](../pruebas/casos-prueba-h39.md).

## Base de datos

Única migración nueva: `database/migracion_h35_recuperacion_password.sql`
(idempotente). `script.sql` también la incluye para instalaciones nuevas.
H30/H31 usan la columna `fecha_recuperacion` de H22, sin cambios de esquema.
No se aplicó nada sobre la base compartida: Leonel la aplica con respaldo previo
junto con H33/H34.

## Validación

Ejecutada el 30/10/2026 sobre `dev/yajaira` (`4d50a5c`; Spring en el PC de Yajaira):

| Capa | Resultado |
| --- | --- |
| Web usuario (Vitest) | **206 pruebas correctas** en 29 archivos; `vite build` correcto |
| Web admin (Vitest) | **17 pruebas correctas** en 3 archivos; `vite build` correcto |
| Django (`nucleo`, SQLite en memoria) | **42 pruebas correctas** (10 de H30/H31) |
| Spring (`mvn verify`, H2 temporal) | **411 pruebas correctas**, 0 fallos, 0 errores, 0 omitidas; JAR generado; `BUILD SUCCESS` (30/10/2026 16:58, Java 21, Maven 3.9.16, Windows 11). Incluye `RecuperarPasswordIntegrationTest` (8 casos) |

Además, la rama se puede integrar con los 6 commits del rediseño de
`mobile/android` sin conflictos: solo tocan `movil/`.

```powershell
# backend/springboot
mvn verify
# backend/django-admin
python manage.py test nucleo --settings=config.settings_test --noinput
# frontend/usuario y frontend/admin
npm test
npm run build
```

## Historial

| Commit | Contenido |
| --- | --- |
| `064a675` | H33: aceptar términos y privacidad en el registro web |
| `08dc693` | H35: recuperar contraseña con token de un solo uso (API) |
| `789eff2` | H35: recuperar contraseña en la web |
| `bc86c6d` | H39: compartir publicación desde el detalle web |
| `d2be178` | H30/H31: métricas de recuperación y rankings en el dashboard admin |
| `94a1516` | H29/H30/H31: dashboard admin con filtro de fechas, tiempo de recuperación y rankings |
| `63b7c55` | Integrar frontend/web en dev/yajaira |
| `f4aa226` | H34: solicitud de eliminación de cuenta en el perfil web |
| `c0abf89` | H21-H24: decidir coincidencias, avisos de coincidencia y preferencias en la web |
| `ce47b89` | H26-H28: categorías, reportar publicación y resolución de reportes en la web |
| `4d50a5c` | Integrar frontend/web en dev/yajaira |

Tres commits agrupan varias historias (`94a1516`, `c0abf89`, `ce47b89`), en contra
de la regla del PDF. Ya están publicados, así que no se reescriben; en el Sprint 3
se hará un commit por historia.

## Pendientes declarados

1. **Evidencias manuales** con API real y Mailpit para H30/H31, H35 y H39 en
   `docs/pruebas/evidencias-yajaira-sprint2/` (detalle en cada caso de prueba).
2. **H35 en Android:** Amore debe probarlo con el APK ahora que la API existe.
3. **H35 en Django:** el panel ADMIN no invalida tokens anteriores al cambio de
   contraseña. Pasa al Sprint 3.
4. **Enlace del correo:** abre la web; el formato `foundia://restablecer` de
   Android no se genera todavía.

## Qué recibe Leonel

1. Tomar `origin/dev/yajaira` (contiene `main`, la entrega de Leonel con su
   corrección y la de Amore) y actualizar `dev/leonel` sin forzar ni descartar
   cambios.
2. Incorporar el rediseño de `mobile/android` que Amore pase a `dev/amore`.
3. Respaldar la base compartida y aplicar solo las migraciones H33, H34 y H35.
4. Revisar las 14 historias e integrar el Sprint 2 en `main`, con autorización
   del equipo.
