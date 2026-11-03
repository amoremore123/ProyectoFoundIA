# H30 y H31 — casos de prueba (Yajaira)

Contrato: [métricas del tablero](../api/metricas-sprint2.md). Pantalla: panel
administrativo, `frontend/admin/src/pages/Dashboard.jsx`.

## H30 — Tiempo promedio de recuperación

| N.º | Caso | Resultado esperado | Prueba automática |
| --- | --- | --- | --- |
| 30-1 | Recuperaciones con fechas válidas en el rango | Promedio, mediana, mínimo y máximo en horas y días; `muestra` = casos usados | Django `test_h30_promedio_mediana_y_extremos_en_horas_y_dias` |
| 30-2 | Recuperado antiguo sin `fecha_recuperacion` | No se inventa duración; se cuenta en `sinFecha` | Django `test_h30_no_inventa_fechas_ni_cuenta_datos_incoherentes_u_ocultos` |
| 30-3 | Recuperación anterior a la publicación | Se excluye y se cuenta en `fechasIncoherentes` | (mismo caso) |
| 30-4 | Publicación oculta o eliminada | No entra al cálculo | (mismo caso) |
| 30-5 | Sin recuperaciones | Tiempos `null`, no 0; la web muestra «sin datos suficientes» | Django `test_h30_sin_recuperaciones_devuelve_nulos_y_no_cero` · Vitest «sin recuperaciones ni publicaciones muestra estados vacíos» |
| 30-6 | Filtro `desde`/`hasta` | Solo publicaciones del rango (UTC, inclusivo) | Django `test_h30_respeta_el_rango_de_publicacion` · Vitest «aplica y limpia el filtro de fechas» |
| 30-7 | Unidad mostrada | Horas hasta 48 h; días desde ahí | Vitest «usa horas hasta 48 h y días desde ahí» |

## H31 — Categorías y ubicaciones con más reportes

«Reportes» = publicaciones de objetos (perdidos + encontrados), no denuncias de
moderación.

| N.º | Caso | Resultado esperado | Prueba automática |
| --- | --- | --- | --- |
| 31-1 | Ranking de categorías | Ordenado por total, con perdidos/encontrados; desempate por nombre | Django `test_h31_categorias_ordenadas_con_desglose_y_desempate_por_nombre` |
| 31-2 | Ubicaciones escritas distinto («Biblioteca», « biblioteca ») | Se agrupan y se muestra la forma más usada | Django `test_h31_ubicaciones_agrupan_variantes_de_escritura` |
| 31-3 | Más de cinco grupos y filtro de fechas | Máximo 5 por lista y respeta el rango | Django `test_h31_muestra_como_maximo_cinco_y_respeta_el_rango` |
| 31-4 | Publicaciones sin ubicación | Se informan en `sinUbicacion`, no se pierden | (caso 31-2) |
| 31-5 | Sin datos | Listas vacías, sin cifras ficticias | Django `test_sin_datos_devuelve_listas_vacias` |
| 31-6 | Privacidad | La respuesta no tiene datos de personas | Django `test_no_expone_datos_de_personas` |

## Comunes al tablero

| N.º | Caso | Resultado esperado | Prueba automática |
| --- | --- | --- | --- |
| T-1 | Sin JWT / usuario no ADMIN | 401 / 403 | Django `test_solo_administrador` |
| T-2 | Rango invertido | La web no consulta y avisa | Vitest «no consulta si el rango está invertido» |
| T-3 | Error del servidor | Se muestra el error y «Reintentar» recupera; no se confunde con vacío | Vitest «muestra el error del servidor y permite reintentar» |
| T-4 | Conteos, tiempos y rankings juntos | Se muestran en el tablero | Vitest «muestra los conteos del periodo, el tiempo de recuperación y los rankings» |

## Cómo ejecutar

```powershell
# backend/django-admin
python manage.py test nucleo.test_metricas_h30_h31 --settings=config.settings_test --noinput
# frontend/admin
npx vitest run src/pages/Dashboard.test.jsx
```

## Resultado (30/10/2026, rama dev/yajaira 4d50a5c)

- Django: suite `nucleo` completa, **42 pruebas correctas** (incluye las 10 de H30/H31).
- Web admin: **17 pruebas correctas** (6 del Dashboard) y `vite build` correcto.

## Evidencia manual pendiente

Falta una captura del tablero con la API real y datos de QA conocidos:
promedio con casos válidos, estado «sin datos» y rankings. Guardarla en
`docs/pruebas/evidencias-yajaira-sprint2/`. Nunca usar la base compartida para
crear datos de prueba.
