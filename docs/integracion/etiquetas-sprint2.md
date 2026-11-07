# Etiquetas oficiales del Sprint 2

Leonel autorizó expresamente ordenar las etiquetas publicadas y sus enlaces,
sin cambiar la aplicación, la base de datos ni los commits existentes.

| Etiqueta | Uso actual |
| --- | --- |
| **`sprint-2`** | Entrega oficial completa: las 14 historias y 84 tareas verificadas, con el ajuste documental de nombres |
| **`sprint-2-preliminar`** | Corte anterior `9a376d84470accf050a299abfeed0164ca0d8ecd`, conservado como antecedente; no es la entrega final |

El nombre `sprint-2-final` se retiró porque era redundante. Apuntaba a
`86aa0a84cd5852747525588c91e16ff109314c82`, que sigue íntegro en el historial.
Los enlaces de entrega actuales usan `sprint-2`; las referencias históricas a
commits concretos siguen siendo válidas.

## Qué cambió y qué no

- La reasignación de la etiqueta publicada `sprint-2` y la retirada del alias
  fueron autorizadas por Leonel después de revisar la duplicidad.
- Antes de cambiar referencias se verificaron y respaldaron los objetos y las
  etiquetas originales en un bundle Git fuera del repositorio.
- El corte preliminar conserva su commit original. No se rebasa, resetea ni
  elimina ningún commit; las ramas avanzan únicamente con documentación.
- Los árboles de `backend/`, `frontend/`, `movil/` y `scripts/`, el SQL, las
  capturas y los resultados medidos permanecen iguales a los del cierre `86aa0a8`.
- Los JSON de evidencias conservan sus referencias previstas originales. No se
  presentan esos registros históricos como una nueva ejecución de pruebas.
- No se ejecutan migraciones, instalaciones ni pruebas sobre la BD para este
  ajuste de nombres. Los datos locales de cada integrante no se tocan.

## Actualizar una copia que ya tenía las etiquetas anteriores

Una etiqueta descargada no se sustituye automáticamente al hacer `pull`.
Después de confirmar que se trata de las etiquetas publicadas del equipo y no
de una variante local propia, ejecutar:

```powershell
git fetch origin "+refs/tags/sprint-2:refs/tags/sprint-2" "refs/tags/sprint-2-preliminar:refs/tags/sprint-2-preliminar"
git tag -d sprint-2-final
```

La actualización forzada se limita a esa etiqueta autorizada, no a ramas ni a
otros tags. La segunda orden solo retira el alias local obsoleto; no borra el
código ni commits. Si ese alias no se había descargado, no hay nada que retirar.

La etiqueta oficial y las ramas se verifican contra GitHub después de la
publicación atómica. El acta y los resultados se consultan desde
[la entrega `sprint-2`](https://github.com/amoremore123/ProyectoFoundIA/tree/sprint-2).
