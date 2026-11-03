# Integración del Sprint 2 en main

> Actualización posterior, 03/11/2026: la revisión completa y las correcciones
> realizadas en `dev/amore` están en [cierre integrado de Amore](cierre-sprint2-amore.md).
> Este documento conserva el estado histórico de su entrega; los pendientes H35
> y las cifras actuales se consultan en esa revisión.


Fecha: 3 de noviembre de 2026. Rama: `dev/yajaira`. Destino: `main` mediante
Pull Request. La prepara Yajaira con autorización del equipo; la aprueba Leonel.

## Qué contiene

`dev/yajaira` (`1029493`) ya incluía, en orden:

1. `main` con el cierre del Sprint 1 (`18b6860`).
2. La entrega de Leonel y su corrección de integración (`dev/leonel`, `b4d810c`).
3. La entrega de Amore (`dev/amore`, `1321289`).
4. La entrega de Yajaira y la web de `frontend/web` (`ce47b89`).

Además se le unió el rediseño Android de `mobile/android` (`f390325`, 6 commits del 27/10),
que estaba pendiente. Solo toca `movil/` y no tuvo conflictos.

67 commits sobre `main`: 40 de Amore, 14 de Yajaira y 13 de Leonel. Se conservan los
commits originales: no hay cherry-picks ni reescritura de historia.

## Historias incluidas

Las 14 historias del Sprint 2: H21–H24 y H26–H29 (Leonel), H33–H34 (Amore) y
H30, H31, H35 y H39 (Yajaira), con la capa móvil de Amore y la web de Yajaira.
Detalle: [Leonel](entrega-leonel-sprint2.md), [corrección](correccion-integracion-leonel-sprint2.md),
[Amore](entrega-amore-sprint2.md) y [Yajaira](entrega-yajaira-sprint2.md).

## Validación

| Capa | Resultado | Sobre |
| --- | --- | --- |
| Spring (`mvn verify`) | 411 pruebas correctas, BUILD SUCCESS | `dev/yajaira`, 30/10, PC de Yajaira |
| Web usuario (Vitest + build) | 206 pruebas correctas, build correcto | `dev/yajaira` `4d50a5c`, 30/10 |
| Web admin (Vitest + build) | 17 pruebas correctas, build correcto | `dev/yajaira` `4d50a5c`, 30/10 |
| Django | 42 pruebas correctas | `dev/yajaira` `4d50a5c`, 30/10 |
| Android | 35 pruebas unitarias y 6 instrumentadas correctas, según Amore | `mobile/android` `f390325`, 27/10 |

Entre `4d50a5c` y el estado actual solo cambian documentos (`docs/`, `README.md`) y `movil/`,
así que los resultados de Spring, web y Django siguen valiendo para este código. Android
no se volvió a ejecutar en esta integración.

Antes de aprobar, se recomienda ejecutar en `dev/yajaira`:

```powershell
# backend/springboot
mvn verify
# movil/android
gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Pendiente después del merge (Leonel)

1. Respaldar la base compartida y aplicar solo las migraciones H33, H34 y H35.
   No reimportar `script.sql`/`seed.sql` ni usar `docker compose down -v`.
2. Etiquetar `main` como `sprint-2`.
3. Las ramas del Sprint 3 parten de este `main`.
